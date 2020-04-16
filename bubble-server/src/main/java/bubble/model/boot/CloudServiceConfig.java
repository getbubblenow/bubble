/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.boot;

import bubble.model.cloud.CloudCredentials;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.NameAndValue;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class CloudServiceConfig {

    @Getter @Setter private Map<String, String> config = new LinkedHashMap<>();
    public boolean hasConfig() { return !empty(config); }

    public CloudServiceConfig addConfig(String name, String value) {
        getConfig().put(name, value);
        return this;
    }

    @Getter @Setter private Map<String, String> credentials = new LinkedHashMap<>();
    public boolean hasCredentials() { return !empty(credentials); }

    @JsonIgnore public CloudCredentials getCredentialsObject() {
        return new CloudCredentials(NameAndValue.map2list(getCredentials()).toArray(NameAndValue.EMPTY_ARRAY));
    }

}
