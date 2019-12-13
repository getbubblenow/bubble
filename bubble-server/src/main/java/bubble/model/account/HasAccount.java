package bubble.model.account;

import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.NamedEntity;

public interface HasAccount extends Identifiable, NamedEntity {

    String getAccount();
    <E> E setAccount (String account);
    default boolean hasAccount () { return getAccount() != null; }
    String getName();

}
