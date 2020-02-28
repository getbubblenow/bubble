/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service;

import org.cobbzilla.wizard.dao.DAO;

public interface SearchService {

    default void flushCache(DAO dao) {}

}
