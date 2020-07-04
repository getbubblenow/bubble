/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud.job;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;

import static bubble.service.cloud.NodeProgressMeterConstants.METER_ERROR_NO_IP;
import static bubble.service.cloud.NodeProgressMeterConstants.METER_ERROR_STARTING_NODE;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

public class NodeStartJob implements Runnable {

    private BubbleNode node;
    private final BubbleNodeDAO nodeDAO;
    private final ComputeServiceDriver computeDriver;

    public NodeStartJob(BubbleNode node,
                        BubbleNodeDAO nodeDAO,
                        ComputeServiceDriver computeDriver) {
        this.node = node;
        this.nodeDAO = nodeDAO;
        this.computeDriver = computeDriver;
    }

    @Override public void run() {
        try {
            node.setState(BubbleNodeState.booting);
            nodeDAO.update(node);
            node = computeDriver.start(node);
            node.setState(BubbleNodeState.booted);
            nodeDAO.update(node);

            if (!node.hasIp4()) {
                throw new NodeJobException(METER_ERROR_NO_IP, "node booted but has no IP");
            }

        } catch (NodeJobException e) {
            throw e;

        } catch (Exception e) {
            throw new NodeJobException(METER_ERROR_STARTING_NODE, "error starting node: "+shortError(e), e);
        }
    }
}
