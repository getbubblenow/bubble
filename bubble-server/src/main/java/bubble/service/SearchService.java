package bubble.service;

import org.cobbzilla.wizard.dao.DAO;

public interface SearchService {

    default void flushCache(DAO dao) {}

}
