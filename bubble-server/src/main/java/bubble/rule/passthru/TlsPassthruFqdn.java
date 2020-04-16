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
public class TlsPassthruFqdn implements Comparable<TlsPassthruFqdn> {

    public String getId() { return passthruFqdn; }
    public void setId(String id) {} // noop

    @Getter @Setter private String passthruFqdn;

    public TlsPassthruFqdn(String fqdn) { setPassthruFqdn(fqdn); }

    @Override public int compareTo(TlsPassthruFqdn o) {
        return getPassthruFqdn().toLowerCase().compareTo(o.getPassthruFqdn().toLowerCase());
    }
}
