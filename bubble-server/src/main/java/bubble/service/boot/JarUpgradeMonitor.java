package bubble.service.boot;

import bubble.model.cloud.BubbleVersionInfo;
import org.cobbzilla.util.daemon.SimpleDaemon;

import java.io.File;

public abstract class JarUpgradeMonitor extends SimpleDaemon {

    public abstract void downloadJar(File upgradeJar, BubbleVersionInfo sageVersion);

}
