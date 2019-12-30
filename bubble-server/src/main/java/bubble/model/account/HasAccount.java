package bubble.model.account;

import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.NamedEntity;
import org.cobbzilla.wizard.model.search.SqlViewSearchResult;

public interface HasAccount extends Identifiable, NamedEntity, SqlViewSearchResult {

    String getAccount();
    <E> E setAccount (String account);
    default boolean hasAccount () { return getAccount() != null; }
    String getName();
    default boolean hasName() { return getName() != null; }

}
