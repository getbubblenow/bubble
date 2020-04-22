/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.boot;

import bubble.server.BubbleConfiguration;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.security.bcrypt.BCryptUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;

import static bubble.ApiConstants.AUTH_ENDPOINT;
import static bubble.ApiConstants.EP_PATCH;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.io.FileUtil.toFileOrDie;

@Service @Slf4j
public class NodeManagerService {

    public static final File NODEMANAGER_PASSWORD_FILE = new File("/home/bubble/.nodemanager_pass");

    @Autowired private BubbleConfiguration configuration;

    public String generatePasswordOrNull() {
        if (NODEMANAGER_PASSWORD_FILE.exists()) return null;
        final String password = randomAlphanumeric(20);
        setPassword(password);
        return password;
    }

    public void setPassword(String password) { toFileOrDie(NODEMANAGER_PASSWORD_FILE, BCryptUtil.hash(password)); }

    public void disable() { toFileOrDie(NODEMANAGER_PASSWORD_FILE, "__disabled__"); }

    @Getter(lazy=true, value=AccessLevel.PROTECTED) private final ExpirationMap<String, File> patches = new ExpirationMap<>();

    public String registerPatch(File zipFile) {
        final String token = randomAlphanumeric(20);
        getPatches().put(token, zipFile);
        return configuration.getApiUriBase() + AUTH_ENDPOINT + EP_PATCH + "/" + token;
    }

    public File findPatch (String token) { return getPatches().get(token); }

}
