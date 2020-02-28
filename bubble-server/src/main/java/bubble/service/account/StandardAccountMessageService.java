/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.account;

import bubble.cloud.auth.AuthenticationDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountMessageApprovalStatus;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.*;
import bubble.model.account.message.handlers.*;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.cobbzilla.util.collection.HasPriority.priorityDesc;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.ValidationRegexes.NUMERIC_PATTERN;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Service @Slf4j
public class StandardAccountMessageService implements AccountMessageService {

    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override public boolean send(AccountMessage message) {
        final String accountUuid = message.getAccount();
        final Account account = accountDAO.findByUuid(accountUuid);
        AccountPolicy policy = policyDAO.findSingleByAccount(accountUuid);
        if (policy == null) {
            policy = policyDAO.create(new AccountPolicy().setAccount(accountUuid));
        }
        final List<AccountContact> contacts = policy.getAllowedContacts(message);
        if (contacts.isEmpty()) {
            log.warn("send("+message+"): no contacts to send to");
            return false;
        }
        return send(account, message, contacts);
    }

    private boolean send(Account account, AccountMessage message, List<AccountContact> contacts) {
        boolean allOk = true;
        for (AccountContact contact : contacts) {
            if (!send(account, message, contact)) allOk = false;
        }
        return allOk;
    }

    private boolean send(Account account, AccountMessage message, AccountContact contact) {
        final String prefix = "send(" + account.getUuid() + ", " + json(message) + " -> " + contact + ": ";
        final List<CloudService> clouds = cloudDAO.findByAccountAndType(account.getUuid(), contact.getType());
        if (clouds.isEmpty()) {
            log.error(prefix+"no clouds of type " + contact.getType() + " found");
            return false;
        }
        final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
        for (CloudService cloud : priorityDesc(clouds)) {
            final AuthenticationDriver driver = cloud.getAuthenticationDriver(configuration);
            if (shouldSend(message, contact)) {
                if (message.getMessageType().hasRequest()) {
                    message.setRequest(messageDAO.findOperationRequest(message));
                    message.setRequestContact(policy.findContactByUuid(message.getRequest().getContact()));
                } else {
                    message.setRequestContact(policy.findContactByUuid(message.getContact()));
                }
                if (driver.send(account, message, contact)) {
                    log.info(prefix + "send succeeded with cloud: " + cloud.getName());
                    return true;
                }
            }
        }
        log.error(prefix+"send failed with all clouds");
        return false;
    }

    private boolean shouldSend(AccountMessage message, AccountContact contact) {
        return message.getMessageType().getDirection() == AccountMessageDirection.to_account || !message.isSameContact(contact.getUuid());
    }

    @Autowired private AccountMessageDAO messageDAO;
    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService confirmationTokens = redis.prefixNamespace(getClass().getSimpleName()+".tokens");

    public String confirmationToken(AccountPolicy policy, AccountMessage message, AccountContact contact) {
        final AccountMessageContact amc = accountMessageContact(message, contact);
        final String key = amc.key();
        String token = getConfirmationTokens().get(key);
        if (token == null) {
            token = randomNumeric(6);
            final long tokenTimeout = message.tokenTimeoutSeconds(policy);
            if (tokenTimeout == -1) return null;
            getConfirmationTokens().set(key, token, EX, tokenTimeout);
            getConfirmationTokens().set(token, json(amc), EX, tokenTimeout);
            log.debug("confirmationToken: action="+message.getAction()+", token="+token+", key="+key);
        }
        return token;
    }

    public AccountMessageContact accountMessageContact(AccountMessage message, AccountContact contact) {
        return new AccountMessageContact(message, contact);
    }

    public AccountMessage approve(Account account, String remoteHost, String token) {
        return approve(account, remoteHost, token, null);
    }

    public AccountMessage approve(Account account, String remoteHost, String token, NameAndValue[] data) {
        final AccountMessage approval = captureResponse(account, remoteHost, token, AccountMessageType.approval, data);
        if (approval == null) {
            return null;
        }
        if (account == null) account = accountDAO.findByUuid(approval.getAccount());
        final AccountMessageApprovalStatus approvalStatus = messageDAO.requestApproved(account, approval, token, data);
        if (approvalStatus == AccountMessageApprovalStatus.ok_confirmed) {
            final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
            final AccountMessage confirm = messageDAO.create(new AccountMessage(approval).setMessageType(AccountMessageType.confirmation));
            approval.setRequest(messageDAO.findOperationRequest(approval));
            approval.setRequestContact(policy.findContactByUuid(approval.getRequest().getContact()));
            getCompletionHandler(approval).confirm(approval, data);

            if (approval.hasConfirmationTokensToRemove()) {
                final RedisService tokens = getConfirmationTokens();
                for (String toRemove : approval.getConfirmationTokensToRemove()) tokens.del(toRemove);
            }
            return confirm;

        } else if (approvalStatus.ok()) {
            if (approval.hasConfirmationTokensToRemove()) {
                final RedisService tokens = getConfirmationTokens();
                for (String toRemove : approval.getConfirmationTokensToRemove()) tokens.del(toRemove);
            }
            return approval;
        }
        throw invalidEx("err.approvalToken.invalid", "Approval cannot proceed: "+approvalStatus, approvalStatus.name());
    }

    @Getter(lazy=true) private final AccountMessageCompletionHandler accountLoginHandler = configuration.autowire(new AccountLoginHandler());
    @Getter(lazy=true) private final AccountMessageCompletionHandler accountPasswordHandler = configuration.autowire(new AccountPasswordHandler());
    @Getter(lazy=true) private final AccountMessageCompletionHandler accountVerifyHandler = configuration.autowire(new AccountVerifyHandler());
    @Getter(lazy=true) private final AccountMessageCompletionHandler accountDeleteHandler = configuration.autowire(new AccountDeletionHandler());
    @Getter(lazy=true) private final AccountMessageCompletionHandler accountDownloadHandler = configuration.autowire(new AccountDownloadHandler());
    @Getter(lazy=true) private final AccountMessageCompletionHandler networkPasswordHandler = configuration.autowire(new NetworkPasswordHandler());
    @Getter(lazy=true) private final AccountMessageCompletionHandler networkStartHandler = configuration.autowire(new NetworkStartHandler());
    @Getter(lazy=true) private final AccountMessageCompletionHandler networkStopHandler = configuration.autowire(new NetworkStopHandler());

    @Getter(lazy=true) private final Map<String, AccountMessageCompletionHandler> confirmationHandlers = initConfirmationHandlers();
    private HashMap<String, AccountMessageCompletionHandler> initConfirmationHandlers() {
        final HashMap<String, AccountMessageCompletionHandler> handlers = new HashMap<>();
        handlers.put(ActionTarget.account+":"+AccountAction.login, getAccountLoginHandler());
        handlers.put(ActionTarget.account+":"+AccountAction.password, getAccountPasswordHandler());
        handlers.put(ActionTarget.account+":"+AccountAction.verify, getAccountVerifyHandler());
        handlers.put(ActionTarget.account+":"+AccountAction.delete, getAccountDeleteHandler());
        handlers.put(ActionTarget.account+":"+AccountAction.download, getAccountDownloadHandler());
        handlers.put(ActionTarget.network+":"+AccountAction.password, getNetworkPasswordHandler());
        handlers.put(ActionTarget.network+":"+AccountAction.start, getNetworkStartHandler());
        handlers.put(ActionTarget.network+":"+AccountAction.stop, getNetworkStopHandler());
        return handlers;
    }

    private AccountMessageCompletionHandler getCompletionHandler(AccountMessage message) {
        final AccountMessageCompletionHandler handler = getConfirmationHandlers().get(message.getTarget()+":"+message.getAction());
        return handler != null ? handler : die("getCompletionHandler: no handler found for target/action: "+message.getTarget()+"/"+message.getAction());
    }

    public AccountMessage deny(Account account, String remoteHost, String token) {

        final AccountMessage denial = captureResponse(account, remoteHost, token, AccountMessageType.denial);

        // has it already been denied?
        final List<AccountMessage> denials = messageDAO.findOperationDenials(denial);
        if (!denials.isEmpty()) {
            final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
            if (denials.size() == 1 && denials.get(0).getUuid().equals(denial.getUuid())) {
                denial.setRequest(messageDAO.findOperationRequest(denial));
                denial.setRequestContact(policy.findContactByUuid(denial.getRequest().getContact()));
                getCompletionHandler(denial).deny(denial);
            }
            return denials.get(0);
        }
        return denial;
    }

    public AccountMessage captureResponse(Account account,
                                          String remoteHost,
                                          String token,
                                          AccountMessageType type) {
        return captureResponse(account, remoteHost, token, type, null);
    }

    @Override public AccountMessage captureResponse(Account account,
                                                    String remoteHost,
                                                    String token,
                                                    AccountMessageType type,
                                                    NameAndValue[] data) {

        if (empty(remoteHost)) {
            throw invalidEx("err.remoteHost.required", "remoteHost was empty");
        }

        final RedisService tokens = getConfirmationTokens();

        String json = tokens.get(token);
        if (json == null) {
            log.warn("captureResponse("+type+"): regular token not found: "+token);
            throw invalidEx("err.approvalToken.invalid");
        }

        if (NUMERIC_PATTERN.matcher(json).matches()) {
            // we were passed the amc key, use it to lookup the amc
            final String numericToken = json;
            json = tokens.get(numericToken);
            if (json == null) {
                log.warn("captureResponse("+type+"): numeric token not found: "+numericToken+" (token="+token+")");
                throw invalidEx("err.approvalToken.invalid");
            }
        }

        final AccountMessageContact amc = json(json, AccountMessageContact.class);
        if (account != null && !amc.getMessage().getAccount().equals(account.getUuid())) return null;

        final AccountMessage toCreate = new AccountMessage()
                .setRemoteHost(remoteHost)
                .setAccount(amc.getMessage().getAccount())
                .setNetwork(amc.getMessage().getNetwork())
                .setContact(amc.getContact().getUuid())
                .setRequestId(amc.getMessage().getRequestId())
                .setName(amc.getMessage().getName())
                .setMessageType(type)
                .setAction(amc.getMessage().getAction())
                .setTarget(amc.getMessage().getTarget());

        if (!getCompletionHandler(toCreate).validate(toCreate, data)) {
            throw invalidEx("err.approvalToken.invalid", type+" request was invalid", json(data));
        }

        final AccountMessage message = messageDAO.create(toCreate);
        message.setConfirmationTokensToRemove(token, amc.key());

        log.debug("captureResponse("+type+"): removing tokens: "+token+" and "+amc.key());

        return message;
    }

    public Account determineRemainingApprovals(AccountMessage approval) {
        final List<AccountMessage> approvals = messageDAO.findOperationApprovals(approval);
        final AccountPolicy policy = policyDAO.findSingleByAccount(approval.getAccount());
        final List<AccountContact> remainingApprovals = policy.getRequiredApprovals(approval.getRequest()).stream()
                .filter(c -> approvals.stream().noneMatch(a -> a.isSameContact(c.getUuid())))
                .collect(Collectors.toList());

        // return masked list of contacts remaining to approve
        log.info("determineRemainingApprovals: remaining="+StringUtil.toString(remainingApprovals));
        return new Account().setMultifactorAuthList(remainingApprovals);
    }
}
