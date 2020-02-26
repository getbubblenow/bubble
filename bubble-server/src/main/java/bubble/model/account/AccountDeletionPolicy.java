/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AccountDeletionPolicy {

    block_delete, full_delete;

    @JsonCreator public static AccountDeletionPolicy fromString (String v) { return enumFromString(AccountDeletionPolicy.class, v); }

}
