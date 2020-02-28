/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud;

import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

public abstract class CloudServiceDriverBase<T> implements CloudServiceDriver {

    @Getter(lazy=true) private final Class<T> firstTypeParam = ReflectionUtil.getFirstTypeParam(getClass());

    @Autowired protected BubbleConfiguration configuration;

    @Getter protected T config;
    protected CloudService cloud;

    @Getter @Setter protected CloudCredentials credentials;

    @Override public void setConfig(JsonNode json, CloudService cloudService) {
        config = json == null ? instantiate(getFirstTypeParam()) : json(json, getFirstTypeParam());
        cloud = cloudService;
    }

}
