/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.passthru;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class FlexFqdn implements Comparable<FlexFqdn> {

    public String getId() { return flexFqdn; }
    public void setId(String id) {} // noop

    @Getter @Setter private String flexFqdn;

    public FlexFqdn(String fqdn) { setFlexFqdn(fqdn); }

    @Override public int compareTo(FlexFqdn o) {
        return getFlexFqdn().toLowerCase().compareTo(o.getFlexFqdn().toLowerCase());
    }

}
