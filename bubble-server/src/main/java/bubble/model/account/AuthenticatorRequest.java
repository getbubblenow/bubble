package bubble.model.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.string.StringUtil.safeParseInt;

@NoArgsConstructor @Accessors(chain=true)
public class AuthenticatorRequest {

    @Getter @Setter private String account;

    @Getter @Setter private String token;
    public Integer intToken() { return safeParseInt(getToken()); }

    @Getter @Setter private Boolean verify;
    public boolean verify() { return bool(verify); }

    @Getter @Setter private Boolean authenticate;
    public boolean authenticate() { return bool(authenticate); }

    public boolean startSession() { return !verify() && !authenticate(); }

}
