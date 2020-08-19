/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;

import java.io.Serializable;
import java.util.Map;

import static bubble.cloud.storage.StorageCryptStream.MIN_DISTINCT_LENGTH;
import static bubble.cloud.storage.StorageCryptStream.MIN_KEY_LENGTH;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.security.CryptoUtil.generatePassword;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
@EqualsAndHashCode(of={"params"}) @Slf4j
public class CloudCredentials implements Serializable {

    public static final String PARAM_DELEGATE_NODE = "delegate_node";
    public static final String PARAM_DELEGATE_API = "delegate_api";
    public static final String PARAM_NETWORK = "network";
    public static final String PARAM_KEY = "data_key";

    @Getter @Setter private NameAndValue[] params;

    public CloudCredentials(CloudCredentials credentials) {
        this.params = credentials.getParams();
    }

    public static CloudCredentials delegate(BubbleNode node, BubbleConfiguration configuration) {
        if (node == null) {
            return die("Cannot delegate to null node");
        }
        return new CloudCredentials(new NameAndValue[] {
                new NameAndValue(PARAM_DELEGATE_NODE, node.getUuid()),
                new NameAndValue(PARAM_DELEGATE_API, configuration.getApiUriBase())
        });
    }

    public String getParam(String name) { return NameAndValue.find(params, name); }
    public CloudCredentials setParam(String name, String value) { return setParams(NameAndValue.update(params, name, value)); }

    public String getPasswordParam(String name, int minLength, int minDistinct) {
        final String value = NameAndValue.find(params, name);
        if (value == null) return null;
        if (value.length() < minLength) {
            return die("getPasswordParam("+name+"): too short: "+value.length()+" < "+minLength);
        }
        final long count = value.chars().distinct().count();
        if (count < minDistinct) {
            return die("getPasswordParam("+name+"): insufficient complexity: "+count+" < "+minDistinct);
        }
        return value;
    }

    public boolean hasParam(String name) { return getParam(name) != null; }

    @JsonIgnore public String getDelegateNode () { return getParam(PARAM_DELEGATE_NODE); }
    @JsonIgnore public boolean isDelegate() { return getDelegateNode() != null; }

    @JsonIgnore public boolean needsNewNetworkKey(String network) {
        return !hasParam(PARAM_NETWORK) || !getParam(PARAM_NETWORK).equals(network);
    }
    public CloudCredentials initNetworkKey (String network) {
        setParam(PARAM_NETWORK, network);
        setParam(PARAM_KEY, generatePassword(MIN_KEY_LENGTH, MIN_DISTINCT_LENGTH));
        return this;
    }

    public int getIntParam(String param) { return getIntParam(param, null); }

    public int getIntParam(String param, Integer defaultValue) {
        final String val = getParam(param);
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            if (defaultValue == null) return die("getIntParam: invalid value: "+val);
            log.warn("getIntParam: invalid value: "+val+", returning default="+defaultValue);
            return defaultValue;
        }
    }

    public Map<String, Object> newContext() { return (Map) NameAndValue.toMap(getParams()); }

}
