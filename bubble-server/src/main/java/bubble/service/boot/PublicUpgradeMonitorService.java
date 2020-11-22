/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.boot;

import bubble.model.cloud.BubbleVersionInfo;
import bubble.server.BubbleConfiguration;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.util.io.Decompressors;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpUtil.url2file;
import static org.cobbzilla.util.http.HttpUtil.url2string;
import static org.cobbzilla.util.io.Decompressors.extract;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.wizard.model.SemanticVersion.SEMANTIC_VERSION_RE;
import static org.cobbzilla.wizard.model.SemanticVersion.isNewerVersion;

@Service @Slf4j
public class PublicUpgradeMonitorService extends JarUpgradeMonitor {

    public static final long MONITOR_INTERVAL = HOURS.toMillis(6);
    public static final long MONITOR_START_DELAY = SECONDS.toMillis(30);

    @Override protected long getStartupDelay() { return MONITOR_START_DELAY; }
    @Override protected long getSleepTime() { return MONITOR_INTERVAL; }
    @Override protected boolean canInterruptSleep() { return true; }

    public static final String BUBBLE_BASE_URI = "https://jenkins.bubblev.org/public/releases/bubble/";
    public static final String RELEASE_VERSION_URL = BUBBLE_BASE_URI + "latest.txt";

    public static final String VERSION_TOKEN = "@@VERSION@@";
    public static final String RELEASE_JAR_URL = BUBBLE_BASE_URI + VERSION_TOKEN + "/bubble.zip";
    public static final String RELEASE_SHA_URL = BUBBLE_BASE_URI + VERSION_TOKEN + "/bubble.zip.sha256";

    @Autowired private BubbleConfiguration configuration;

    @Override protected void process() {
        try {
            final String rawVersion = url2string(RELEASE_VERSION_URL).trim();
            final String fullVersion = rawVersion.replace("_", " ");
            String currentVersion = configuration.getVersionInfo().getVersion();
            // only update our sage version if the new public version is both
            // -- newer than ourselves
            // -- newer than the current sageVersion (or the current sageVersion is null)
            final BubbleVersionInfo currentSageVersion = configuration.getSageVersion();
            final String prefix = "process: latest public version ("+fullVersion+") is ";
            if (isNewerVersion(currentVersion, fullVersion)) {
                if (currentSageVersion == null || empty(currentSageVersion.getVersion()) || isNewerVersion(currentSageVersion.getVersion(), fullVersion)) {
                    log.info(prefix+"newer than current version ("+currentVersion+"), setting configuration.sageVersion");
                    final String shortVersion = fullVersion.substring(fullVersion.indexOf(" ") + 1);
                    final String shaUrl = RELEASE_SHA_URL.replace(VERSION_TOKEN, rawVersion);
                    configuration.setSageVersion(new BubbleVersionInfo()
                            .setVersion(fullVersion)
                            .setShortVersion(shortVersion)
                            .setSha256(url2string(shaUrl)));
                } else {
                    log.info(prefix+"newer than current version ("+currentVersion+") but not the existing configuration.sageVersion ("+currentSageVersion+"), not setting configuration.sageVersion");
                }
            } else {
                log.info(prefix+"not newer than current version ("+currentVersion+"), not setting configuration.sageVersion");
            }
        } catch (Exception e) {
            log.warn("process: error: "+shortError(e));
        }
    }

    @Override public void downloadJar(File upgradeJar, BubbleVersionInfo sageVersion) {
        try {
            @Cleanup final TempDir temp = new TempDir();
            final File bubbleZip = new File(temp, "bubble.zip");
            final String jarUrl = RELEASE_JAR_URL.replace(VERSION_TOKEN, sageVersion.getVersion().replace(" ", "_"));
            log.info("downloadJar: downloading from "+jarUrl+" -> "+abs(bubbleZip));
            url2file(jarUrl, bubbleZip);
            extract(bubbleZip, temp);
            final File jarFile = findFile(temp, "bubble.jar");
            if (jarFile == null) {
                die("downloadJar: bubble.jar file not found in zip file");
            }
            copyFile(jarFile, upgradeJar);

        } catch (Exception e) {
            die("downloadJar: "+shortError(e), e);
        }
    }

}
