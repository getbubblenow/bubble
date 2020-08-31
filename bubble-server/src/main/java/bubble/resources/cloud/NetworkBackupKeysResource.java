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
import lombok.NonNull;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.stream.FileSendableResource;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;

import static bubble.ApiConstants.*;
import static bubble.model.account.Account.validatePassword;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_OCTET_STREAM;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

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

    @NonNull private String fetchAndCheckEncryptionKey(@Nullable final NameAndValue enc) {
        final String encryptionKey = enc == null ? null : enc.getValue();
        final ConstraintViolationBean error = validatePassword(encryptionKey);
        if (error != null) throw new SimpleViolationException(error);
        return encryptionKey;
    }

    @POST @Path("/{keysCode}")
    @NonNull public Response retrieveNetworkKeys(@NonNull @Context final Request req,
                                                 @NonNull @Context final ContainerRequest ctx,
                                                 @NonNull @PathParam("keysCode") final String keysCode,
                                                 @Nullable final NameAndValue enc) {
        final var encryptionKey = fetchAndCheckEncryptionKey(enc);
        final var networkKeys = keysService.retrieveKeys(keysCode);
        return ok(networkKeys.encrypt(encryptionKey));
    }

    @POST @Path("/{keysCode}" + EP_BACKUPS + EP_START)
    @NonNull public Response backupDownloadStart(@NonNull @Context final ContainerRequest ctx,
                                                 @NonNull @PathParam("keysCode") final String keysCode,
                                                 @NonNull @QueryParam("backupId") final String backupId,
                                                 @Nullable final NameAndValue enc) {
        final var passphrase = fetchAndCheckEncryptionKey(enc);
        keysService.retrieveKeys(keysCode);

        final var backup = backupDAO.findByNetworkAndId(thisNetwork.getUuid(), backupId);
        if (backup == null) throw notFoundEx(backupId);

        keysService.startBackupDownload(thisNetwork.getUuid(), backup, keysCode, passphrase);
        keysService.backupDownloadStatus(keysCode);
        return ok();
    }

    @GET @Path("/{keysCode}" + EP_BACKUPS + EP_STATUS)
    @NonNull public Response backupDownloadStatus(@NonNull @Context final ContainerRequest ctx,
                                                  @NonNull @PathParam("keysCode") final String keysCode) {
        // not checking keys code now here. However, such key will be required in preparing/prepared backup downloads'
        // mapping within restoreService.
        return ok(keysService.backupDownloadStatus(keysCode));
    }

    @GET @Path("/{keysCode}" + EP_BACKUPS + EP_DOWNLOAD)
    @Produces(APPLICATION_OCTET_STREAM)
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
