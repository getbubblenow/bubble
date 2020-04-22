/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account;

public interface HasNetwork extends HasAccount {

    String getNetwork ();
    String getDomain ();

}
