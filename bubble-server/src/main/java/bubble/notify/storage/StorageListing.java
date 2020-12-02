/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.storage;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.reflect.OpenApiSchema;

@NoArgsConstructor @Accessors(chain=true) @OpenApiSchema
public class StorageListing {

    @Getter @Setter private String[] keys;
    @Getter @Setter private String listingId;
    @Getter @Setter private boolean truncated;

}
