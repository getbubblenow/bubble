/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.rule.passthru;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class TlsPassthruConfig {

    @Getter @Setter private String[] passthruFqdn;

    @JsonIgnore @Getter(lazy=true) private final Set<String> passthruSet = initPassthruSet();

    private Set<String> initPassthruSet() {
        final Set<String> set = new HashSet<>();
        if (!empty(passthruFqdn)) set.addAll(Arrays.asList(passthruFqdn));
        return set;
    }

    public boolean isPassthru(String fqdn) { return getPassthruSet().contains(fqdn); }

}
