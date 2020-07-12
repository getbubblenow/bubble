/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud.job;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.cloud.compute.UnavailableComputeLocationException;
import bubble.model.cloud.BubbleNode;
import lombok.extern.slf4j.Slf4j;

import static bubble.service.cloud.NodeProgressMeterConstants.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

@Slf4j
public class NodeStartJob implements Runnable {

    private BubbleNode node;
    private final ComputeServiceDriver computeDriver;

    public NodeStartJob(BubbleNode node,
                        ComputeServiceDriver computeDriver) {
        this.node = node;
        this.computeDriver = computeDriver;
    }

    @Override public void run() {
        try {
            log.debug("run: calling computeDriver.start("+node.id()+")");
            node.setLaunchException(null);
            node = computeDriver.start(node);
            log.debug("run: computeDriver.start("+node.id()+") returned successfully");

            if (!node.hasIp4()) {
                node.setLaunchException(new IllegalStateException("node booted but has no IP"));
                throw new NodeJobException(METER_ERROR_NO_IP, "node booted but has no IP");
            }

        } catch (NodeJobException e) {
            log.debug("run: computeDriver.start("+node.id()+") returning with launchError(NodeJobException): "+shortError(e));
            node.setLaunchException(e);
            throw e;

        } catch (UnavailableComputeLocationException e) {
            log.debug("run: computeDriver.start("+node.id()+") returning with launchError(UnavailableComputeLocationException): "+shortError(e));
            node.setLaunchException(e);
            throw new NodeJobException(METER_ERROR_UNAVAILABLE_LOCATION, "node cannot be launched at this location", e);

        } catch (RuntimeException e) {
            log.debug("run: computeDriver.start("+node.id()+") returning with launchError(RuntimeException): "+shortError(e));
            node.setLaunchException(e);
            throw new NodeJobException(METER_ERROR_STARTING_NODE, "error starting node: "+shortError(e), e);

        } catch (Exception e) {
            log.debug("run: computeDriver.start("+node.id()+") returning with launchError(Exception): "+shortError(e));
            node.setLaunchException(new IllegalStateException(shortError(e), e));
            throw new NodeJobException(METER_ERROR_STARTING_NODE, "error starting node: "+shortError(e), e);
        }
    }
}
