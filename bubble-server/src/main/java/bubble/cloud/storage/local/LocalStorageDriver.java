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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.io.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_file;
import static org.cobbzilla.util.string.ValidationRegexes.UUID_PATTERN;

public class LocalStorageDriver extends CloudServiceDriverBase<LocalStorageConfig> implements StorageServiceDriver {

    public static final String SUFFIX_META = "._bubble_metadata";

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private AccountDAO accountDAO;

    public static final String LOCAL_STORAGE = "LocalStorage";
    public static final String LOCAL_STORAGE_STANDARD_BASE_DIR = "/home/bubble/.bubble_local_storage";

    public static final Class[] FATAL_EX_CLASSES = {FileNotFoundException.class};

    @Override public Class[] getFatalExceptionClasses() { return FATAL_EX_CLASSES; }

    @Override public boolean _exists(String fromNode, String key) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        if (from != null) {
            final File file = keyFile(from, key);
            return file.exists();
        }
        if (activated()) return false;
        // check classpath only if bubble has not been activated
        @Cleanup final InputStream in = getClass().getClassLoader().getResourceAsStream(key);
        return in != null;
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

        // only try classpath is bubble has not been activated
        if (activated()) return null;

        @Cleanup InputStream in = getClass().getClassLoader().getResourceAsStream(key);
        if (in == null) return null;

        // copy file to root network storage, so we can find it after activation
        final File file = keyFileForNetwork(ROOT_NETWORK_UUID, config.getBaseDir(), key);
        @Cleanup OutputStream out = new FileOutputStream(file);
        IOUtils.copyLarge(in, out);
        return new FileInputStream(file);
    }

    public boolean activated() {
        return accountDAO.activated() && configuration.getThisNode() != null;
    }

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
        IOUtils.copyLarge(data, out);
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

    @Override public boolean delete(String fromNode, String uri) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        final File file = keyFile(from, uri);
        FileUtils.forceDelete(file);
        return true;
    }

    @Override public boolean deleteNetwork(String networkUuid) throws IOException {
        FileUtils.forceDelete(keyFileForEntireNetwork(networkUuid));
        return true;
    }

    @Override public boolean rekey(String fromNode, CloudService newCloud) throws IOException { return notSupported("rekey"); }

    @Override public StorageListing list(String fromNode, String prefix) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        final File file = keyFile(from, prefix);
        final File rootFile = keyFile(from, "");
        final List<String> keys = new ArrayList<>();
        listFilesRecursively(file).forEach(f -> keys.add(abs(f).substring((int) rootFile.length())));
        return new StorageListing()
                .setListingId(null)
                .setTruncated(false)
                .setKeys(keys.toArray(new String[0]));
    }

    @Override public StorageListing listNext(String fromNode, String listingId) throws IOException {
        return notSupported("listNext");
    }

    protected BubbleNode getFromNode(String fromNode) {
        final BubbleNode node = nodeDAO.findByUuid(fromNode);
        return node != null ? node : !activated() ? null : die("fromNode not found: "+fromNode);
    }

    private File keyFile(BubbleNode from, String key) { return keyFile(from, config.getBaseDir(), key); }
    private File keyFileNoNetwork(String key) { return keyFileNoNetwork(config.getBaseDir(), key); }

    public static File keyFile(BubbleNode from, String baseDir, String key) {
        return keyFileForNetwork(from.getNetwork(), baseDir, key);
    }
    public File keyFileForEntireNetwork(String network) {
        return keyFileForNetwork(network, config.getBaseDir(), "");
    }
    public static File keyFileForNetwork(String network, String baseDir, String key) {
        return new File(baseDir + "/" + network + "/" + key);
    }
    public static File keyFileNoNetwork(String baseDir, String key) {
        return new File(baseDir + "/" + key);
    }

    public void migrateInitialData(BubbleNetwork network) {
        final File base = new File(config.getBaseDir());
        final File[] matched = base.listFiles((file, s) -> new File(file, s).isDirectory() && !UUID_PATTERN.matcher(s).find());
        if (matched != null) {
            Arrays.stream(matched)
                    .forEach(f -> {
                        final String path = abs(f);
                        final String key = path.substring(config.getBaseDir().length());
                        final File dest = new File(config.getBaseDir() + "/" + network.getUuid() + key);
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
