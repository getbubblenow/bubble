/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud.notify;

import bubble.model.cloud.BubbleNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;

@NoArgsConstructor @Accessors(chain=true) @ToString
public class NotificationReceipt extends IdentifiableBase {

    @Getter @Setter private boolean success = true;
    @Getter @Setter private BubbleNode resolvedSender;
    @Getter @Setter private BubbleNode resolvedRecipient;

}
