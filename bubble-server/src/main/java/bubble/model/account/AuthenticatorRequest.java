package bubble.model.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class AuthenticatorRequest {

    @Getter @Setter private String account;
    @Getter @Setter private int token;

}
