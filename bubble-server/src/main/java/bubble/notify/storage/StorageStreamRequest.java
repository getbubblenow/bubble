/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.storage;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import static java.util.UUID.randomUUID;

@NoArgsConstructor @Accessors(chain=true) @ToString
public class StorageStreamRequest {

    @Getter @Setter private String cloud;
    @Getter @Setter private String fromNode;
    @Getter @Setter private String key;
    @Getter @Setter private String token = randomUUID().toString();

}
