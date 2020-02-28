/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import bubble.model.cloud.CloudService;
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
            return nodeDAO.update(node.setState(BubbleNodeState.stopped));

        } catch (EntityNotFoundException e) {
            log.info("stopNode: node not found by compute service: "+node.id()+": "+e);
            return nodeDAO.update(node.setState(BubbleNodeState.unreachable));

        } catch (SimpleViolationException e) {
            log.info("stopNode: error stopping "+node.id()+": "+e);
            throw e;

        } catch (Exception e) {
            log.info("stopNode: error stopping "+node.id());
            return die("stopNode: "+e, e);
        }
    }

}
