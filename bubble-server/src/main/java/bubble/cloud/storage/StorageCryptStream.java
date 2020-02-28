/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.storage;

import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.StorageMetadata;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.cobbzilla.util.security.CryptStream;

import java.io.IOException;
import java.io.InputStream;

import static bubble.model.cloud.CloudCredentials.PARAM_KEY;
import static org.cobbzilla.util.security.CryptStream.GCM_TAG_SIZE;
import static org.cobbzilla.util.security.ShaUtil.sha256;

@AllArgsConstructor
public class StorageCryptStream {

    public static final int MIN_KEY_LENGTH = 32;
    public static final int MIN_DISTINCT_LENGTH = 16;

    private final CloudCredentials credentials;

    @Getter(lazy=true) private final CryptStream cryptStream
            = new CryptStream(credentials.getPasswordParam(PARAM_KEY, MIN_KEY_LENGTH, MIN_DISTINCT_LENGTH));

    public InputStream wrapRead(InputStream in, String key, StorageMetadata metadata) throws IOException {
        return getCryptStream().wrapRead(in, getSalt(key, metadata), getAad(key, metadata));
    }

    public InputStream wrapWrite(InputStream data, String key, StorageMetadata metadata) throws IOException {
        return getCryptStream().wrapWrite(data, getSalt(key, metadata), getAad(key, metadata));
    }

    protected String getAad(String key, StorageMetadata metadata) { return metadata.getCnode()+":"+key+":"+metadata.getNonce(); }

    protected byte[] getSalt(String key, StorageMetadata metadata) {
        final byte[] salt = new byte[GCM_TAG_SIZE];
        System.arraycopy(sha256(metadata.getCnode()), 0, salt, 0, 32);
        System.arraycopy(sha256(key), 0, salt, 32, 32);
        System.arraycopy(sha256(metadata.getOrCreateNonce()), 0, salt, 64, 32);
        System.arraycopy(sha256(getAad(key, metadata)), 0, salt, 96, 32);
        return salt;
    }

}
