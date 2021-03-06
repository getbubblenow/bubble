/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.cloud.storage.StorageServiceDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.RekeyRequest;
import bubble.model.cloud.StorageMetadata;
import bubble.server.BubbleConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.ApiConstants.*;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.*;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.io.FileUtil.basename;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Slf4j
public class StorageResource {

    private final Account account;
    @Getter private final CloudService cloud;

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    @Getter(lazy=true) private final AtomicReference<StorageServiceDriver> storageDriver
            = new AtomicReference<>(getCloud().getStorageDriver(configuration));

    public StorageResource(Account account, CloudService cloud) {
        this.account = account;
        this.cloud = cloud;
    }

    protected String thisNodeUuid() { return configuration.getThisNode().getUuid(); }

    @GET @Path(EP_READ_METADATA+"/{key : .+}")
    @Produces(APPLICATION_JSON)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Read metadata for key from storage",
            description="Read metadata for key from storage. Caller must own the underlying storage.",
            parameters=@Parameter(name="key", description="key to read metadata from", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a StorageMetadata object"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller does not own the storage"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="key not found")
            }
    )
    public Response meta(@Context ContainerRequest ctx,
                         @PathParam("key") String key) {
        final Account caller = getCaller(ctx);
        final StorageMetadata metadata = getStorageDriver().get().readMetadata(thisNodeUuid(), key);
        return metadata == null ? notFound(key) : ok(metadata);
    }

    @GET @Path(EP_READ+"/{key : .+}")
    @Produces(CONTENT_TYPE_ANY)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Read from storage",
            description="Read from storage. Caller must own the underlying storage.",
            parameters=@Parameter(name="key", description="key to read from", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="response will be a stream of data. Content-Type is set based on the key suffix"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller does not own the storage"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="key not found")
            }
    )
    public Response read(@Context ContainerRequest ctx,
                         @PathParam("key") String key) {
        final Account caller = getCaller(ctx);

        final StorageServiceDriver driver = getStorageDriver().get();
        StorageMetadata metadata = driver.readMetadata(thisNodeUuid(), key);
        if (metadata == null) metadata = new StorageMetadata();

        final InputStream in = driver.read(thisNodeUuid(), key);
        if (in == null) return notFound(key);

        final String contentType = metadata.hasContentType() ? metadata.getContentType() : APPLICATION_OCTET_STREAM;
        try {
            return stream(contentType, in);
        } catch (Exception e) {
            return invalid("err.read.failed", "read failed for prefix: "+key, key);
        }
    }

    @GET @Path(EP_LIST+"/{key : .+}")
    @Produces(APPLICATION_JSON)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="List keys in storage matching a prefix",
            description="List keys in storage matching a prefix. Caller must own the underlying storage. Returns a StorageListing object which contains the first page of results and an listingId that can be used to get more pages of results",
            parameters=@Parameter(name="key", description="list keys with this prefix", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a StorageListing object"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller does not own the storage"),
                    @ApiResponse(responseCode=SC_INVALID, description="an error occurred listing keys")
            }
    )
    public Response list(@Context ContainerRequest ctx,
                         @PathParam("key") String key) {
        final Account caller = getCaller(ctx);
        final StorageServiceDriver driver = getStorageDriver().get();
        try {
            return ok(driver.list(thisNodeUuid(), key));
        } catch (IOException e) {
            return invalid("err.list.failed", "listing failed for prefix: "+key, key);
        }
    }

    @GET @Path(EP_LIST_NEXT+"/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="List more keys in storage matching a prefix",
            description="List more keys in storage matching a prefix. Caller must own the underlying storage. Returns a StorageListing object which contains the first page of results and an listingId that can be used to get more pages of results",
            parameters=@Parameter(name="id", description="the `listingId` from a `StorageListing` object from an initial listing"),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a StorageListing object"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller does not own the storage"),
                    @ApiResponse(responseCode=SC_INVALID, description="an error occurred listing keys")
            }
    )
    public Response listNext(@Context ContainerRequest ctx,
                             @PathParam("id") String id) {
        final Account caller = getCaller(ctx);
        final StorageServiceDriver driver = getStorageDriver().get();
        try {
            return ok(driver.listNext(thisNodeUuid(), id));
        } catch (IOException e) {
            return invalid("err.listNext.failed", "listing failed for id: "+id, id);
        }
    }

    @POST @Path(EP_WRITE+"/{key : .+}")
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Write to storage",
            description="Write to storage. Caller must own the underlying storage. Returns a StorageMetadata object for the data written.",
            parameters={
                @Parameter(name="key", description="write to this key", required=true),
                @Parameter(name="sha256", description="SHA-256 sum of the data"),
                @Parameter(name="file", description="stream of bytes to write to storage", required=true)
            },
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a StorageMetadata object"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller does not own the storage"),
                    @ApiResponse(responseCode=SC_INVALID, description="an error occurred writing to storage")
            }
    )
    public Response write(@Context ContainerRequest ctx,
                          @PathParam("key") String key,
                          @QueryParam("sha256") String sha256,
                          @FormDataParam("file") InputStream in) {
        log.info("write: key="+key);
        final Account caller = getCaller(ctx);
        final StorageMetadata metadata = new StorageMetadata().setName(basename(key));
        if (!empty(sha256)) metadata.setSha256(sha256);
        if (getStorageDriver().get().write(thisNodeUuid(), key, in, metadata)) {
            return ok(metadata);
        } else {
            return invalid("err.write.failed", "write operation failed for key: "+key);
        }
    }

    @DELETE @Path(EP_DELETE+"/{key : .+}")
    @Produces(APPLICATION_JSON)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Delete from storage",
            description="Delete from storage. Caller must own the underlying storage. Returns a true upon successful deletion.",
            parameters=@Parameter(name="key", description="delete this key and its data", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="true"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller does not own the storage"),
                    @ApiResponse(responseCode=SC_INVALID, description="an error occurred deleting from storage")
            }
    )
    public Response delete(@Context ContainerRequest ctx,
                           @PathParam("key") String key) {
        final Account caller = getCaller(ctx);
        if (key.equals("*")) key = "";
        try {
            return ok(getStorageDriver().get().delete(thisNodeUuid(), key));
        } catch (Exception e) {
            return invalid("err.delete.failed", "delete operation failed for key: "+key+": "+shortError(e), key);
        }
    }

    @POST @Path(EP_REKEY)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Re-key storage",
            description="Re-key storage. Generates a new encryption key and re-encrypts all data stored with the new key. Caller must own the underlying storage. Returns a true upon successful re-key.",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="true"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller does not own the storage")
            }
    )
    public Response rekey(@Context ContainerRequest ctx,
                          RekeyRequest request) {
        final Account caller = getCaller(ctx);
        if (!caller.admin()) {
            if (!account.getHashedPassword().isCorrectPassword(request.getPassword())) forbidden();
        }

        // re-lookup cloud
        final StorageServiceDriver driver = getStorageDriver().get();
        final CloudService storage = cloudDAO.findByUuid(cloud.getUuid());
        if (storage == null) return notFound(cloud.getUuid());

        // ensure new cloud exists
        final CloudService newStorage = cloudDAO.findByAccountAndId(caller.getUuid(), request.getNewCloud());
        if (newStorage == null) return notFound(request.getNewCloud());

        try {
            return ok(driver.rekey(thisNodeUuid(), newStorage));
        } catch (IOException e) {
            return die("rekey: "+e, e);
        }
    }

    protected Account getCaller(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.getUuid().equals(account.getUuid())) throw forbiddenEx();
        return caller;
    }

}
