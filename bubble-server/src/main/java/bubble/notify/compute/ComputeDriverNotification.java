package bubble.notify.compute;

import bubble.model.cloud.BubbleNode;
import bubble.notify.SynchronousNotification;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class ComputeDriverNotification extends SynchronousNotification {

    @Getter @Setter private BubbleNode node;
    @Getter @Setter private String computeService;

    public ComputeDriverNotification(BubbleNode node) { this.node = node; }

}
