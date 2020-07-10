/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;

import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

@Service @Slf4j
public class NodeService {

    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    public void stopNode(ComputeServiceDriver compute, BubbleNode node) {
        log.info("stopNode: starting for node "+node.id());

        final BubbleDomain domain = domainDAO.findByUuid(node.getDomain());
        final CloudService dns = cloudDAO.findByUuid(domain.getPublicDns());
        try {
            log.debug("stopNode: deleting dns entries for node: "+node.id());
            dns.getDnsDriver(configuration).deleteNode(node);
            log.debug("stopNode: stopping instance for node: "+node.id());
            node = compute.stop(node);
            log.debug("stopNode: node stopped: "+node.id());

        } catch (EntityNotFoundException e) {
            log.warn("stopNode: node not found by compute service: "+node.id()+": "+e);

        } catch (SimpleViolationException e) {
            log.warn("stopNode: error stopping "+node.id()+": "+e);
            throw e;

        } catch (Exception e) {
            log.warn("stopNode: error stopping "+node.id()+": "+shortError(e));
        }
    }

}
