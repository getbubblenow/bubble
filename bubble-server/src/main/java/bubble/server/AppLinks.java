/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class AppLinks extends BasicAppLinks {

    @JsonIgnore @Getter @Setter private Map<String, BasicAppLinks> locale = new HashMap<>();

    public BasicAppLinks forLocale (String loc) {
        final BasicAppLinks appLinks = locale.get(loc);
        return appLinks == null ? this : appLinks;
    }

}
