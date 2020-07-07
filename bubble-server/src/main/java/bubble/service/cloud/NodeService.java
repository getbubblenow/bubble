/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.*;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Service @Slf4j
public class NodeService {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    public BubbleNode stopNode(ComputeServiceDriver compute, BubbleNode node) {

        final BubbleDomain domain = domainDAO.findByUuid(node.getDomain());
        final CloudService dns = cloudDAO.findByUuid(domain.getPublicDns());

        node.setState(BubbleNodeState.stopping);
        if (node.hasUuid()) nodeDAO.update(node);
        try {
            dns.getDnsDriver(configuration).deleteNode(node);
            node = compute.stop(node);
            return safeUpdateNodeState(node, BubbleNodeState.stopped);

        } catch (EntityNotFoundException e) {
            log.info("stopNode: node not found by compute service: "+node.id()+": "+e);
            return safeUpdateNodeState(node, BubbleNodeState.unreachable);

        } catch (SimpleViolationException e) {
            log.info("stopNode: error stopping "+node.id()+": "+e);
            throw e;

        } catch (Exception e) {
            log.info("stopNode: error stopping "+node.id());
            return die("stopNode: "+e, e);
        }
    }

    public BubbleNode safeUpdateNodeState(BubbleNode node, BubbleNodeState newState) {
        // ensure node still exists
        final BubbleNode existingNode = nodeDAO.findByUuid(node.getUuid());
        if (existingNode == null) {
            log.warn("stopNode: node not found, not updating: " + node.id());
            return node;
        } else {
            // ensure network still exists
            final BubbleNetwork network = networkDAO.findByUuid(node.getNetwork());
            if (network == null) {
                log.warn("stopNode: node exists (" + node.id() + ") but network (" + node.getNetwork() + ") does not, deleting node");
                nodeDAO.delete(node.getUuid());
                return node;
            }
            return nodeDAO.update(node.setState(newState));
        }
    }

}
