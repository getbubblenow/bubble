/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.packer;

import bubble.cloud.compute.PackerImage;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.DaemonThreadFactory;
import org.cobbzilla.util.security.ShaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.mkdirOrDie;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.string.StringUtil.splitAndTrim;
import static org.cobbzilla.util.system.CommandShell.chmod;
import static org.cobbzilla.util.system.CommandShell.execScript;

@Service @Slf4j
public class PackerService {

    public static final String PACKER_DIR = "packer";

    public static final List<String> SAGE_ROLES = splitAndTrim(stream2string(PACKER_DIR + "/sage-roles.txt"), "\n")
            .stream().filter(s -> !empty(s)).collect(Collectors.toList());

    public static final List<String> NODE_ROLES = splitAndTrim(stream2string(PACKER_DIR + "/node-roles.txt"), "\n")
            .stream().filter(s -> !empty(s)).collect(Collectors.toList());
    public static final String PACKER_KEY_NAME = "packer_rsa";

    private final Map<String, Future<List<PackerImage>>> activeJobs = new ConcurrentHashMap<>(16);
    private final Map<String, List<PackerImage>> completedJobs = new ConcurrentHashMap<>(16);
    private final ExecutorService pool = DaemonThreadFactory.fixedPool(5);

    @Autowired private BubbleConfiguration configuration;

    public List<PackerImage> writePackerImages(CloudService cloud,
                                               AnsibleInstallType installType,
                                               AtomicReference<List<PackerImage>> imagesRef) {
        final String cacheKey = cacheKey(cloud, installType);
        synchronized (activeJobs) {
            final List<PackerImage> images = completedJobs.get(cacheKey);
            if (images != null) return images;
            activeJobs.computeIfAbsent(cacheKey, k -> {
                final PackerJob packerJob = configuration.autowire(new PackerJob(cloud, installType, imagesRef));
                return pool.submit(packerJob);
            });
            return null;
        }
    }

    public static String cacheKey(CloudService cloud, AnsibleInstallType installType) {
        return cloud.getUuid()+"_"+installType;
    }

    public void recordJobCompleted(PackerJob job) {
        synchronized (activeJobs) {
            activeJobs.remove(job.cacheKey());
            completedJobs.put(job.cacheKey(), job.getImages());
        }
    }

    public void recordJobError(PackerJob job, Exception e) {
        log.error("recordJobError: "+shortError(e), e);
        activeJobs.remove(job.cacheKey());
    }

    public File getPackerPublicKey () { return initPackerKey(true); }
    public File getPackerPrivateKey () { return initPackerKey(false); }
    public String getPackerPublicKeyHash () { return ShaUtil.sha256_file(getPackerPublicKey()); }

    private synchronized File initPackerKey(boolean pub) {
        final File keyDir = new File(System.getProperty("user.home"),".ssh");
        if (!keyDir.exists()) mkdirOrDie(keyDir);
        chmod(keyDir, "700");
        final File pubKeyFile = new File(keyDir, PACKER_KEY_NAME+".pub");
        final File privateKeyFile = new File(keyDir, PACKER_KEY_NAME);
        if (!pubKeyFile.exists() || !privateKeyFile.exists()) {
            final String comment = configuration.getVersion() + "_" + configuration.getJarSha();
            execScript("ssh-keygen -t rsa -q -N '' -C '"+comment+"' -f "+abs(privateKeyFile));
            if (!pubKeyFile.exists() || !privateKeyFile.exists()) return die("initPackerKey: error creating packer key");
        }
        return pub ? pubKeyFile : privateKeyFile;
    }

}