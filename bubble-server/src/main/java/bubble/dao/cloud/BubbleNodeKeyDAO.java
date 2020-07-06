/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.cloud;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeKey;
import bubble.service.boot.SelfNodeService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

import static bubble.model.cloud.BubbleNodeKey.defaultExpiration;
import static org.cobbzilla.wizard.model.Identifiable.UUID;

@Repository @Slf4j
public class BubbleNodeKeyDAO extends AccountOwnedEntityDAO<BubbleNodeKey> {

    public static final Order EXPIRATION_DESC = Order.desc("expiration");

    @Autowired private SelfNodeService selfNodeService;

    @Override public Order getDefaultSortOrder() { return EXPIRATION_DESC; }
    @Override protected int getFinderMaxResults() { return Integer.MAX_VALUE; }

    @Override public Object preCreate(BubbleNodeKey key) {
        if (key.getExpiration() == null) key.setExpiration(defaultExpiration());
        return super.preCreate(key);
    }

    public List<BubbleNodeKey> filterValid(List<BubbleNodeKey> tokens) {
        return tokens.stream().filter(BubbleNodeKey::valid).collect(Collectors.toList());
    }

    public BubbleNodeKey filterValid(BubbleNodeKey token) { return token != null && token.valid() ? token : null; }

    public List<BubbleNodeKey> findByNode(String uuid) {
        final List<BubbleNodeKey> keys = findByField("node", uuid);
        keys.forEach(t -> { if (!t.valid()) delete(t.getUuid()); });
        final List<BubbleNodeKey> validKeys = filterValid(keys);

        if (validKeys.isEmpty()) {
            final BubbleNode thisNode = selfNodeService.getThisNode();
            if (thisNode != null && thisNode.getUuid().equals(uuid)) {
                // we just deleted the last key for ourselves. create a new one.
                validKeys.add(create(new BubbleNodeKey(thisNode)));
            }
        }

        return validKeys;
    }

    @Override public BubbleNodeKey findByUuid(String uuid) { return filterValid(super.findByUuid(uuid)); }

    public BubbleNodeKey findByPublicKeyHash(String hash) { return filterValid(super.findByUniqueField("publicKeyHash", hash)); }

    public BubbleNodeKey findFirstByNode(String uuid) {
        // todo: limit results to 1, this is very inefficient if there are a large number of keys for a node
        final List<BubbleNodeKey> tokens = findByNode(uuid);
        if (tokens.isEmpty()) {
            log.warn("findFirstByNode: no tokens for node: "+uuid);
            return null;
        }
        return tokens.get(0);
    }

    public BubbleNodeKey findByNodeAndUuid(String nodeUuid, String keyUuid) {
        return filterValid(findByUniqueFields("node", nodeUuid, UUID, keyUuid));
    }

    public String findRemoteHostForNode(String nodeUuid) {
        // todo: select distinct or limit results to 1
        final List<BubbleNodeKey> keys = findByField("node", nodeUuid);
        return keys.isEmpty() ? null : keys.get(0).getRemoteHost();
    }
}
