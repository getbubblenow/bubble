/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.device;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.device.FlexRouter;
import bubble.service.device.FlexRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static bubble.ApiConstants.getKnownHostKey;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.hibernate.criterion.Restrictions.*;

@Repository @Slf4j
public class FlexRouterDAO extends AccountOwnedEntityDAO<FlexRouter> {

    @Autowired private FlexRouterService flexRouterService;

    @Override protected String getNameField() { return "ip"; }

    @Override public Object preCreate(FlexRouter router) {
        return super.preCreate(router.setInitialized(false));
    }

    @Override public FlexRouter postCreate(FlexRouter router, Object context) {
        flexRouterService.register(router);
        router.setHost_key(getKnownHostKey());
        return super.postCreate(router, context);
    }

    @Override public Object preUpdate(FlexRouter router) {
        final FlexRouter existing = findByUuid(router.getUuid());
        if (!existing.getIp().equals(router.getIp())) throw invalidEx("err.ip.cannotChange");
        if (!existing.getPort().equals(router.getPort())) throw invalidEx("err.port.cannotSetOrChange");
        if (!existing.getKeyHash().equals(router.getKeyHash())) throw invalidEx("err.sshPublicKey.cannotChange");
        return super.preUpdate(router);
    }

    @Override public FlexRouter postUpdate(FlexRouter router, Object context) {
        flexRouterService.register(router);
        router.setHost_key(getKnownHostKey());
        return super.postUpdate(router, context);
    }

    @Override public void delete(String uuid) {
        final FlexRouter router = findByUuid(uuid);
        if (router == null) return;
        flexRouterService.unregister(router);
        super.delete(uuid);
    }

    public List<FlexRouter> findEnabledAndRegistered() {
        return list(criteria().add(and(
                eq("enabled", true),
                eq("registered", true),
                gt("port", 1024),
                le("port", 65535),
                isNotNull("token"))));
    }

    public List<FlexRouter> findActive(long maxAge) {
        return list(criteria().add(and(
                eq("active", true),
                eq("initialized", true),
                eq("enabled", true),
                gt("port", 1024),
                le("port", 65535),
                ge("lastSeen", now()-maxAge),
                isNotNull("token"))));
    }

    public FlexRouter findByPort(int port) { return findByUniqueField("port", port); }

    public FlexRouter findByKeyHash(String keyHash) { return findByUniqueField("keyHash", keyHash); }

}
