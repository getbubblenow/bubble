/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.packer;

import bubble.cloud.CloudRegion;
import bubble.cloud.CloudRegionRelative;
import bubble.cloud.compute.ComputeConfig;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.cloud.compute.PackerConfig;
import bubble.cloud.compute.PackerImage;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.server.SoftwareVersions;
import bubble.service.cloud.GeoService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.system.Command;
import org.cobbzilla.util.system.CommandResult;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.util.time.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.ApiConstants.copyScripts;
import static bubble.model.cloud.RegionalServiceDriver.findClosestRegions;
import static bubble.server.SoftwareVersions.*;
import static bubble.service.packer.PackerService.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.io.StreamUtil.copyClasspathDirectory;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.getExternalIp;
import static org.cobbzilla.util.string.StringUtil.truncate;
import static org.cobbzilla.util.system.CommandShell.domainname;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYYMMDDHHMMSS;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;

@Slf4j
public class PackerJob implements Callable<List<PackerImage>> {

    public static final String PACKER_TEMPLATE = PACKER_DIR+"/packer.json.hbs";
    public static final String PACKER_IMAGE_NAME_VAR = "packerImageName";
    public static final String PACKER_IMAGE_PREFIX = "packer_bubble_";

    public static final String INSTALL_TYPE_VAR = "@@TYPE@@";
    public static final String SAGE_NET_VAR = "@@SAGE_NET@@";
    public static final String PACKER_VERSION_HASH_VAR = "@@PACKER_VERSION_HASH@@";
    public static final String BUBBLE_VERSION_VAR = "@@BUBBLE_VERSION@@";
    public static final String TIMESTAMP_VAR = "@@TIMESTAMP@@";
    public static final String PACKER_IMAGE_NAME_TEMPLATE = PACKER_IMAGE_PREFIX + INSTALL_TYPE_VAR
            + "_" + SAGE_NET_VAR
            + "_" + PACKER_VERSION_HASH_VAR
            + "_" + BUBBLE_VERSION_VAR
            + "_" + TIMESTAMP_VAR;

    public static final String VARIABLES_VAR = "packerVariables";
    public static final String BUILD_REGION_VAR = "buildRegion";
    public static final String IMAGE_REGIONS_VAR = "imageRegions";
    public static final String BUILDERS_VAR = "builders";
    public static final String POST_PROCESSOR_VAR = "postProcessor";
    public static final String SUDO_VAR = "sudo";
    public static final String PACKER_PLAYBOOK_TEMPLATE = "packer-playbook.yml.hbs";
    public static final String PACKER_PLAYBOOK = "packer-playbook.yml";
    public static final String PACKER_BINARY = System.getProperty("user.home")+"/packer/packer";

    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountDAO accountDAO;
    @Autowired private GeoService geoService;
    @Autowired private PackerService packerService;

    @Getter private final CloudService cloud;
    @Getter private final AnsibleInstallType installType;
    @Getter private final List<AtomicReference<List<PackerImage>>> imagesRefList = new ArrayList<>();
    @Getter private List<PackerImage> images = new ArrayList<>();
    @Getter private final long ctime = now();

    private final AtomicLong completedAt = new AtomicLong(0);
    public Long getCompletedAt() {
        long t = completedAt.get();
        return t == 0 ? null : t;
    }

    public PackerJob(CloudService cloud, AnsibleInstallType installType) {
        this.cloud = cloud;
        this.installType = installType;
    }

    public void addImagesRef(AtomicReference<List<PackerImage>> imagesRef) {
        synchronized (imagesRefList) {
            if (!images.isEmpty()) {
                imagesRef.set(images);
            } else {
                imagesRefList.add(imagesRef);
            }
        }
    }

    private void setImagesRefs() {
        synchronized (imagesRefList) {
            for (AtomicReference<List<PackerImage>> ref : imagesRefList) {
                ref.set(images);
            }
        }
    }

    public String cacheKey() { return PackerService.cacheKey(cloud, installType); }

    @Override public List<PackerImage> call() throws Exception {
        try {
            final List<PackerImage> images = _call();
            completedAt.set(now());
            packerService.recordJobCompleted(this);
            return images;

        } catch (Exception e) {
            completedAt.set(now());
            packerService.recordJobError(this, e);
            throw e;
        }
    }

    public List<PackerImage> _call() throws Exception {
        final ComputeConfig computeConfig = json(cloud.getDriverConfigJson(), ComputeConfig.class);
        final ComputeServiceDriver computeDriver = cloud.getComputeDriver(configuration);
        final PackerConfig packerConfig = computeConfig.getPacker();

        // create handlebars context
        final Map<String, Object> ctx = new HashMap<>();
        final CloudCredentials creds = cloud.getCredentials();
        ctx.put("credentials", creds == null ? Collections.emptyMap() : NameAndValue.toMap(creds.getParams()));
        ctx.put("compute", computeDriver);
        ctx.put("sizes", computeDriver.getSizesMap());
        ctx.put("os", computeDriver.getOs());

        // Find admin account
        final Account account = accountDAO.getFirstAdmin();

        // determine lat/lon to find closest cloud region to perform build in
        final GeoLocation here = geoService.locate(account.getUuid(), getExternalIp());
        final List<CloudRegionRelative> closestRegions = findClosestRegions(configuration, new SingletonList<>(cloud), null, here.getLatitude(), here.getLongitude());
        if (empty(closestRegions)) return die("no closest region could be determined");
        CloudRegionRelative buildRegion = closestRegions.get(0);
        ctx.put(BUILD_REGION_VAR, buildRegion);

        // set environment variables
        final Map<String, String> env = new HashMap<>();
        for (NameAndValue variable : packerConfig.getVars()) {
            env.put(variable.getName(), HandlebarsUtil.apply(configuration.getHandlebars(), variable.getValue(), ctx, '[', ']'));
        }
        if (!env.containsKey("HOME")) env.put("HOME", HOME_DIR);

        // Docker builder requires "docker" command to be on our path
        // It is usually in /usr/local/bin
        // May need to make this more flexible if docker is elsewhere, or other tools/paths are needed
        if (env.containsKey("PATH")) {
            env.put("PATH", "${PATH}:/usr/local/bin");
        } else {
            env.put("PATH", "/usr/local/bin");
        }
        ctx.put(VARIABLES_VAR, packerConfig.getVars());

        // copy ansible and other packer files to temp dir
        @Cleanup final TempDir tempDir = copyClasspathDirectory("packer");

        // record versions of algo, mitmproxy and dnscrypt_proxy
        writeSoftwareVars(ROLE_ALGO, tempDir);
        writeSoftwareVars(ROLE_MITMPROXY, tempDir);
        writeSoftwareVars(ROLE_BUBBLE, tempDir);

        // copy packer ssh key
        copyFile(packerService.getPackerPublicKey(), new File(abs(tempDir)+"/roles/common/files/"+PACKER_KEY_NAME));

        // copy bubble jar and scripts to role dir, calculate shasum for packer image name
        final File jar = configuration.getBubbleJar();
        final File bubbleFilesDir = new File(abs(tempDir)+"/roles/bubble/files");
        copyFile(jar, new File(abs(bubbleFilesDir)+"/bubble.jar"));
        copyScripts(bubbleFilesDir);

        // check to see if we have packer images for all regions
        final List<PackerImage> existingImages = computeDriver.getAllPackerImages();
        if (!empty(existingImages)) {
            final List<PackerImage> existingForInstallType = existingImages.stream()
                    .filter(i -> i.getName().startsWith(PACKER_IMAGE_PREFIX+installType.name()))
                    .collect(Collectors.toList());
            if (!empty(existingForInstallType)) {
                if (existingForInstallType.size() == 1 && existingForInstallType.get(0).getRegions() == null) {
                    // this image is for all regions
                    log.info("packer image already exists for "+installType+" for all regions");
                    images = existingForInstallType;
                    setImagesRefs();
                    return images;

                } else {
                    final List<CloudRegion> existingRegions = new ArrayList<>();
                    for (PackerImage image : existingForInstallType) {
                        existingRegions.addAll(Arrays.asList(image.getRegions()));
                    }
                    log.info("packer images already exist for "+installType+" for regions: "+existingRegions.stream().map(CloudRegion::getInternalName).collect(Collectors.joining(", ")));
                    final List<String> existingRegionNames = existingRegions.stream().map(CloudRegion::getInternalName).collect(Collectors.toList());;
                    // only create packer images for regions that are missing
                    final List<String> imagesToCreate = computeDriver.getRegions().stream()
                            .filter(r -> !existingRegionNames.contains(r.getInternalName()))
                            .map(CloudRegion::getInternalName)
                            .collect(Collectors.toList());
                    if (empty(imagesToCreate)) {
                        log.info("packer image already exists for "+installType+" for all regions");
                        images = existingForInstallType;
                        setImagesRefs();
                        return images;
                    }
                    ctx.put(IMAGE_REGIONS_VAR, toInnerStringList(imagesToCreate));
                }
            } else {
                // create list of all regions, without leading/trailing double-quote, which should already be in the template
                addAllRegions(computeDriver, ctx);
            }
        } else {
            // create list of all regions, without leading/trailing double-quote, which should already be in the template
            addAllRegions(computeDriver, ctx);
        }

        final String imageName = PACKER_IMAGE_NAME_TEMPLATE
                .replace(INSTALL_TYPE_VAR, installType.name())
                .replace(SAGE_NET_VAR, truncate(domainname(), 19))
                .replace(PACKER_VERSION_HASH_VAR, packerService.getPackerVersionHash())
                .replace(BUBBLE_VERSION_VAR, configuration.getShortVersion())
                .replace(TIMESTAMP_VAR, TimeUtil.format(now(), DATE_FORMAT_YYYYMMDDHHMMSS));
        if (imageName.length() > 128) return die("imageName.length > 128: "+imageName); // sanity check
        ctx.put(PACKER_IMAGE_NAME_VAR, imageName);

        final String packerConfigTemplate = stream2string(PACKER_TEMPLATE);
        ctx.put("installType", installType.name());
        ctx.put("roles", getRolesForInstallType(installType));

        final List<String> builderJsons = new ArrayList<>();
        if (packerConfig.iterateRegions()) {
            for (CloudRegion region : computeDriver.getRegions()) {
                ctx.put("region", region);
                final Map<String, Object> perRegionCtx = computeDriver.getPackerRegionContext(region);
                if (perRegionCtx != null) ctx.putAll(perRegionCtx);
                builderJsons.add(generateBuilder(packerConfig, ctx));
            }
        } else {
            builderJsons.add(generateBuilder(packerConfig, ctx));
        }
        ctx.put(BUILDERS_VAR, builderJsons);
        ctx.put(SUDO_VAR, packerConfig.sudo());

        if (packerConfig.hasPost()) ctx.put(POST_PROCESSOR_VAR, generatePostProcessor(packerConfig, ctx));

        // write playbook file
        final String playbookTemplate = FileUtil.toString(abs(tempDir)+ "/" + PACKER_PLAYBOOK_TEMPLATE);
        FileUtil.toFile(new File(tempDir, PACKER_PLAYBOOK), HandlebarsUtil.apply(configuration.getHandlebars(), playbookTemplate, ctx, '[', ']'));

        // write packer file
        final String packerJson = HandlebarsUtil.apply(configuration.getHandlebars(), packerConfigTemplate, ctx, '[', ']');
        toFileOrDie(abs(tempDir) + "/packer.json", packerJson);

        // run packer, return handle to running packer
        final long start = now();
        log.info("running packer for " + installType + "...");
        final int packerParallelBuilds = computeDriver.getPackerParallelBuilds();
        final CommandResult commandResult = CommandShell.exec(new Command(new CommandLine(PACKER_BINARY)
                .addArgument("build")
                .addArgument("-parallel-builds="+packerParallelBuilds)
                .addArgument("-color=false")
                .addArgument("packer.json"))
                .setDir(tempDir)
                .setEnv(env)
                .setCopyToStandard(true));

        if (commandResult.isZeroExitStatus()) {
            // read manifest, populate images
            final File packerManifestFile = new File(tempDir, "manifest.json");
            if (!packerManifestFile.exists()) {
                return die("Error executing packer: manifest file not found: " + abs(packerManifestFile));
            }
            final PackerManifest packerManifest = json(FileUtil.toString(packerManifestFile), PackerManifest.class);
            final PackerBuild[] builds = packerManifest.getBuilds();
            if (empty(builds)) {
                return die("Error executing packer: no builds found");
            }
            images.addAll(Arrays.stream(builds).map(b -> b.toPackerImage(imageName)).collect(Collectors.toList()));

        } else {
            final List<PackerImage> finalizedImages = computeDriver.finalizeIncompletePackerRun(commandResult, installType);
            if (empty(finalizedImages)) {
                return die("Error executing packer: exit status " + commandResult.getExitStatus());
            }
            images.addAll(finalizedImages);
        }

        setImagesRefs();
        log.info("packer images created in "+formatDuration(now() - start)+": "+images);
        return images;
    }

    private void writeSoftwareVars(String roleName, TempDir tempDir) throws IOException {
        final SoftwareVersions softwareVersions = configuration.getSoftwareVersions();
        final File varsDir = mkdirOrDie(abs(tempDir) + "/roles/"+roleName+"/vars");
        softwareVersions.writeAnsibleVars(new File(varsDir, "main.yml"));
    }

    private List<String> getRolesForInstallType(AnsibleInstallType installType) {
        switch (installType) {
            case sage: return SAGE_ROLES;
            case node: return NODE_ROLES;
            default: return die("getRolesForInstallType: invalid installType: "+installType);
        }
    }

    public void addAllRegions(ComputeServiceDriver computeDriver, Map<String, Object> ctx) {
        ctx.put(IMAGE_REGIONS_VAR, toInnerStringList(computeDriver.getRegions().stream()
                .map(CloudRegion::getInternalName)
                .collect(Collectors.toList())));
    }

    private String toInnerStringList(List<String> list) {
        if (empty(list)) return die("toInnerStringList: empty list");
        final StringBuilder b = new StringBuilder();
        for (String val : list) {
            if (b.length() > 0) b.append("\", \"");
            b.append(val);
        }
        return b.toString();
    }

    public String generateBuilder(PackerConfig packerConfig, Map<String, Object> ctx) {
        return appyHandlebars(ctx, packerConfig.getBuilder());
    }

    public String generatePostProcessor(PackerConfig packerConfig, Map<String, Object> ctx) {
        return packerConfig.hasPost() ? appyHandlebars(ctx, packerConfig.getPost()) : null;
    }

    private String appyHandlebars(Map<String, Object> ctx, JsonNode thing) {
        return HandlebarsUtil.apply(configuration.getHandlebars(), json(thing), ctx, '<', '>')
                .replace("[[", "{{")
                .replace("]]", "}}");
    }

}
