/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import bubble.model.cloud.BubbleNode;
import lombok.Getter;

public class UnavailableComputeLocationException extends RuntimeException {

    @Getter private final BubbleNode node;

    public UnavailableComputeLocationException(BubbleNode node, String message) {
        super(message);
        this.node = node;
    }
}
