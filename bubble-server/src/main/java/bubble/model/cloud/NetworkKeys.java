/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.NameAndValue;

import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.CryptoUtil.string_decrypt;
import static org.cobbzilla.util.security.CryptoUtil.string_encrypt;

@NoArgsConstructor @Accessors(chain=true)
public class NetworkKeys {

    public static final String PARAM_STORAGE  = "storage";
    public static final String PARAM_STORAGE_CREDENTIALS = "credentials";

    @Getter @Setter private NameAndValue[] keys;

    public NetworkKeys addKey (String name, String value) { return setKeys(NameAndValue.update(keys, name, value)); }

    public EncryptedNetworkKeys encrypt(String password) { return new EncryptedNetworkKeys(this, password); }

    @NoArgsConstructor @Accessors(chain=true)
    public static class EncryptedNetworkKeys {

        public EncryptedNetworkKeys (NetworkKeys keys, String password) {
            setData(string_encrypt(json(keys), password));
        }

        public NetworkKeys decrypt () {
            return json(string_decrypt(getData(), getPassword()), NetworkKeys.class);
        }

        @Getter @Setter private String data;
        @Getter @Setter private String password;
    }
}
