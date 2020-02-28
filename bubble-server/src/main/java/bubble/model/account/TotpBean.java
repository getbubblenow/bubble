/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.account;

import bubble.server.BubbleConfiguration;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL;

@NoArgsConstructor @Accessors(chain=true)
public class TotpBean {

    public TotpBean(GoogleAuthenticatorKey creds, Account account, BubbleConfiguration configuration) {
        setKey(creds.getKey());
        setUrl(getOtpAuthTotpURL(configuration.getThisNetwork().getNetworkDomain(), account.getName()+"@"+configuration.getThisNetwork().getNetworkDomain(), creds));
        setBackupCodes(creds.getScratchCodes().toArray(new Integer[0]));
    }

    @Getter @Setter private String key;
    @Getter @Setter private String url;
    @Getter @Setter private Integer[] backupCodes;

}
