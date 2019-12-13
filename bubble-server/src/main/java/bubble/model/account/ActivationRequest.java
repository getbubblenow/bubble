package bubble.model.account;

import bubble.model.cloud.AnsibleRole;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.validation.HasValue;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class ActivationRequest {

    @HasValue(message="err.name.required")
    @Getter @Setter private String name;
    public boolean hasName () { return !empty(name); }

    @HasValue(message="err.password.required")
    @Getter @Setter private String password;
    public boolean hasPassword() { return !empty(password); }

    @Getter @Setter private String description;
    public boolean hasDescription() { return !empty(description); }

    @HasValue(message="err.networkName.required")
    @Getter @Setter private String networkName;

    @Getter @Setter private AnsibleRole[] roles;
    public boolean hasRoles () { return roles != null && roles.length > 0; }

    @HasValue(message="err.domain.required")
    @Getter @Setter private BubbleDomain domain;

    @HasValue(message="err.dns.required")
    @Getter @Setter private CloudService dns;

    @HasValue(message="err.storage.required")
    @Getter @Setter private CloudService storage;

}
