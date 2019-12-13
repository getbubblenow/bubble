package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static bubble.cloud.storage.StorageCryptStream.MIN_DISTINCT_LENGTH;
import static bubble.cloud.storage.StorageCryptStream.MIN_KEY_LENGTH;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_OCTET_STREAM;
import static org.cobbzilla.util.http.HttpContentTypes.contentType;
import static org.cobbzilla.util.security.CryptoUtil.generatePassword;
import static org.cobbzilla.util.security.ShaUtil.sha256_file;

@NoArgsConstructor @Accessors(chain=true)
public class StorageMetadata implements Serializable {

    public static final StorageMetadata EMPTY_METADATA = new StorageMetadata() {
        @Override public StorageMetadata setName(String name) { return notSupported(); }
        @Override public StorageMetadata setContentType(String contentType) { return notSupported(); }
        @Override public StorageMetadata setContentLength(Long contentLength) { return notSupported(); }
        @Override public StorageMetadata setSha256(String sha256) { return notSupported(); }
    };

    public static final String META_NAME = "name";
    public static final String META_CNODE = "cnode";
    public static final String META_MNODE = "mnode";
    public static final String META_CTIME = "ctime";
    public static final String META_MTIME = "mtime";
    public static final String META_CONTENT_TYPE = "contentType";
    public static final String META_CONTENT_LENGTH = "contentLength";
    public static final String META_SHA256 = "sha256";
    public static final String META_NONCE = "nonce";

    public StorageMetadata(File file) {
        setName(file.getName());
        setSha256(sha256_file(file));
        setContentLength(file.length());
        if (getName().contains(".")) setContentType(contentType(getName()));
    }

    @Getter @Setter private String name;
    public boolean hasName () { return !empty(name); }

    @Getter @Setter private String cnode;
    public boolean hasCnode() { return !empty(cnode); }

    @Getter @Setter private String mnode;
    public boolean hasMnode() { return !empty(mnode); }

    @Getter @Setter private Long ctime = now();
    @Getter @Setter private Long mtime = now();

    @Getter @Setter private String contentType = APPLICATION_OCTET_STREAM;
    public boolean hasContentType () { return !empty(contentType); }

    @Getter @Setter private Long contentLength;
    public boolean hasContentLength () { return contentLength != null && contentLength >= 0; }

    @Getter @Setter private String sha256;
    public boolean hasSha256 () { return !empty(sha256); }
    public boolean sameSha(String sha256) { return sha256 != null && hasSha256() && sha256.equals(getSha256()); }

    @Getter @Setter private String nonce;
    public boolean hasNonce () { return !empty(nonce); }

    @JsonIgnore @Transient public String getOrCreateNonce() {
        return hasNonce() ? getNonce() : setNonce(generatePassword(MIN_KEY_LENGTH, MIN_DISTINCT_LENGTH)).getNonce();
    }

    @JsonIgnore @Transient @Getter @Setter private transient boolean forceWrite = false;

    public static StorageMetadata fromMap (Map<String, String> map) {
        final StorageMetadata meta = new StorageMetadata();
        if (map != null) {
            if (map.containsKey(META_NAME)) meta.setName(map.get(META_NAME));
            if (map.containsKey(META_CNODE)) meta.setCnode(map.get(META_CNODE));
            if (map.containsKey(META_MNODE)) meta.setMnode(map.get(META_MNODE));
            if (map.containsKey(META_CTIME)) meta.setCtime(safeLong(map.get(META_CTIME)));
            if (map.containsKey(META_MTIME)) meta.setMtime(safeLong(map.get(META_MTIME)));
            if (map.containsKey(META_CONTENT_TYPE)) meta.setContentType(map.get(META_CONTENT_TYPE));
            if (map.containsKey(META_CONTENT_LENGTH)) meta.setContentLength(safeLong(map.get(META_CONTENT_LENGTH)));
            if (map.containsKey(META_SHA256)) meta.setSha256(map.get(META_SHA256));
            if (map.containsKey(META_NONCE)) meta.setNonce(map.get(META_NONCE));
        }
        return meta;
    }

    public Map<String, String> toMap() {
        final Map<String, String> m = new HashMap<>();
        if (hasName()) m.put(META_NAME, getName());
        if (hasCnode()) m.put(META_CNODE, getCnode());
        if (hasMnode()) m.put(META_MNODE, getMnode());
        m.put(META_CTIME, ""+getCtime());
        m.put(META_MTIME, ""+getMtime());
        if (hasContentType()) m.put(META_CONTENT_TYPE, getContentType());
        if (hasContentLength()) m.put(META_CONTENT_LENGTH, ""+getContentLength());
        if (hasSha256()) m.put(META_SHA256, getSha256());
        m.put(META_NONCE, getOrCreateNonce());
        return m;
    }

}
