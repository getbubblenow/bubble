/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account.message;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.*;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.service.account.AccountMessageService;
import bubble.service.account.AuthenticatorService;
import bubble.service.boot.SelfNodeService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.string.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.wizard.model.IdentifiableBase.CTIME_DESC;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class AccountMessageDAO extends AccountOwnedEntityDAO<AccountMessage> {

    private static final String DATA_TOTP_TOKEN = "totpToken";

    @Autowired private AccountMessageService messageService;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private SelfNodeService selfNodeService;
    @Autowired private AuthenticatorService authenticatorService;

    @Override public AccountMessage postCreate(AccountMessage message, Object context) {
        if (!messageService.send(message)) {
            log.warn("postCreate: message not sent: "+message);
        }
        return super.postCreate(message, context);
    }

    public AccountMessage sendVerifyRequest(String remoteHost, Account account, AccountContact contact) {
        return create(new AccountMessage()
                .setAccount(account.getUuid())
                .setNetwork(selfNodeService.getThisNetwork().getUuid())
                .setName(account.getUuid())
                .setRemoteHost(remoteHost)
                .setTarget(ActionTarget.account)
                .setMessageType(AccountMessageType.request)
                .setAction(AccountAction.verify)
                .setContact(contact.getUuid()));
    }

    public AccountMessageApprovalStatus requestApproved(Account account,
                                                        AccountMessage approval,
                                                        String token,
                                                        NameAndValue[] data) {

        final String accountUuid = account != null ? account.getUuid() : approval.getAccount();
        final AccountPolicy policy = policyDAO.findSingleByAccount(accountUuid);
        final Long timeout = policy.getTimeout(approval);

        final AccountMessage request = findOperationRequest(approval);
        if (request == null) {
            log.info("requestApproved: request not found for approval: "+approval.getUuid());
            return AccountMessageApprovalStatus.request_not_found;
        }

        // did the request expire?
        if (now() > request.getCtime() + timeout) {
            log.info("requestApproved: request expired: "+request.getUuid());
            return AccountMessageApprovalStatus.request_expired;
        }

        // has it already been denied?
        if (!findOperationDenials(approval).isEmpty()) {
            log.info("requestApproved: request denied: "+request.getUuid());
            return AccountMessageApprovalStatus.request_already_denied;
        }

        // has the approval already been confirmed?
        if (!findOperationConfirmations(approval).isEmpty()) {
            log.info("requestApproved: request already confirmed: "+request.getUuid());
            return AccountMessageApprovalStatus.already_confirmed;
        }

        // if this is a verification approval, it's acceptable if it came from the same contact
        if (request.getTarget() == ActionTarget.account && request.getAction() == AccountAction.verify) {
            if (approval.getContact().equals(request.getContact())) {
                log.info("requestApproved: account contact verified: "+request.getUuid());
                return AccountMessageApprovalStatus.ok_confirmed;
            }
            log.info("requestApproved: account contact NOT verified (wrong contact sent approval(: "+request.getUuid());
            return AccountMessageApprovalStatus.wrong_contact_sent_approval;
        }

        // check required approvals
        final List<AccountMessage> approvals = findOperationApprovals(approval);
        final List<AccountContact> requiredApprovals = policy.getRequiredApprovals(approval);

        // is any AccountContact that requires approval missing its corresponding approval AccountMessage?
        if (requiredApprovalsRemain(approvals, requiredApprovals, false)) {
            // If the only remaining approval is an authenticator, check for valid totpToken in data
            final AccountContact authenticator = requiredApprovals.stream().filter(AccountContact::isAuthenticator).findFirst().orElse(null);
            if (authenticator != null) {
                final String totpToken = NameAndValue.find(data, DATA_TOTP_TOKEN);
                if (!empty(totpToken)) {
                    authenticatorService.authenticate(account, policy, new AuthenticatorRequest()
                            .setAccount(accountUuid)
                            .setToken(totpToken));
                    if (!requiredApprovalsRemain(approvals, requiredApprovals, true)) {
                        // totp was the only remaining required approval, this request is approved
                        log.info("requestApproved: only remaining required approval was authenticator and totpToken was valid, can confirm: "+approval.getUuid());
                        return AccountMessageApprovalStatus.ok_confirmed;
                    } else {
                        log.info("requestApproved: > 1 required approvals remain: "+StringUtil.toString(requiredApprovals));
                    }
                } else {
                    log.info("requestApproved: > required approvals remain (and no totpToken provided): "+StringUtil.toString(requiredApprovals));
                    throw invalidEx("err.totpToken.required");
                }
            } else {
                log.info("requestApproved: > required approvals remain (and none are authenticator): "+StringUtil.toString(requiredApprovals));
            }
            log.info("requestApproved: OK, awaiting more approvals: "+StringUtil.toString(requiredApprovals));
            return AccountMessageApprovalStatus.ok_accepted_and_awaiting_further_approvals;
        }

        // if this was a login request, and approval is sufficient to confirm, then confirm
        if (request.getAction() == AccountAction.login && request.getTarget() == ActionTarget.account) {
            final AuthFactorType authFactor = policy.getAuthFactor(approval.getContact());
            if (authFactor == null) {
                log.warn("requestApproved: authFactor was null for contact: "+approval.getContact());
            }
            if (authFactor == AuthFactorType.sufficient) {
                log.info("requestApproved: authFactor sufficient for login, can confirm: "+approval.getUuid());
                return AccountMessageApprovalStatus.ok_confirmed;
            }
        }

        log.info("requestApproved: all approvals received, can confirm");
        return AccountMessageApprovalStatus.ok_confirmed;
    }

    public boolean requiredApprovalsRemain(List<AccountMessage> approvals,
                                           List<AccountContact> requiredApprovals,
                                           boolean skipAuthenticator) {
        for (AccountContact contact : requiredApprovals) {
            if (contact.isAuthenticator() && skipAuthenticator) continue;
            boolean approved = false;
            for (AccountMessage approval : approvals) {
                if (approval.getContact().equals(contact.getUuid())) {
                    approved = true;
                    break;
                }
            }
            if (!approved) return true;
        }
        return false;
    }

    public AccountMessage findOperationRequest(AccountMessage basis) {
        return findByUniqueFields("account", basis.getAccount(),
                "name", basis.getName(),
                "requestId", basis.getRequestId(),
                "messageType", AccountMessageType.request,
                "action", basis.getAction(),
                "target", basis.getTarget());
    }

    public List<AccountMessage> findOperationDenials(AccountMessage basis) {
        if (basis == null) {
            return Collections.emptyList();
        }
        return findByFields("account", basis.getAccount(),
                "name", basis.getAccount(),
                "requestId", basis.getRequestId(),
                "messageType", AccountMessageType.denial,
                "action", basis.getAction(),
                "target", basis.getTarget());
    }

    public List<AccountMessage> findOperationApprovals(AccountMessage basis) {
        return findByFields("account", basis.getAccount(),
                "name", basis.getName(),
                "requestId", basis.getRequestId(),
                "messageType", AccountMessageType.approval,
                "action", basis.getAction(),
                "target", basis.getTarget());
    }

    public List<AccountMessage> findOperationConfirmations(AccountMessage basis) {
        return findByFields("account", basis.getAccount(),
                "name", basis.getAccount(),
                "requestId", basis.getRequestId(),
                "messageType", AccountMessageType.confirmation,
                "action", basis.getAction(),
                "target", basis.getTarget());
    }

    public AccountMessage findMostRecentLoginRequest(String accountUuid) {
        final List<AccountMessage> requests = findByFields("account", accountUuid,
                "name", accountUuid,
                "messageType", AccountMessageType.request,
                "action", AccountAction.login,
                "target", ActionTarget.account);
        if (requests.isEmpty()) return null;
        requests.sort(CTIME_DESC);
        return requests.get(0);
    }
}
