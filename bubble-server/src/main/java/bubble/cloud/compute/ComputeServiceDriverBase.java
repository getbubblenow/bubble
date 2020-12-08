/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import bubble.cloud.CloudRegion;
import bubble.cloud.CloudServiceDriverBase;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudCredentials;
import bubble.service.packer.PackerService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.toMap;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public abstract class ComputeServiceDriverBase
        extends CloudServiceDriverBase<ComputeConfig>
        implements ComputeServiceDriver {

    public static final long PACKER_TIMEOUT = MINUTES.toMillis(60);

    private static final Map<CloudCredentials, NodeReaper> reapers = new HashMap<>();

    @Override public void postSetup() {
        final String prefix = "postSetup("+getClass().getSimpleName()+"/"+cloud.getUuid()+"): ";
        final BubbleNetwork thisNetwork = configuration.getThisNetwork();
        if (thisNetwork != null && thisNetwork.sage()) {
            if (cloud.delegated()) {
                log.info(prefix + "NOT starting NodeReaper for delegated driver");
            } else {
                synchronized (reapers) {
                    if (reapers.get(getCredentials()) == null) {
                        log.info(prefix+"starting NodeReaper");
                        final NodeReaper reaper = new NodeReaper(this);
                        reapers.put(getCredentials(), reaper);
                        configuration.autowire(reaper).start();
                    } else {
                        log.info(prefix+"NodeReaper already running with same credentials, not starting another");
                    }
                }
            }
        } else {
            log.info(prefix+"not self-sage, not starting NodeReaper");
        }
    }

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected PackerService packerService;

    public abstract List<BubbleNode> listNodes() throws IOException;

    protected abstract List<CloudRegion> getCloudRegions();
    protected abstract List<ComputeNodeSize> getCloudSizes();
    protected abstract List<OsImage> getCloudOsImages();

    @Getter(lazy=true) private final List<CloudRegion> regions = initRegions();
    private List<CloudRegion> initRegions() {
        final ArrayList<CloudRegion> cloudRegions = new ArrayList<>();
        if (config != null && config.getRegions() != null) {
            for (CloudRegion configRegion : config.getRegions()) {
                final CloudRegion cloudRegion = getCloudRegions().stream()
                        .filter(s -> s.getInternalName().equalsIgnoreCase(configRegion.getInternalName()))
                        .findFirst()
                        .orElse(null);
                if (cloudRegion == null) {
                    log.warn("initRegions: config region not found: " + configRegion.getInternalName());
                } else {
                    cloudRegions.add(configRegion.setId(cloudRegion.getId()));
                }
            }
        }
        return cloudRegions;
    }

    @Getter(lazy=true) private final List<ComputeNodeSize> sizes = initSizes();
    private List<ComputeNodeSize> initSizes() {
        final ArrayList<ComputeNodeSize> cloudSizes = new ArrayList<>();
        for (ComputeNodeSize configSize : config.getSizes()) {
            final ComputeNodeSize cloudSize = getCloudSizes().stream().filter(sz -> sz.getInternalName().equals(configSize.getInternalName())).findFirst().orElse(null);
            if (cloudSize == null) {
                log.warn("initSizes: config region not found: "+configSize.getInternalName());
            } else {
                cloudSizes.add(cloudSize
                        .setName(configSize.getName())
                        .setType(configSize.getType()));
            }
        }
        return cloudSizes;
    }

    @Getter(lazy=true) private final OsImage os = initOs();
    protected OsImage initOs() {
        final OsImage os = getCloudOsImages().stream()
                .filter(s -> s.getName().equals(config.getOs()))
                .findFirst()
                .orElse(null);
        if (os == null) return die("initOs: os not found: "+config.getOs());
        return os;
    }

    @Override public CloudRegion getRegion(String region) {
        return getRegions().stream()
                .filter(r -> r.getName().equalsIgnoreCase(region) || r.getInternalName().equalsIgnoreCase(region))
                .findAny().orElse(null);
    }

    @Override public ComputeNodeSize getSize(ComputeNodeSizeType type) {
        return getSizes().stream()
                .filter(s -> s.getType() == type)
                .findAny().orElse(null);
    }

    @Override public boolean supportsPacker(AnsibleInstallType installType) {
        return installType == AnsibleInstallType.sage || getConfig().getDeployment().isNginx();
    }

    @Override public void addLaunchContext(Map<String, Object> ctx, String prefix) {
        ctx.putAll(toMap(getConfig().getDeployment(), prefix));
    }

    public PackerImage getOrCreatePackerImage(BubbleNode node) {
        PackerImage packerImage = getPackerImage(node.getInstallType(), node.getRegion());
        if (packerImage == null) {
            node.setPackerImageCreation(true);
            final AtomicReference<List<PackerImage>> imagesRef = new AtomicReference<>();
            packerService.writePackerImages(cloud, node.getInstallType(), imagesRef);
            long start = now();
            while (imagesRef.get() == null && now() - start < PACKER_TIMEOUT) {
                sleep(SECONDS.toMillis(1), "getPackerImage: waiting for packer image creation");
            }
            if (imagesRef.get() == null) {
                return die("getPackerImage: timeout creating packer image");
            }
            packerImage = getPackerImage(node.getInstallType(), node.getRegion());
            if (packerImage == null) {
                return die("getPackerImage: error creating packer image");
            }
            node.setPackerImageCreation(false);
        }
        return packerImage;
    }

    private PackerImage getPackerImage(AnsibleInstallType installType, String region) {
        final List<PackerImage> images = getPackerImagesForRegion(region);
        return images == null ? null : images.stream()
                .filter(i -> i.getName().contains("_"+installType.name()+"_"))
                .findFirst()
                .orElse(null);
    }

}
