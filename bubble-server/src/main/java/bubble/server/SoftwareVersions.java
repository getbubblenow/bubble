/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static bubble.ApiConstants.HOME_DIR;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpUtil.url2string;

@Slf4j
public class SoftwareVersions {

    public static final String ROLE_ALGO = "algo";
    public static final String ROLE_MITMPROXY = "mitmproxy";
    public static final String ROLE_DNSCRYPT = "dnscrypt-proxy";
    public static final String ROLE_BUBBLE = "bubble";
    public static final String[] VERSIONED_SOFTWARE = {ROLE_DNSCRYPT, ROLE_ALGO, ROLE_MITMPROXY};

    public static final File SOFTWARE_VERSIONS_FILE = new File(HOME_DIR+"/bubble_versions.properties");

    public static final String SUFFIX_VERSION = "_version";
    public static final String SUFFIX_SHA = "_sha";

    private final String releaseUrlBase;

    public SoftwareVersions (String releaseUrlBase) { this.releaseUrlBase = releaseUrlBase; }

    public String getRolePropBase(String roleName) { return roleName.replace("-", "_"); }

    public String getLatestVersion(String r) {
        try {
            return url2string(releaseUrlBase+"/"+ r +"/latest.txt").trim();
        } catch (IOException e) {
            return die("getLatestVersion("+ r +"): "+shortError(e), e);
        }
    }

    public String downloadHash(String roleName, String version) {
        try {
            return url2string(releaseUrlBase+"/"+ roleName +"/"+ version +"/"+ roleName +getSoftwareSuffix(roleName)+".sha256").trim();
        } catch (IOException e) {
            return die("getSoftwareHash("+ roleName +"): "+shortError(e), e);
        }
    }

    @Getter(lazy=true) private final Properties defaultSoftwareVersions = initDefaultSoftwareVersions();
    private Properties initDefaultSoftwareVersions() {
        if (empty(releaseUrlBase)) {
            log.warn("initDefaultSoftwareVersions: releaseUrlBase not defined");
            return null;
        }
        final Properties props = new Properties();
        if (!SOFTWARE_VERSIONS_FILE.exists()) {
            // write latest versions
            for (String roleName : VERSIONED_SOFTWARE) {
                final String latestVersion = getLatestVersion(roleName);
                props.setProperty(getRolePropBase(roleName)+SUFFIX_VERSION, latestVersion);
                props.setProperty(getRolePropBase(roleName)+SUFFIX_SHA, downloadHash(roleName, latestVersion));
            }
            writeVersions(props, SOFTWARE_VERSIONS_FILE);
        }
        try (InputStream in = new FileInputStream(SOFTWARE_VERSIONS_FILE)) {
            props.load(in);
            return props;
        } catch (Exception e) {
            log.error("initDefaultSoftwareVersions: "+shortError(e));
            return null;
        }
    }

    public void writeVersions(File file) { writeVersions(getDefaultSoftwareVersions(), file); }

    public void writeVersions(Properties props, File file) {
        try (OutputStream out = new FileOutputStream(file)) {
            props.store(out, null);
        } catch (Exception e) {
            log.error("saveSoftwareVersions: "+shortError(e));
        }
    }

    public void writeAnsibleVars(File file) { writeAnsibleVars(getDefaultSoftwareVersions(), file);  }

    public void writeAnsibleVars(Properties props, File file) {
        try (OutputStream out = new FileOutputStream(file)) {
            final StringBuilder b = new StringBuilder();
            for (String name : props.stringPropertyNames()) {
                b.append(name).append(" : '").append(props.getProperty(name)).append("'\n");
            }
            FileUtil.toFile(file, b.toString());

        } catch (Exception e) {
            die("writeAnsibleVars: "+shortError(e));
        }
    }

    private final Map<String, String> softwareVersions = new HashMap<>();

    public String getSoftwareVersion(String roleName) {
        final Properties defaults = getDefaultSoftwareVersions();
        if (defaults != null) {
            final String propName = getRolePropBase(roleName) + SUFFIX_VERSION;
            final String version = defaults.getProperty(propName);
            if (version != null) return version;
        }
        return softwareVersions.computeIfAbsent(roleName, this::getLatestVersion);
    }

    private final Map<String, String> softwareHashes = new HashMap<>();

    public String getSoftwareHash(String roleName, String version) {
        final Properties defaults = getDefaultSoftwareVersions();
        if (defaults != null) {
            final String roleBase = getRolePropBase(roleName);
            final String foundVersion = defaults.getProperty(roleBase + SUFFIX_VERSION);
            if (foundVersion != null && foundVersion.equals(version)) {
                final String hash = defaults.getProperty(roleBase + SUFFIX_SHA);
                if (hash != null) return hash;
            }
        }
        return softwareHashes.computeIfAbsent(roleName, r -> downloadHash(r, version));
    }

    private String getSoftwareSuffix(String roleName) {
        switch (roleName) {
            case ROLE_ALGO: case ROLE_MITMPROXY: return ".zip";
            case ROLE_DNSCRYPT: return "";
            default: return die("getSoftwareSuffix: unrecognized roleName: "+roleName);
        }
    }

}
