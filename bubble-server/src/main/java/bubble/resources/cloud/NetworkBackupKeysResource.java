/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.cloud.BubbleBackupDAO;
import bubble.model.account.Account;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.cloud.BubbleNetwork;
import bubble.service.backup.NetworkKeysService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.NonNull;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.stream.FileSendableResource;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;

import static bubble.ApiConstants.*;
import static bubble.model.account.Account.validatePassword;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_OCTET_STREAM;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

/**
 * Ensure that only network admin can access these calls, and only for current network. Such admin should have verified
 * account contact, and should be authenticated as in `authenticatorService.ensureAuthenticated`.
 */
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class NetworkBackupKeysResource {
    @Autowired private AccountMessageDAO messageDAO;
    @Autowired private BubbleBackupDAO backupDAO;
    @Autowired private NetworkKeysService keysService;

    private final Account adminCaller;
    private final BubbleNetwork thisNetwork;

    public NetworkBackupKeysResource(@NonNull final Account adminCaller, @NonNull final BubbleNetwork thisNetwork) {
        this.adminCaller = adminCaller;
        this.thisNetwork = thisNetwork;
    }

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Request Bubble keys",
            description="Request Bubble keys. Sends a message to the owner of the Bubble to approve the request",
            responses=@ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success")
    )
    @NonNull public Response requestNetworkKeys(@NonNull @Context final Request req,
                                                @NonNull @Context final ContainerRequest ctx) {
        messageDAO.create(new AccountMessage().setMessageType(AccountMessageType.request)
                                              .setAction(AccountAction.password)
                                              .setTarget(ActionTarget.network)
                                              .setAccount(adminCaller.getUuid())
                                              .setNetwork(thisNetwork.getUuid())
                                              .setName(thisNetwork.getUuid())
                                              .setRemoteHost(getRemoteHost(req)));
        return ok();
    }

    @NonNull private String fetchAndCheckEncryptionKey(final NameAndValue enc) {
        final String encryptionKey = enc == null ? null : enc.getValue();
        final ConstraintViolationBean error = validatePassword(encryptionKey);
        if (error != null) throw new SimpleViolationException(error);
        return encryptionKey;
    }

    @POST @Path("/{keysCode}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Retrieve Bubble keys",
            description="Once request for Bubble keys is approved, retrieve them here. This returns a NetworkKeys objects which contains the encrypted keys.",
            responses=@ApiResponse(responseCode=SC_OK, description="a NetworkKeys object containing the encrypted keys")
    )
    @NonNull public Response retrieveNetworkKeys(@NonNull @Context final Request req,
                                                 @NonNull @Context final ContainerRequest ctx,
                                                 @NonNull @PathParam("keysCode") final String keysCode,
                                                 final NameAndValue enc) {
        final var encryptionKey = fetchAndCheckEncryptionKey(enc);
        final var networkKeys = keysService.retrieveKeys(keysCode);
        return ok(networkKeys.encrypt(encryptionKey));
    }

    @POST @Path("/{keysCode}" + EP_BACKUPS + EP_START)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Start downloading a backup",
            description="Start downloading a backup",
            parameters={
                    @Parameter(name="keysCode", description="a code to associate with this download", required=true),
                    @Parameter(name="backupId", description="the backup to download", required=true)
            },
            responses=@ApiResponse(responseCode=SC_OK, description="empty response indicates success")
    )
    @NonNull public Response backupDownloadStart(@NonNull @Context final ContainerRequest ctx,
                                                 @NonNull @PathParam("keysCode") final String keysCode,
                                                 @NonNull @QueryParam("backupId") final String backupId,
                                                 final NameAndValue enc) {
        final var passphrase = fetchAndCheckEncryptionKey(enc);
        keysService.retrieveKeys(keysCode);

        final var backup = backupDAO.findByNetworkAndId(thisNetwork.getUuid(), backupId);
        if (backup == null) throw notFoundEx(backupId);

        keysService.startBackupDownload(thisNetwork.getUuid(), backup, keysCode, passphrase);
        keysService.backupDownloadStatus(keysCode);
        return ok();
    }

    @GET @Path("/{keysCode}" + EP_BACKUPS + EP_STATUS)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Check backup download status",
            description="Check backup download status",
            parameters=@Parameter(name="keysCode", description="the code supplied when the backup download was started", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="a BackupPackagingStatus object")
    )
    @NonNull public Response backupDownloadStatus(@NonNull @Context final ContainerRequest ctx,
                                                  @NonNull @PathParam("keysCode") final String keysCode) {
        // not checking keys code now here. However, such key will be required in preparing/prepared backup downloads'
        // mapping within restoreService.
        return ok(keysService.backupDownloadStatus(keysCode));
    }

    @GET @Path("/{keysCode}" + EP_BACKUPS + EP_DOWNLOAD)
    @Produces(APPLICATION_OCTET_STREAM)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Download a backup",
            description="Once a backup has fully downloaded to the Bubble, use this API call to retrieve the download.",
            parameters=@Parameter(name="keysCode", description="the code supplied when the backup download was started", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="the backup file")
    )
    @NonNull public Response backupDownload(@NonNull @Context final ContainerRequest ctx,
                                            @NonNull @PathParam("keysCode") final String keysCode) {
        final var status = keysService.backupDownloadStatus(keysCode);
        if (!status.isDone()) return accepted();

        keysService.clearBackupDownloadKey(keysCode);
        final var outFileName = "backup-" + thisNetwork.getNickname() + ".tgz.enc";
        final var backupArchiveFile = new File(status.getPackagePath());
        return send(new FileSendableResource(backupArchiveFile).setContentType(APPLICATION_OCTET_STREAM)
                                                               .setContentLength(backupArchiveFile.length())
                                                               .setForceDownload(true)
                                                               .setName(outFileName));
    }
}
