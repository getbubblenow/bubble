package bubble.dao.cloud;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.cloud.BubbleNodeKey;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

import static bubble.model.cloud.BubbleNodeKey.defaultExpiration;

@Repository @Slf4j
public class BubbleNodeKeyDAO extends AccountOwnedEntityDAO<BubbleNodeKey> {

    @Override public Order getDefaultSortOrder() { return Order.desc("expiration"); }

    @Override public Object preCreate(BubbleNodeKey key) {
        if (key.getExpiration() == null) key.setExpiration(defaultExpiration());
        return super.preCreate(key);
    }

    public List<BubbleNodeKey> filterValid(List<BubbleNodeKey> tokens) {
        return tokens.stream().filter(BubbleNodeKey::valid).collect(Collectors.toList());
    }

    public BubbleNodeKey filterValid(BubbleNodeKey token) { return token != null && token.valid() ? token : null; }

    public List<BubbleNodeKey> findByNode(String uuid) {
        final List<BubbleNodeKey> tokens = findByField("node", uuid);
        tokens.forEach(t -> { if (!t.valid()) delete(t.getUuid()); });
        return filterValid(tokens);
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
        return filterValid(findByUniqueFields("node", nodeUuid, "uuid", keyUuid));
    }

    public String findRemoteHostForNode(String nodeUuid) {
        // todo: select distinct or limit results to 1
        final List<BubbleNodeKey> keys = findByField("node", nodeUuid);
        return keys.isEmpty() ? null : keys.get(0).getRemoteHost();
    }
}
