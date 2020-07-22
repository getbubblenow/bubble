/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package db;

import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.spring.config.rdbms.RdbmsConfig;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;

/**
 * Place SQL migrations in the 'db' package.
 *
 * Names should be in the form: VyyyyMMddnn__operation_description.sql (or .java for Java migrations)
 *
 *   yyyy        = 4 digit year
 *   MM          = 2 digit month, 01=January
 *   dd          = d digit day of month
 *   nn          = start with 01 and increment upwards if there are multiple migrations on the same day
 *   operation   = 'add' (adding columns), 'drop' (dropping columns), 'update' (multiple adds/drops and/or renames)
 *   description = a helpful description of the migration, using underscores for spaces
 *
 * For example: V2020042301__add_account_payment_archived.sql
 */
@Slf4j
public abstract class BubbleMigration extends BaseJavaMigration {

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
