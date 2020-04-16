/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Transient;

public interface HasAccountNoName extends HasAccount {

    @Override @JsonIgnore @Transient default String getName() { return getUuid(); }

}
