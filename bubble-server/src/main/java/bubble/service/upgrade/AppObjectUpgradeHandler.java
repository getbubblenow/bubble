/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.upgrade;

import bubble.dao.app.AppTemplateEntityDAO;
import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.app.*;
import bubble.server.BubbleConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.entityconfig.IdentifiableBaseParentEntity;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@AllArgsConstructor @Accessors(chain=true) @Slf4j
public class AppObjectUpgradeHandler<T extends AppTemplateEntity> {

    @Getter private final Class<T> appObjectClass;

    public static final AppObjectUpgradeHandler[] APP_UPGRADE_HANDLERS = new AppObjectUpgradeHandler[] {

            new AppObjectUpgradeHandler<>(AppMessage.class),

            new AppObjectUpgradeHandler<>(AppSite.class),

            new AppObjectUpgradeHandler<>(AppRule.class) {
                @Override public AppRule importObject(AppRule sageObject, RuleDriver[] sageDrivers, List<RuleDriver> myDrivers, BubbleApp sageApp, BubbleApp myApp) {

                    final RuleDriver sageDriver = Arrays.stream(sageDrivers).filter(d -> d.getUuid().equals(sageObject.getDriver())).findFirst().orElse(null);
                    if (sageDriver == null)
                        return die("importObject: sageDriver not found for rule " + sageObject.getName() + ": " + sageObject.getDriver());

                    final RuleDriver myDriver = myDrivers.stream().filter(d -> d.getName().equals(sageDriver.getName())).findFirst().orElse(null);
                    if (myDriver == null)
                        return die("importObject: myDriver with name " + sageDriver.getName() + " not found for rule " + sageObject.getName());

                    return sageObject.setDriver(myDriver.getUuid());
                }
            },

            new AppObjectUpgradeHandler<>(AppMatcher.class) {
                @Override public AppMatcher importObject(AppMatcher sageObject, RuleDriver[] sageDrivers, List<RuleDriver> myDrivers, BubbleApp sageApp, BubbleApp myApp) {

                    final List<AppSite> sageSites = sageApp.getChildren(AppSite.class);
                    final AppSite sageSite = sageSites.stream().filter(s -> s.getUuid().equals(sageObject.getSite())).findFirst().orElse(null);
                    if (sageSite == null)
                        return die("importObject: sageSite not found for matcher " + sageObject.getName() + ": " + sageObject.getSite());

                    final List<AppSite> mySites = myApp.getChildren(AppSite.class);
                    final AppSite mySite = mySites.stream().filter(s -> s.getName().equals(sageSite.getName())).findFirst().orElse(null);
                    if (mySite == null)
                        return die("importObject: mySite with name " + sageSite.getName() + " not found for matcher " + sageObject.getName());

                    final List<AppRule> sageRules = sageApp.getChildren(AppRule.class);
                    final AppRule sageRule = sageRules.stream().filter(s -> s.getUuid().equals(sageObject.getRule())).findFirst().orElse(null);
                    if (sageRule == null)
                        return die("importObject: sageRule not found for matcher " + sageObject.getName() + ": " + sageObject.getRule());

                    final List<AppRule> myRules = myApp.getChildren(AppRule.class);
                    final AppRule myRule = myRules.stream().filter(s -> s.getName().equals(sageRule.getName())).findFirst().orElse(null);
                    if (myRule == null)
                        return die("importObject: myRule with name " + sageRule.getName() + " not found for matcher " + sageObject.getName());

                    return sageObject
                            .setSite(mySite.getUuid())
                            .setRule(myRule.getUuid());
                }
            },

            new AppObjectUpgradeHandler<>(AppData.class) {
                @Override public boolean matchSageObject(AppData myObject, AppData sageObject) {
                    return myObject.hasNoDevice() && super.matchSageObject(myObject, sageObject);
                }

                @Override public AppData importObject(AppData sageObject, RuleDriver[] sageDrivers, List<RuleDriver> myDrivers, BubbleApp sageApp, BubbleApp myApp) {

                    final List<AppMatcher> sageMatchers = sageApp.getChildren(AppMatcher.class);
                    final AppMatcher sageMatcher = sageMatchers.stream().filter(s -> s.getUuid().equals(sageObject.getMatcher())).findFirst().orElse(null);
                    if (sageMatcher == null)
                        return die("importObject: sageMatcher not found for matcher " + sageObject.getName() + ": " + sageObject.getMatcher());

                    final List<AppMatcher> myMatchers = myApp.getChildren(AppMatcher.class);
                    final AppMatcher myMatcher = myMatchers.stream().filter(s -> s.getName().equals(sageMatcher.getName())).findFirst().orElse(null);
                    if (myMatcher == null)
                        return die("importObject: myMatcher with name " + sageMatcher.getName() + " not found for matcher " + sageObject.getName());

                    return sageObject
                            .setDevice(null)
                            .setMatcher(myMatcher.getUuid())
                            .setSite(myMatcher.getSite());
                }

                @Override public boolean shouldDelete() { return false; }
            }
    };

    public static final AppObjectUpgradeHandler[] APP_UPGRADE_HANDLERS_REVERSED = ArrayUtil.copyAndReverse(APP_UPGRADE_HANDLERS, AppObjectUpgradeHandler.class);

    static {
        for (AppObjectUpgradeHandler handler : APP_UPGRADE_HANDLERS) {
            IdentifiableBaseParentEntity.addChildClass(handler.getAppObjectClass());
        }
    }

    public static String appString(BubbleApp app) {
        final StringBuilder b = new StringBuilder();
        if (app.hasChildren()) {
            for (AppObjectUpgradeHandler handler : APP_UPGRADE_HANDLERS) {
                if (b.length() > 0) b.append(",");
                final Class<? extends AppTemplateEntity> clazz = handler.getAppObjectClass();
                final List<? extends AppTemplateEntity> children = app.getChildren(clazz);
                if (!empty(children)) {
                    b.append(clazz.getSimpleName()).append("={")
                            .append(children.stream()
                                    .map(HasAccount::getName)
                                    .collect(Collectors.joining(",")))
                            .append("}");
                }
            }
        }
        return app.getName() + "{" + b.toString() + "}";
    }

    public List<T> findSageObjects(BubbleConfiguration configuration, Account admin, BubbleApp app) {
        return ((AppTemplateEntityDAO<T>) configuration.getDaoForEntityClass(appObjectClass))
                .findPublicTemplatesByApp(admin.getUuid(), app.getUuid());
    }

    public void updateAppObjects(BubbleConfiguration configuration,
                                 Account account,
                                 BubbleApp myApp,
                                 BubbleApp sageApp,
                                 RuleDriver[] sageDrivers,
                                 List<RuleDriver> myDrivers) {

        final AppTemplateEntityDAO<T> dao = (AppTemplateEntityDAO<T>) configuration.getDaoForEntityClass(appObjectClass);
        final List<T> myAppObjects = dao.findByAccountAndApp(account.getUuid(), myApp.getUuid());
        final List<T> sageAppObjects = sageApp.getChildren(appObjectClass);

        final String className = appObjectClass.getSimpleName();
        if (!empty(sageAppObjects)) {
            for (final T sageObject : sageAppObjects) {
                T myAppObject = myAppObjects.stream()
                        .filter(o -> matchSageObject(o, sageObject))
                        .findFirst()
                        .orElse(null);

                final T sageCopy = copy(sageObject);
                if (myAppObject == null) {
                    sageCopy.setAccount(account.getUuid());
                    sageCopy.setApp(myApp.getUuid());
                    log.info("updateAppObjects: creating " + className + ": " + sageCopy.getName());
                    final T toCreate = importObject(sageCopy, sageDrivers, myDrivers, sageApp, myApp);
                    log.info("updateAppObject: calling dao.create with object="+json(toCreate));
                    myAppObject = dao.create(toCreate);
                } else {
                    // preserve existing "enabled" flag
                    boolean enabled = myAppObject.enabled();
                    myAppObject.upgrade(sageCopy, configuration);
                    myAppObject.setEnabled(enabled);
                    log.info("updateAppObjects: updating " + className + ": " + sageCopy.getName());
                    myAppObject = dao.update(myAppObject);
                }
                myApp.addChild(appObjectClass, myAppObject);
            }
        }
    }

    public void removeAppObjects(BubbleConfiguration configuration, Account account, BubbleApp myApp, BubbleApp sageApp, RuleDriver[] sageDrivers, List<RuleDriver> ruleDrivers) {
        final AppTemplateEntityDAO<T> dao = (AppTemplateEntityDAO<T>) configuration.getDaoForEntityClass(appObjectClass);
        final List<T> myAppObjects = dao.findByAccountAndApp(account.getUuid(), myApp.getUuid());
        final List<T> sageAppObjects = sageApp.getChildren(appObjectClass);
        for (T obj : myAppObjects) {
            if (sageAppObjects.stream().noneMatch(o -> matchSageObject(obj, o))) {
                log.info("updateAppObjects: deleting "+obj.getClass().getSimpleName()+": "+obj.getName());
                dao.delete(obj.getUuid());
            }
        }
    }

    public boolean matchSageObject(T myObject, T sageObject) {
        return myObject != null && sageObject != null && myObject.getName().equals(sageObject.getName());
    }

    public T importObject(T sageObject,
                          RuleDriver[] sageDrivers,
                          List<RuleDriver> myDrivers,
                          BubbleApp sageApp,
                          BubbleApp myApp) {
        return sageObject;
    }

    public boolean shouldDelete() { return true; }

}
