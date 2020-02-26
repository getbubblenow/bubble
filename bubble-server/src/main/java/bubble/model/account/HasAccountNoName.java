/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Transient;

public interface HasAccountNoName extends HasAccount {

    @Override @JsonIgnore @Transient default String getName() { return getUuid(); }

}
