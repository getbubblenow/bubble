/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account.message;

import org.cobbzilla.util.collection.NameAndValue;

public interface AccountMessageCompletionHandler {

    default boolean validate(AccountMessage message, NameAndValue[] data) { return true; }
    default void confirm(AccountMessage message, NameAndValue[] data) {}
    default void deny(AccountMessage message) {}

}
