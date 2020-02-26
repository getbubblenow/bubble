/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.cloud;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public class BubbleDnsName implements Serializable {

    @Getter @Setter private String host;
    @Getter @Setter private String network;
    @Getter @Setter private String domain;

    public BubbleDnsName (String fqdn) {
        final String[] parts = fqdn.split("\\.");
        if (parts.length != 4) throw invalidEx("err.name.invalid");
        host = parts[0];
        network = parts[1];
        domain = parts[2] + "." + parts[3];
    }

}
