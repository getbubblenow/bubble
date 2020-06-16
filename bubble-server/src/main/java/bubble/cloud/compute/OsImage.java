/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class OsImage {

    @Getter @Setter private String id;
    @Getter @Setter private String name;
    @Getter @Setter private String region;

}
