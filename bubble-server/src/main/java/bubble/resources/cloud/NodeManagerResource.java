package bubble.resources.cloud;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.service.boot.NodeManagerService;
import bubble.service.boot.SelfNodeService;
import bubble.service.notify.NotificationService;
import edu.emory.mathcs.backport.java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.*;
import org.cobbzilla.util.io.ByteLimitedInputStream;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.string.Base64;
import org.cobbzilla.util.system.Bytes;
import org.cobbzilla.wizard.auth.LoginRequest;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static bubble.model.cloud.notify.NotificationType.hello_to_sage;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class NodeManagerResource {

    // these constants must match up with those defined in bubble-nodemanager
    public static final String BUBBLE_NODE_ADMIN = "bubble_node_admin";
    public static final String ROOT_DIR_PREFIX = "root_dir/";
    public static final String COMPONENT_ROOT = "root";
    public static final Set<String> PATCH_COMPONENTS = new HashSet<>(Arrays.asList(new String[]{COMPONENT_ROOT, "bubble", "mitmproxy"}));

    // other constants
    public static final String AUTH_BASIC_PREFIX = "Basic ";
    public static final long MAX_PATCH_SIZE = 200 * Bytes.MB;

    private BubbleNode node;

    public NodeManagerResource (BubbleNode node) { this.node = node; }

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private NodeManagerService nodeManagerService;
    @Autowired private SelfNodeService selfNodeService;
    @Autowired private NotificationService notificationService;

    @POST @Path("/set_password")
    public Response setPassword (@Context ContainerRequest ctx,
                                 LoginRequest request) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();

        final String password = request.getPassword();
        if (empty(password)) return invalid("err.password.required");
        if (password.length() < 10) return invalid("err.password.tooShort");

        nodeManagerService.setPassword(password);
        final BubbleNode selfNode = selfNodeService.getThisNode();
        if (selfNode != null && selfNode.hasSageNode() && !selfNode.getUuid().equals(selfNode.getSageNode())) {
            final BubbleNode sageNode = nodeDAO.findByUuid(selfNode.getSageNode());
            if (sageNode == null) {
                log.warn("setPassword: error finding sage to notify: " + selfNode.getSageNode());
            } else {
                selfNode.setNodeManagerPassword(password);
                final NotificationReceipt receipt = notificationService.notify(sageNode, hello_to_sage, selfNode);
                if (!receipt.isSuccess()) {
                    log.warn("setPassword: error notifying sage of new nodemanager password: " + receipt);
                }
                selfNode.setNodeManagerPassword(null); // just in case the object gets sync'd to db
            }
        }
        return ok_empty();
    }

    @POST @Path("/disable")
    public Response disable (@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();

        nodeManagerService.disable();
        return ok_empty();
    }

    public HttpRequestBean validateNodeManagerRequest(ContainerRequest ctx, String path) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(node.getAccount())) throw forbiddenEx();

        final BubbleNode n = nodeDAO.findByUuid(node.getUuid());
        final String nodeManagerPassword;
        if (n.hasNodeManagerPassword()) {
            nodeManagerPassword = n.getNodeManagerPassword();
        } else {
            final String authHeader = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (empty(authHeader)) throw invalidEx("err.nodemanager.noPasswordSet");
            try {
                final String userNameAndPassword = new String(Base64.decode(authHeader.substring(authHeader.indexOf(AUTH_BASIC_PREFIX)+AUTH_BASIC_PREFIX.length())));
                nodeManagerPassword = userNameAndPassword.substring(userNameAndPassword.indexOf(':') + 1);
            } catch (Exception e) {
                throw invalidEx("err.nodemanager.noPasswordSet");
            }
        }

        final String url = "https://" + node.getFqdn() + ":" + node.getSslPort() + "/nodeman/" + path;
        log.info("validateNodeManagerRequest: requesting URL: "+url);
        return new HttpRequestBean(url)
                .setAuthType(HttpAuthType.basic)
                .setAuthUsername(BUBBLE_NODE_ADMIN)
                .setAuthPassword(nodeManagerPassword);
    }

    public Response callNodeManager(HttpRequestBean request, String prefix) {
        prefix = prefix + ": ";
        try {
            final HttpResponseBean response = HttpUtil.getResponse(request);
            if (response.isOk()) return ok(response.getEntityString());
            log.error(prefix+response);
        } catch (IOException e) {
            log.error(prefix+shortError(e));
        }
        throw invalidEx("err.nodemanager.error");
    }

    @GET @Path("/stats/{stat}")
    public Response getStats (@Context ContainerRequest ctx,
                              @PathParam("stat") String stat) {
        final HttpRequestBean request = validateNodeManagerRequest(ctx, "stats/"+stat);
        return callNodeManager(request, "getStats");
    }

    @POST @Path("/cmd/{command}")
    public Response runCommand (@Context ContainerRequest ctx,
                                @PathParam("command") String command) {
        final HttpRequestBean request = validateNodeManagerRequest(ctx, "cmd/"+command)
                .setMethod(HttpMethods.POST);
        return callNodeManager(request, "runCommand");
    }

    @POST @Path("/service/{service}/{action}")
    public Response service (@Context ContainerRequest ctx,
                             @PathParam("service") String service,
                             @PathParam("action") String action) {
        final HttpRequestBean request = validateNodeManagerRequest(ctx, "service/"+service+"/"+action)
                .setMethod(HttpMethods.POST);
        return callNodeManager(request, "service");
    }

    @POST @Path("/patch/file/{component}/{path : .+}")
    @Consumes(MULTIPART_FORM_DATA)
    public Response patchFile (@Context ContainerRequest ctx,
                               @PathParam("component") String component,
                               @PathParam("path") String path,
                               @FormDataParam("file") InputStream in,
                               @FormDataParam("name") String name) {
        log.info("patchFile: component="+component+", path="+path+", name="+name);
        if (!PATCH_COMPONENTS.contains(component)) return notFound(component);
        final HttpRequestBean request = validateNodeManagerRequest(ctx, "patch/"+component)
                .setMethod(HttpMethods.POST);

        // create a zipfile containing the file at the proper path
        final File zipFile = buildPatchZip(component, path, new ByteLimitedInputStream(in, MAX_PATCH_SIZE));

        // register patch with NodeManagerService, receive URL
        final String url = nodeManagerService.registerPatch(zipFile);
        request.setEntity(url);

        return callNodeManager(request, "patchFile");
    }

    private File buildPatchZip(String component, String path, InputStream in) {
        final TempDir tempDir = new TempDir();
        if (component.equals(COMPONENT_ROOT)) {
            path = ROOT_DIR_PREFIX + path;
        }
        if (path.startsWith("/")) path = path.substring(1);
        if (empty(path)) throw invalidEx("err.nodemanager.invalidPath");
        final File dest = new File(abs(tempDir)+"/"+path);
        mkdirOrDie(dirname(abs(dest)));
        FileUtil.toFileOrDie(dest, in);
        log.info("buildPatchZip: wrote temp file: "+abs(dest));
        final File zipFile = FileUtil.temp(".zip");
        if (!zipFile.delete()) return die("buildPatchZip: error deleting zipfile");
        log.info("buildPatchZip: zipping into zipFile: "+abs(zipFile));
        try {
            execScript("cd "+abs(tempDir)+" && zip -r "+abs(zipFile)+" *");

        } catch (Exception e) {
            return die("buildPatchZip: "+shortError(e), e);
        }
        log.info("buildPatchZip: created patch file: "+abs(zipFile));
        return zipFile;
    }

}
