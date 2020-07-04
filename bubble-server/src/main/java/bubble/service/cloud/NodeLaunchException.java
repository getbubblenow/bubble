/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.model.cloud.BubbleNode;
import lombok.Getter;

public class NodeLaunchException extends RuntimeException {

    @Getter private final BubbleNode node;
    public boolean hasNode () { return node != null; }
    public String nodeSummary () { return node == null ? "null" : node.id()+"/"+node.getState(); }

    @Getter private final boolean fatal;

    private NodeLaunchException (BubbleNode node, Exception e, String message, boolean fatal) {
        super(message, e);
        this.node = node;
        this.fatal = fatal;
    }

    private NodeLaunchException (BubbleNode node, Exception e, boolean fatal) {
        this(node, e, e.getMessage(), fatal);
    }

    private NodeLaunchException (BubbleNode node, String message, boolean fatal) {
        this(node, null, message, fatal);
    }

    private NodeLaunchException (Exception e, String message, boolean fatal) {
        this(null, e, message, fatal);
    }

    private NodeLaunchException (String message, boolean fatal) {
        this(null, null, message, fatal);
    }

    private NodeLaunchException (Exception e, boolean fatal) {
        this(null, e, e.getMessage(), fatal);
    }

    public static <T> T fatalLaunchFailure (String message) { throw new NodeLaunchException(message, true); }
    public static <T> T fatalLaunchFailure (Exception e, String message) { throw new NodeLaunchException(e, message, true); }
    public static <T> T fatalLaunchFailure (Exception e) { throw new NodeLaunchException(e, true); }
    public static <T> T fatalLaunchFailure (BubbleNode node, String message) { throw new NodeLaunchException(node, message, true); }
    public static <T> T fatalLaunchFailure (BubbleNode node, Exception e) { throw new NodeLaunchException(node, e, true); }
    public static <T> T fatalLaunchFailure (BubbleNode node, Exception e, String message) { throw new NodeLaunchException(node, e, message, true); }

    public static <T> T launchFailureCanRetry (String message) { throw new NodeLaunchException(message, false); }
    public static <T> T launchFailureCanRetry (Exception e, String message) { throw new NodeLaunchException(e, message, false); }
    public static <T> T launchFailureCanRetry (Exception e) { throw new NodeLaunchException(e, false); }
    public static <T> T launchFailureCanRetry (BubbleNode node, String message) { throw new NodeLaunchException(node, message, false); }
    public static <T> T launchFailureCanRetry (BubbleNode node, Exception e) { throw new NodeLaunchException(node, e, false); }
    public static <T> T launchFailureCanRetry (BubbleNode node, Exception e, String message) { throw new NodeLaunchException(node, e, message, false); }

}
