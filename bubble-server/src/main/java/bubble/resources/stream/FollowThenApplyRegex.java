/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import lombok.Getter;
import lombok.Setter;

public class FollowThenApplyRegex {

    @Getter @Setter private String url;
    @Getter @Setter private String regex;
    @Getter @Setter private Integer[] groups;

}
