package bubble.model.cloud;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.NameAndValue;

@NoArgsConstructor @Accessors(chain=true)
public class NetworkKeys {

    public static final String PARAM_STORAGE  = "storage";
    public static final String PARAM_STORAGE_CREDENTIALS = "credentials";

    @Getter @Setter private NameAndValue[] keys;

    public NetworkKeys addKey (String name, String value) {
        return setKeys(NameAndValue.update(keys, name, value));
    }

}
