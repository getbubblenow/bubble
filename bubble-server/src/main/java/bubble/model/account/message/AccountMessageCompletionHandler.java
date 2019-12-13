package bubble.model.account.message;

import org.cobbzilla.util.collection.NameAndValue;

public interface AccountMessageCompletionHandler {

    default boolean validate(AccountMessage message, NameAndValue[] data) { return true; }
    default void confirm(AccountMessage message, NameAndValue[] data) {}
    default void deny(AccountMessage message) {}

}
