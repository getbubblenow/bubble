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
