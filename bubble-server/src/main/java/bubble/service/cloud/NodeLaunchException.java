/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.model.cloud.BubbleNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

@Slf4j
public class NodeLaunchException extends RuntimeException {

    public enum NodeLaunchExceptionType { fatal, canRetry, interrupted, unavailableRegion }

    @Getter private final BubbleNode node;
    public boolean hasNode () { return node != null; }
    public String nodeSummary () { return node == null ? "null" : node.id()+"/"+node.getState(); }

    @Getter private final NodeLaunchExceptionType type;

    private NodeLaunchException (BubbleNode node, Exception e, String message, NodeLaunchExceptionType type) {
        super(message, e);
        this.node = node;
        this.type = type;
        if (log.isTraceEnabled()) log.trace("NodeLaunchException created: "+this);
    }

    @Override public String toString() {
        return "{NodeLaunchException type="+type+", message="+getMessage()+", cause="+(getCause() == null ? "null" : shortError(getCause()))+"}";
    }

    private NodeLaunchException (BubbleNode node, Exception e, NodeLaunchExceptionType type) {
        this(node, e, e.getMessage(), type);
    }

    private NodeLaunchException (BubbleNode node, String message, NodeLaunchExceptionType type) {
        this(node, null, message, type);
    }

    private NodeLaunchException (Exception e, String message, NodeLaunchExceptionType type) {
        this(null, e, message, type);
    }

    private NodeLaunchException (String message, NodeLaunchExceptionType type) {
        this(null, null, message, type);
    }

    private NodeLaunchException (Exception e, NodeLaunchExceptionType type) {
        this(null, e, e.getMessage(), type);
    }

    public static <T> T fatalLaunchFailure (String message) { throw new NodeLaunchException(message, NodeLaunchExceptionType.fatal); }
    public static <T> T fatalLaunchFailure (Exception e, String message) { throw new NodeLaunchException(e, message, NodeLaunchExceptionType.fatal); }
    public static <T> T fatalLaunchFailure (Exception e) { throw new NodeLaunchException(e, NodeLaunchExceptionType.fatal); }
    public static <T> T fatalLaunchFailure (BubbleNode node, String message) { throw new NodeLaunchException(node, message, NodeLaunchExceptionType.fatal); }
    public static <T> T fatalLaunchFailure (BubbleNode node, Exception e) { throw new NodeLaunchException(node, e, NodeLaunchExceptionType.fatal); }
    public static <T> T fatalLaunchFailure (BubbleNode node, Exception e, String message) { throw new NodeLaunchException(node, e, message, NodeLaunchExceptionType.fatal); }

    public static <T> T launchFailureCanRetry (String message) { throw new NodeLaunchException(message, NodeLaunchExceptionType.canRetry); }
    public static <T> T launchFailureCanRetry (Exception e, String message) { throw new NodeLaunchException(e, message, NodeLaunchExceptionType.canRetry); }
    public static <T> T launchFailureCanRetry (Exception e) { throw new NodeLaunchException(e, NodeLaunchExceptionType.canRetry); }
    public static <T> T launchFailureCanRetry (BubbleNode node, String message) { throw new NodeLaunchException(node, message, NodeLaunchExceptionType.canRetry); }
    public static <T> T launchFailureCanRetry (BubbleNode node, Exception e) { throw new NodeLaunchException(node, e, NodeLaunchExceptionType.canRetry); }
    public static <T> T launchFailureCanRetry (BubbleNode node, Exception e, String message) { throw new NodeLaunchException(node, e, message, NodeLaunchExceptionType.canRetry); }

    public static <T> T launchInterrupted (String message) { throw new NodeLaunchException(message, NodeLaunchExceptionType.interrupted); }
    public static <T> T launchInterrupted (Exception e, String message) { throw new NodeLaunchException(e, message, NodeLaunchExceptionType.interrupted); }
    public static <T> T launchInterrupted (Exception e) { throw new NodeLaunchException(e, NodeLaunchExceptionType.interrupted); }
    public static <T> T launchInterrupted (BubbleNode node, String message) { throw new NodeLaunchException(node, message, NodeLaunchExceptionType.interrupted); }
    public static <T> T launchInterrupted (BubbleNode node, Exception e) { throw new NodeLaunchException(node, e, NodeLaunchExceptionType.interrupted); }
    public static <T> T launchInterrupted (BubbleNode node, Exception e, String message) { throw new NodeLaunchException(node, e, message, NodeLaunchExceptionType.interrupted); }

    public static <T> T unavailableRegion (String message) { throw new NodeLaunchException(message, NodeLaunchExceptionType.unavailableRegion); }
    public static <T> T unavailableRegion (Exception e, String message) { throw new NodeLaunchException(e, message, NodeLaunchExceptionType.unavailableRegion); }
    public static <T> T unavailableRegion (Exception e) { throw new NodeLaunchException(e, NodeLaunchExceptionType.unavailableRegion); }
    public static <T> T unavailableRegion (BubbleNode node, String message) { throw new NodeLaunchException(node, message, NodeLaunchExceptionType.unavailableRegion); }
    public static <T> T unavailableRegion (BubbleNode node, Exception e) { throw new NodeLaunchException(node, e, NodeLaunchExceptionType.unavailableRegion); }
    public static <T> T unavailableRegion (BubbleNode node, Exception e, String message) { throw new NodeLaunchException(node, e, message, NodeLaunchExceptionType.unavailableRegion); }

}
