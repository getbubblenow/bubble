package bubble.service.cloud;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Service @Slf4j
public class NodeService {

    @Autowired private BubbleNodeDAO nodeDAO;

    public BubbleNode stopNode(ComputeServiceDriver compute, BubbleNode node) {
        node.setState(BubbleNodeState.stopping);
        if (node.hasUuid()) nodeDAO.update(node);
        try {
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
