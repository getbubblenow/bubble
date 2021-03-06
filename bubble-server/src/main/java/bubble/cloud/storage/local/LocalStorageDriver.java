/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.storage.local;

import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.storage.StorageServiceDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.StorageMetadata;
import bubble.notify.storage.StorageListing;
import lombok.Cleanup;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.system.OneWayFlag;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.util.Arrays;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_file;
import static org.cobbzilla.util.string.ValidationRegexes.UUID_PATTERN;

public class LocalStorageDriver extends CloudServiceDriverBase<LocalStorageConfig> implements StorageServiceDriver {

    public static final String SUFFIX_META = "._bubble_metadata";

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private AccountDAO accountDAO;

    public static final String LOCAL_STORAGE = "LocalStorage";
    public static final String BUBBLE_LOCAL_STORAGE_DIR = ".bubble_local_storage";
    public static final String LOCAL_STORAGE_STANDARD_BASE_DIR = "/home/bubble/" + BUBBLE_LOCAL_STORAGE_DIR;

    public static final Class[] FATAL_EX_CLASSES = {FileNotFoundException.class};

    @Override public Class[] getFatalExceptionClasses() { return FATAL_EX_CLASSES; }

    @Getter(lazy=true) private final String baseDir = initBaseDir();
    public String initBaseDir() {
        if (!empty(config.getBaseDir())) {
            final File base = new File(config.getBaseDir());
            if (base.isAbsolute()) return base.getAbsolutePath();
            return new File(HOME_DIR+"/"+config.getBaseDir()).getAbsolutePath();
        }

        final File standardBaseDir = new File(LOCAL_STORAGE_STANDARD_BASE_DIR);
        if ((standardBaseDir.exists() || standardBaseDir.mkdirs()) && standardBaseDir.canRead() && standardBaseDir.canWrite()) {
            return abs(standardBaseDir);
        }

        final File userBaseDir = new File(HOME_DIR+"/"+BUBBLE_LOCAL_STORAGE_DIR);
        if ((userBaseDir.exists() || userBaseDir.mkdirs()) && userBaseDir.canRead() && userBaseDir.canWrite()) {
            return abs(userBaseDir);
        }

        return die("getBaseDir: not set and no defaults exist");
    }

    @Override public boolean _exists(String fromNode, String key) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        if (from != null) {
            final File file = keyFile(from, key);
            return file.exists();
        }

        // check root network filesystem
        final File file = keyFileForNetwork(ROOT_NETWORK_UUID, getBaseDir(), key);
        if (file.exists()) return true;

        // check classpath
        @Cleanup final InputStream in = getClass().getClassLoader().getResourceAsStream(key);
        if (in == null) return false;

        // copy file to root network storage, so we can find it after activation
        if (!file.getParentFile().mkdirs()) return false;
        @Cleanup OutputStream out = new FileOutputStream(file);
        IOUtils.copyLarge(in, out);
        return true;
    }

    protected File metaFile(File f) { return new File(abs(f)+SUFFIX_META); }

    @Override public StorageMetadata readMetadata(String fromNode, String key) {
        final BubbleNode from = getFromNode(fromNode);
        final File f = keyFile(from, key);
        if (!f.exists()) return null;
        if (key.endsWith(SUFFIX_META)) return null; // no metadata-on-metadata (avoid infinite recursion)
        final File meta = metaFile(f);
        return meta.exists() ? json(FileUtil.toStringOrDie(meta), StorageMetadata.class) : null;
    }

    @Override public InputStream _read(String fromNode, String key) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        if (from != null) {
            final File f = keyFile(from, key);
            return f.exists() ? new FileInputStream(f) : null;
        }

        // check root network filesystem
        final File rootNetFile = keyFileForNetwork(ROOT_NETWORK_UUID, getBaseDir(), key);
        if (rootNetFile.exists()) return new FileInputStream(rootNetFile);

        // check classpath
        @Cleanup InputStream in = getClass().getClassLoader().getResourceAsStream(key);
        if (in == null) return null;

        // copy file to root network storage, so we can find it after activation
        final File file = keyFileForNetwork(ROOT_NETWORK_UUID, getBaseDir(), key);
        @Cleanup OutputStream out = new FileOutputStream(file);
        IOUtils.copyLarge(in, out);
        return new FileInputStream(file);
    }

    // once activated (any accounts exist), you can never go back
    // avoid crossing the Hibernate TX boundary by caching the result here
    private final OneWayFlag activated = new OneWayFlag("activated", () -> accountDAO.activated());
    public boolean activated() { return activated.check(); }

    @Override public boolean _write(String fromNode, String key, InputStream data, StorageMetadata metadata, String requestId) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        if (metadata == null) metadata = new StorageMetadata();

        final File f = keyFile(from, key);
        String fileSha;
        if (!f.exists()) {
            mkdirOrDie(f.getParentFile());
            fileSha = null;
        } else {
            fileSha = sha256_file(f);
            if (metadata.sameSha(fileSha)) {
                log.debug("_write: metadata sha256 was same as file, not writing, returning true");
                return true;
            }
        }
        @Cleanup final FileOutputStream out = new FileOutputStream(f);
        long countBytes = 0;
        try {
            IOUtils.copyLarge(data, out);
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("timeout")) {
                if (countBytes == 0) {
                    return die("LocalStorageDriver._write: error: " + e);
                }
            }
        } catch (Exception e) {
            return die("LocalStorageDriver._write: error: " + e);
        }
        if (fileSha == null) fileSha = sha256_file(f);

        // write meta-file
        metadata = new StorageMetadata();
        toFile(metaFile(f), json(metadata.setSha256(fileSha)));

        return true;
    }

    @Override public boolean canWrite(String fromNode, String toNode, String key) {
        // Only allows writes from nodes in same network as self
        final BubbleNode from = getFromNode(fromNode);
        return from != null && from.getNetwork().equals(configuration.getThisNetwork().getUuid());
    }

    @Override public boolean delete(String fromNode, String uri) {
        final BubbleNode from = getFromNode(fromNode);
        final File file = keyFile(from, uri);
        try {
            FileUtils.forceDelete(file);
        } catch (IOException e) {
            return die("delete: forceDelete("+abs(file)+") failed: "+shortError(e));
        }
        final File metaFile = metaFile(file);
        if (metaFile.exists()) {
            try {
                FileUtils.forceDelete(metaFile);
            } catch (IOException e) {
                log.warn("delete: forceDelete of meta file (" + abs(metaFile) + ") failed: " + shortError(e));
            }
        }
        return true;
    }

    @Override public boolean deleteNetwork(String networkUuid) throws IOException {
        FileUtils.forceDelete(keyFileForEntireNetwork(networkUuid));
        return true;
    }

    @Override public boolean rekey(String fromNode, CloudService newCloud) throws IOException { return notSupported("rekey"); }

    @Override @NonNull public StorageListing list(@NonNull final String fromNodeUuid, @NonNull final String prefix) {
        final var from = getFromNode(fromNodeUuid);
        final var rootPathLength = abs(keyFile(from, "")).length();
        final var keys = listFilesRecursively(keyFile(from, prefix)).stream()
                                                                    .filter(File::isFile)
                                                                    .map(FileUtil::abs)
                                                                    .map(path -> path.substring(rootPathLength))
                                                                    .toArray(String[]::new);
        return new StorageListing().setListingId(null)
                                   .setTruncated(false)
                                   .setKeys(keys);
    }

    @Override public StorageListing listNext(String fromNode, String listingId) throws IOException {
        return notSupported("listNext");
    }

    protected BubbleNode getFromNode(String fromNode) {
        final BubbleNode node = nodeDAO.findByUuid(fromNode);
        return node != null ? node : !activated() ? null : die("fromNode not found: "+fromNode);
    }

    private File keyFile(BubbleNode from, String key) { return keyFile(from, getBaseDir(), key); }
    private File keyFileNoNetwork(String key) { return keyFileNoNetwork(getBaseDir(), key); }

    public static File keyFile(BubbleNode from, String baseDir, String key) {
        return keyFileForNetwork(from.getNetwork(), baseDir, key);
    }
    public File keyFileForEntireNetwork(String network) {
        return keyFileForNetwork(network, getBaseDir(), "");
    }
    public static File keyFileForNetwork(String network, String baseDir, String key) {
        return new File(baseDir + "/" + network + "/" + key);
    }
    public static File keyFileNoNetwork(String baseDir, String key) {
        return new File(baseDir + "/" + key);
    }

    public void migrateInitialData(BubbleNetwork network) {
        final File base = new File(getBaseDir());
        final File[] matched = base.listFiles((file, s) -> new File(file, s).isDirectory() && !UUID_PATTERN.matcher(s).find());
        if (matched != null) {
            Arrays.stream(matched)
                    .forEach(f -> {
                        final String path = abs(f);
                        final String key = path.substring(getBaseDir().length());
                        final File dest = new File(getBaseDir() + "/" + network.getUuid() + key);
                        try {
                            FileUtils.copyDirectory(f, dest);
                        } catch (IOException e) {
                            die("migrateInitialData: error copying "+ path + "\n   -> " + abs(dest)+": "+e);
                        }
                        log.info("migrateInitialData: copied " + path + "\n   -> " + abs(dest));
                    });
            log.info("migrateInitialData: complete");
        }
    }
}
