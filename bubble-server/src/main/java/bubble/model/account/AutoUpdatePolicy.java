/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable @Accessors(chain=true)
public class AutoUpdatePolicy implements Serializable {

    public static final AutoUpdatePolicy EMPTY_AUTO_UPDATE_POLICY = new AutoUpdatePolicy()
            .setJarUpdates(null)
            .setAppUpdates(null);

    @Getter @Setter private Boolean jarUpdates = true;
    public boolean jarUpdates() { return jarUpdates == null || jarUpdates; }

    @Getter @Setter private Boolean appUpdates = true;
    public boolean appUpdates() { return appUpdates == null || appUpdates; }

}
