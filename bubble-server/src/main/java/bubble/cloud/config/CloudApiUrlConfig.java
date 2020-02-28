/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.NameAndValue;

import java.util.Arrays;
import java.util.List;

@NoArgsConstructor @Accessors(chain=true)
public class CloudApiUrlConfig extends CloudApiConfig {

    @Getter @Setter private String url;
    @Getter @Setter private String file;
    @Getter @Setter private NameAndValue[] headers;

    public List<NameAndValue> headersList() {
        return headers == null ? null : Arrays.asList(headers);
    }

}
