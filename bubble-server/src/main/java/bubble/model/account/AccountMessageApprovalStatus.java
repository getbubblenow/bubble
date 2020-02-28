/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AccountMessageApprovalStatus {

    request_not_found, request_expired, request_already_denied, wrong_contact_sent_approval, already_confirmed,
    ok_confirmed, ok_accepted_and_awaiting_further_approvals;

    @JsonCreator public static AccountMessageApprovalStatus fromString (String v) { return enumFromString(AccountMessageApprovalStatus.class, v); }

    public boolean ok () { return name().startsWith("ok_"); }

}
