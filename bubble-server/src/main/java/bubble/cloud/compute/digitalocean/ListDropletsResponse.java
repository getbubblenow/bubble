/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.digitalocean;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class ListDropletsResponse {

    @Getter @Setter private Droplet[] droplets;
    public boolean hasDroplets () { return !empty(droplets); }

    @Getter @Setter private JsonNode links;
    @Getter @Setter private JsonNode meta;

}
