/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.storage.s3;

import com.amazonaws.regions.Regions;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class S3StorageConfig {

    @Getter @Setter private Regions region = Regions.DEFAULT_REGION;
    @Getter @Setter private String bucket;
    @Getter @Setter private String prefix;
    @Getter @Setter private int listFetchSize = 100;

}
