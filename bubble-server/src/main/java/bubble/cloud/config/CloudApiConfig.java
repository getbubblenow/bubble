/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.NameAndValue;

import java.util.HashMap;
import java.util.Map;

import static bubble.cloud.CloudServiceDriver.CTX_API_KEY;
import static bubble.cloud.CloudServiceDriver.CTX_PARAMS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@NoArgsConstructor @Accessors(chain=true)
public class CloudApiConfig {

    @Getter @Setter private String apiKey;
    public boolean hasApiKey () { return apiKey != null; }

    @Getter @Setter private NameAndValue[] params;

    public String getParam (String name) { return NameAndValue.find(params, name); }

    public int getIntParam(String name) {
        try {
            return Integer.parseInt(getParam(name));
        } catch (Exception e) {
            return die("getIntParam("+name+"): "+e);
        }
    }

    public Map<String, Object> getCtx(Map other) {
        final Map<String, Object> ctx = getCtx();
        ctx.putAll(other);
        return ctx;
    }

    public Map<String, Object> getCtx() {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(CTX_API_KEY, apiKey);
        ctx.put(CTX_PARAMS, params == null ? null : NameAndValue.toMap(params));
        return ctx;
    }

    public Map<String, Object> latLonCtx(String lat, String lon, Map<String, String> env) {
        final Map<String, Object> ctx = getCtx(env);
        ctx.put("lat", lat);
        ctx.put("lon", lon);
        return ctx;
    }

}
