/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.delegate;

import bubble.cloud.CloudRegion;
import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.cloud.compute.*;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.compute.ComputeDriverNotification;
import org.cobbzilla.util.collection.ExpirationMap;

import java.util.Arrays;
import java.util.List;

import static bubble.model.cloud.notify.NotificationType.*;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public class DelegatedComputeDriver extends DelegatedCloudServiceDriverBase implements ComputeServiceDriver {

    public DelegatedComputeDriver(CloudService cloud) { super(cloud); }

    protected ComputeDriverNotification notification() { return new ComputeDriverNotification().setComputeService(cloud.getDelegated()); }
    protected ComputeDriverNotification notification(BubbleNode n) { return new ComputeDriverNotification(n).setComputeService(cloud.getDelegated()); }

    @Override public List<ComputeNodeSize> getSizes() {
        final BubbleNode delegate = getDelegateNode();
        final ComputeNodeSize[] sizes = notificationService.notifySync(delegate, compute_driver_get_sizes, notification());
        return Arrays.asList(sizes);
    }

    @Override public ComputeNodeSize getSize(ComputeNodeSizeType type) {
        return getSizes().stream()
                .filter(s -> s.getType() == type)
                .findAny()
                .orElse(null);
    }

    private static final ExpirationMap<String, List<CloudRegion>> regionsCache = new ExpirationMap<>(DAYS.toMillis(1));

    @Override public List<CloudRegion> getRegions() {
        final BubbleNode delegate = getDelegateNode();
        final String cacheKey = delegate.id()+"/"+cloud.getUuid();
        return regionsCache.computeIfAbsent(cacheKey, k -> {
            final CloudRegion[] regions = notificationService.notifySync(delegate, compute_driver_get_regions, notification());
            return Arrays.asList(regions);
        });
    }

    @Override public CloudRegion getRegion(String region) {
        return getRegions().stream()
                .filter(r -> r.getInternalName().equalsIgnoreCase(region) || r.getName().equalsIgnoreCase(region))
                .findAny()
                .orElse(null);
    }

    @Override public OsImage getOs() {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, compute_driver_get_os, notification());
    }

    @Override public BubbleNode start(BubbleNode node) throws Exception {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, compute_driver_start, notification(node));
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, compute_driver_cleanup_start, notification(node));
    }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, compute_driver_stop, notification(node));
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, compute_driver_status, notification(node));
    }

    @Override public List<PackerImage> getAllPackerImages() { return notSupported("getPackerImages"); }
    @Override public List<PackerImage> getPackerImagesForRegion(String region) { return notSupported("getPackerImagesForRegion"); }


}
