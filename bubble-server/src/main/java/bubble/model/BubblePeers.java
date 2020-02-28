/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor @AllArgsConstructor
public class BubblePeers implements Serializable {

    // todo: write ~bubble/peers.json periodically, based on peers tracked in BubbleConfiguration
    // the list is updated when we receive hello_from_sage and peer_hello notifications
    // this triggers cron to open ports appropriately
    public String[] peers;
    public String[] ports;

}
