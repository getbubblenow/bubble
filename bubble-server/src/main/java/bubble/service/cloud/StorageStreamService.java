/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service.cloud;

import bubble.cloud.storage.StorageServiceDriver;
import bubble.cloud.storage.WriteRequest;
import bubble.cloud.storage.delegate.DelegatedStorageDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.CloudService;
import bubble.notify.storage.StorageStreamRequest;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Service @Slf4j
public class StorageStreamService {

    public static final long TOKEN_TTL = SECONDS.toMillis(30);

    public static final String WR_PREFIX = "writeRequest:";

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private RedisService redis;

    @Getter(lazy=true) private final RedisService readRequests = redis.prefixNamespace("read:");

    public String registerRead(StorageStreamRequest request) {
        final String token = randomUUID().toString();
        request.setToken(token);
        getReadRequests().set(token, json(request.setToken(token)), EX, TOKEN_TTL);
        return token;
    }

    public String registerRead(StorageStreamRequest request, WriteRequest writeRequest) {
        final String token = WR_PREFIX + writeRequest.requestId;
        getReadRequests().set(token, json(request.setToken(token)), EX, TOKEN_TTL);
        return token;
    }

    public StorageStreamRequest findRead(String token) {
        final String val = getReadRequests().get(token);
        if (val == null) return null;
        return json(val, StorageStreamRequest.class);
    }

    public InputStream read(StorageStreamRequest request) throws IOException {
        final CloudService cloud = cloudStorage(request);
        final String fromNode = request.getFromNode();
        final StorageServiceDriver storageDriver = cloud.getStorageDriver(configuration);
        if (request.getToken().startsWith(WR_PREFIX) && DelegatedStorageDriver.class.isAssignableFrom(storageDriver.getClass())) {
            final DelegatedStorageDriver delegatedStorage = (DelegatedStorageDriver) storageDriver;
            final String requestId = request.getToken().substring(WR_PREFIX.length());
            final WriteRequest writeRequest = delegatedStorage.findWriteRequest(requestId);
            if (writeRequest == null) {
                throw notFoundEx(requestId);
            }
            log.debug("read: ("+request+"): returning writeRequest for key: "+request.getKey());
            return new FileInputStream(writeRequest.file);
        } else {
            log.debug("read: ("+request+"): reading directly from storage: "+request.getKey());
            return storageDriver.read(fromNode, request.getKey());
        }
    }

    private CloudService cloudStorage(StorageStreamRequest request) {
        final String cloudId = request.getCloud();
        final CloudService cloud = cloudDAO.findByUuid(cloudId);
        if (cloud == null) die("read: cloud not found: "+cloudId);
        return cloud;
    }

    public void clearToken(String token) { getReadRequests().del(token); }

}
