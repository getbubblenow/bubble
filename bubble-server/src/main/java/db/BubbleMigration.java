/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package db;

import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.spring.config.rdbms.RdbmsConfig;
import org.flywaydb.core.api.migration.JavaMigration;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;

@Slf4j
public abstract class BubbleMigration implements JavaMigration {

    @Getter @Setter private static BubbleConfiguration configuration;

    public RdbmsConfig getRdbmsConfig () {
        final RdbmsConfig config = new RdbmsConfig();
        config.setConfiguration(getConfiguration());
        return config;
    }

    protected HibernatePBEStringEncryptor stringEncryptor() { return getRdbmsConfig().hibernateEncryptor(); }
    protected HibernatePBEStringEncryptor longEncryptor  () { return getRdbmsConfig().hibernateLongEncryptor(); }

    @Override public String getDescription() {
        return getClass().getName()+": checksum: "+getChecksum();
    }

    @Override public boolean isUndo() { return false; }

    @Override public boolean canExecuteInTransaction() { return true; }

}
