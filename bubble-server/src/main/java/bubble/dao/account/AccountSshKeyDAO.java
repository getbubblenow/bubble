/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account;

import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.File;

import static bubble.ApiConstants.HOME_DIR;
import static org.cobbzilla.util.io.FileUtil.touch;
import static org.cobbzilla.util.security.RsaKeyPair.isValidSshPublicKey;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class AccountSshKeyDAO extends AccountOwnedEntityDAO<AccountSshKey> {

    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleConfiguration configuration;

    public AccountSshKey findByAccountAndHash(String accountUuid, String hash) {
        return findByUniqueFields("account", accountUuid, "sshPublicKeyHash", hash);
    }

    public AccountSshKey findByHash(String hash) {
        return findByUniqueField("sshPublicKeyHash", hash);
    }

    @Override public Object preCreate(AccountSshKey key) {

        if (!key.hasSshPublicKey()) throw invalidEx("err.sshPublicKey.required");
        key.setSshPublicKey(key.getSshPublicKey().trim()); // trim leading/trailing whitespace from key

        if (!key.getSshPublicKey().startsWith("ssh-rsa ")) throw invalidEx("err.sshPublicKey.notRSA");
        if (!isValidSshPublicKey(key.getSshPublicKey())) throw invalidEx("err.sshPublicKey.invalidRSA");

        if (key.hasExpiration() && key.expired()) throw invalidEx("err.expiration.cannotCreateSshKeyAlreadyExpired");

        final Account owner = accountDAO.findByUuid(key.getAccount());
        final BubbleNetwork thisNetwork = configuration.getThisNetwork();
        if (thisNetwork == null || thisNetwork.getInstallType() != AnsibleInstallType.sage) {
            // never allow installation of a key on sage. must be manually set in the database.
            key.setInstallSshKey(false);

        } else {
            // admin keys are always installed on a node
            // never install key for non-admin
            key.setInstallSshKey(owner.admin() && thisNetwork.getInstallType() == AnsibleInstallType.node);
        }

        final String hash = sha256_hex(key.getSshPublicKey());
        if (!key.hasSshPublicKeyHash() || !key.getSshPublicKeyHash().equals(hash)) {
            log.warn("preCreate: hash was empty or did not match, setting correctly for AccountSshKey: "+key.getUuid()+"/"+key.getName());
            key.setSshPublicKeyHash(hash);
        }

        final AccountSshKey byHash = findByHash(key.getSshPublicKeyHash());
        if (byHash != null) throw invalidEx("err.sshPublicKey.alreadyExists");

        final AccountSshKey byName = findByAccountAndId(key.getAccount(), key.getName());
        if (byName != null) throw invalidEx("err.sshPublicKey.alreadyExists");

        return super.preCreate(key);
    }

    @Override public AccountSshKey postCreate(AccountSshKey key, Object context) {
        if (key.installSshKey()) refreshInstalledKeys();
        return super.postCreate(key, context);
    }

    @Override public AccountSshKey postUpdate(AccountSshKey key, Object context) {
        final Account owner = accountDAO.findByUuid(key.getAccount());
        if (owner.admin()) refreshInstalledKeys();
        return super.postUpdate(key, context);
    }

    @Override public void delete(String uuid) {
        final AccountSshKey key = findByUuid(uuid);
        super.delete(uuid);
        if (key.installSshKey()) refreshInstalledKeys();
    }

    // refresh_bubble_ssh_keys_monitor.sh watches this file (in ansible bubble role)
    private static final File REFRESH_SSH_KEYS_FILE = new File(HOME_DIR, ".refresh_ssh_keys");
    public void refreshInstalledKeys() { touch(REFRESH_SSH_KEYS_FILE); }

}
