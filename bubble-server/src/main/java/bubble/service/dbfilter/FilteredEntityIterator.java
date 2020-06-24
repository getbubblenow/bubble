/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.dbfilter;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.account.ReferralCode;
import bubble.model.account.message.AccountMessage;
import bubble.model.bill.*;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeKey;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.model.cloud.notify.SentNotification;
import bubble.model.device.Device;
import bubble.server.BubbleConfiguration;
import edu.emory.mathcs.backport.java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.model.Identifiable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.model.device.Device.newUninitializedDevice;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class FilteredEntityIterator extends EntityIterator {

    private static final List<Class<? extends Identifiable>> POST_COPY_ENTITIES = Arrays.asList(new Class<?>[] {
        BubbleNode.class, BubbleNodeKey.class, Device.class, AccountMessage.class,
        ReferralCode.class, AccountPayment.class, Bill.class, Promotion.class,
        ReceivedNotification.class, SentNotification.class
    });

    private static boolean isPostCopyEntity(Class<? extends Identifiable> clazz) {
        return POST_COPY_ENTITIES.stream().anyMatch(c -> c.isAssignableFrom(clazz));
    }

    private final BubbleConfiguration configuration;
    private final Account account;
    private final BubbleNetwork network;
    private final BubbleNode node;
    private final List<BubblePlanApp> planApps;

    public FilteredEntityIterator(BubbleConfiguration configuration,
                                  Account account,
                                  BubbleNetwork network,
                                  BubbleNode node,
                                  List<BubblePlanApp> planApps,
                                  AtomicReference<Exception> error) {
        super(error);
        this.configuration = configuration;
        this.account = account;
        this.network = network;
        this.node = node;
        this.planApps = planApps;
    }

    @Override protected void iterate() {
        // in the new DB, the admin on this system is NOT an admin,
        // and the new/initial user IS the admin
        if (account.hasParent()) {
            final Account sageAccount = configuration.getBean(AccountDAO.class).findByUuid(account.getParent());
            if (sageAccount == null) die(getClass().getName()+": iterate: parent "+account.getParent()+" not found for account: "+account.getUuid());
            add(Account.sageMask(sageAccount));
        }
        add(account.setAdmin(true));

        configuration.getEntityClasses().forEach(c -> {
            final DAO dao = configuration.getDaoForEntityClass(c);
            if (!AccountOwnedEntityDAO.class.isAssignableFrom(dao.getClass())) {
                log.debug("iterate: skipping entity: " + c.getSimpleName());
            } else if (isPostCopyEntity(c)) {
                log.debug("iterate: skipping " + c.getSimpleName() + ", will copy after other objects are copied");
            } else {
                // copy entities. this is how the re-keying works (decrypt using current spring config,
                // encrypt using new config)
                final AccountOwnedEntityDAO aoDAO = (AccountOwnedEntityDAO) dao;
                final List<? extends HasAccount> entities = aoDAO.dbFilterIncludeAll()
                        ? aoDAO.findAll()
                        : aoDAO.findByAccount(account.getUuid());
                addEntities(false, c, entities, network, node, planApps);
            }
        });

        // in the new DB, the sage node must exist
        final BubbleNode sageNode = configuration.getBean(BubbleNodeDAO.class).findByUuid(node.getSageNode());

        // set valid domain, network, and cloud on the sage node
        add(BubbleNode.sageMask(sageNode)
                .setDomain(node.getDomain())
                .setNetwork(node.getNetwork())
                .setCloud(node.getCloud()));

        // add self-node
        add(node);

        // add an initial device so that algo starts properly the first time
        // name and totp key will be overwritten when the device is initialized for use
        log.info("iterate: creating a single dummy device for algo to start properly");
        final var initDevice = newUninitializedDevice(network.getUuid(), account.getUuid());
        add(configuration.getBean(DeviceDAO.class).create(initDevice));

        // in the new DB, the sage's node key must exist, but not its private key
        final BubbleNodeKey sageKey = configuration.getBean(BubbleNodeKeyDAO.class).findFirstByNode(sageNode.getUuid());
        if (sageKey == null) {
            die("sage has no keys!");
        } else {
            add(BubbleNodeKey.sageMask(sageKey));
        }
    }

}
