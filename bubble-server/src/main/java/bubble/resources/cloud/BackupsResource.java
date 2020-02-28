/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.dao.cloud.BubbleBackupDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BackupStatus;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.BubbleNetwork;
import bubble.service.backup.BackupCleanerService;
import bubble.service.backup.BackupService;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.EP_CLEAN_BACKUPS;
import static bubble.cloud.storage.StorageServiceDriver.STORAGE_PREFIX;
import static bubble.cloud.storage.StorageServiceDriver.STORAGE_PREFIX_TRUNCATED;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class BackupsResource {

    private Account account;
    private BubbleNetwork network;

    public BackupsResource(Account account, BubbleNetwork network) {
        this.account = account;
        this.network = network;
    }

    @Autowired private BubbleBackupDAO backupDAO;
    @Autowired private BackupService backupService;
    @Autowired private BackupCleanerService backupCleanerService;

    @GET
    public Response listBackups(@Context ContainerRequest ctx) {
        final Account account = getAccount(ctx);
        return ok(backupDAO.findByNetwork(network.getUuid()));
    }

    @GET @Path("/{id}")
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
    public Response addLabeledBackup(@Context ContainerRequest ctx,
                                     @PathParam("label") String label) {
        final Account account = getAccount(ctx);
        return ok(backupService.queueBackup(label));
    }

    @POST @Path(EP_CLEAN_BACKUPS)
    public Response cleanBackups(@Context ContainerRequest ctx) {
        final Account account = getAccount(ctx);
        return ok(backupCleanerService.cleanNow());
    }

    @DELETE @Path("/{id : .+}")
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
