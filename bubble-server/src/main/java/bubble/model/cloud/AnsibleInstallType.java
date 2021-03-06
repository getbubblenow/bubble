/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AnsibleInstallType {

    standard, sage, node;

    @JsonCreator public static AnsibleInstallType fromString(String val) { return enumFromString(AnsibleInstallType.class, val); }

    public boolean shouldInstall(AnsibleInstallType installType) {
        return installType == this || this == standard;
    }

}
