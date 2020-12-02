/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.dao.cloud.BubbleBackupDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BackupStatus;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.BubbleNetwork;
import bubble.service.backup.BackupCleanerService;
import bubble.service.backup.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.API_TAG_BACKUP_RESTORE;
import static bubble.ApiConstants.EP_CLEAN_BACKUPS;
import static bubble.cloud.storage.StorageServiceDriver.STORAGE_PREFIX;
import static bubble.cloud.storage.StorageServiceDriver.STORAGE_PREFIX_TRUNCATED;
import static bubble.service.backup.BackupCleanerService.MAX_BACKUPS;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class BackupsResource {

    private final Account account;
    private final BubbleNetwork network;

    public BackupsResource(Account account, BubbleNetwork network) {
        this.account = account;
        this.network = network;
    }

    @Autowired private BubbleBackupDAO backupDAO;
    @Autowired private BackupService backupService;
    @Autowired private BackupCleanerService backupCleanerService;

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="List backups",
            description="List backups for the current Bubble",
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON array of BubbleBackup objects")
    )
    public Response listBackups(@Context ContainerRequest ctx) {
        final Account account = getAccount(ctx);
        return ok(backupDAO.findByNetwork(network.getUuid()));
    }

    @GET @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Get details for a backup by ID",
            description="Get details for a backup by ID. If the `status` parameter is specified, then the backup is only returned if the status matches this",
            parameters={
                    @Parameter(name="id", description="UUID or path of a backup", required=true),
                    @Parameter(name="status", description="only return backup if it's status matches this BackupStatus")
            },
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the BubbleBackup object representing the backup"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="no backup found with the given ID and/or status")
            }
    )
    public Response viewBackup(@Context ContainerRequest ctx,
                               @PathParam("id") String id,
                               @QueryParam("status") BackupStatus status) {
        final Account account = getAccount(ctx);
        final BubbleBackup backup = backupDAO.findByNetworkAndId(network.getUuid(), id);
        if (backup == null) return notFound(id);
        if (status != null && backup.getStatus() != status) return notFound(id+":"+status);
        return ok(backup);
    }

    @PUT @Path("/{label}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Queue a new backup job",
            description="Queue a new backup job. It will run soon. If an existing backup is in progress, it will run after that backup has completed.",
            parameters={
                    @Parameter(name="id", description="UUID or path of a backup", required=true),
                    @Parameter(name="label", description="label for the backup", required=true)
            },
            responses=@ApiResponse(responseCode=SC_OK, description="the BubbleBackup object representing the backup that was enqueued")
    )
    public Response addLabeledBackup(@Context ContainerRequest ctx,
                                     @PathParam("label") String label) {
        final Account account = getAccount(ctx);
        return ok(backupService.queueBackup(label));
    }

    @POST @Path(EP_CLEAN_BACKUPS)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Remove old backups",
            description="Bubble automatically cleans up old backups every 24 hours, retaining the most recent "+MAX_BACKUPS+" backups. Use this endpoint to run the cleaner now.",
            responses=@ApiResponse(responseCode=SC_OK, description="the BubbleBackup object representing the backup that was enqueued")
    )
    public Response cleanBackups(@Context ContainerRequest ctx) {
        final Account account = getAccount(ctx);
        return ok(backupCleanerService.cleanNow());
    }

    @DELETE @Path("/{id : .+}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_BACKUP_RESTORE,
            summary="Delete a backup",
            description="Delete a backup",
            parameters=@Parameter(name="id", description="UUID or path of a backup", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="an empty response with status 200 indicates success"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="no backup found with the given ID and/or status")
            }
    )
    public Response deleteBackup(@Context ContainerRequest ctx,
                                 @PathParam("id") String id) {
        if (id.startsWith(STORAGE_PREFIX_TRUNCATED) && !id.startsWith(STORAGE_PREFIX)) {
            id = STORAGE_PREFIX + id.substring(STORAGE_PREFIX_TRUNCATED.length());
        }
        final Account account = getAccount(ctx);
        final BubbleBackup backup = backupDAO.findByNetworkAndId(network.getUuid(), id);
        if (backup == null) return notFound(id);
        backupDAO.delete(backup.getUuid());
        return ok();
    }

    private Account getAccount(ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        // caller must be admin or owner of the network that this backup belongs to
        if (!caller.admin() && !caller.getUuid().equals(network.getAccount())) {
            throw forbiddenEx();
        }
        return caller;
    }
}
