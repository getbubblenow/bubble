package bubble.model.boot;

import bubble.model.account.AccountSshKey;
import bubble.model.cloud.AnsibleRole;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.validation.HasValue;

import java.util.LinkedHashMap;
import java.util.Map;

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
    @Getter @Setter private String networkName = "boot-network";

    @Getter @Setter private AnsibleRole[] roles;
    public boolean hasRoles () { return !empty(roles); }

    @Getter @Setter private Map<String, CloudServiceConfig> cloudConfigs = new LinkedHashMap<>();
    public boolean hasCloudConfigs () { return !empty(cloudConfigs); }
    public ActivationRequest addCloudConfig(CloudService cloud) {
        cloudConfigs.put(cloud.getName(), cloud.toCloudConfig());
        return this;
    }

    @HasValue(message="err.domain.required")
    @Getter @Setter private BubbleDomain domain;

    @Getter @Setter private Boolean createDefaultObjects = true;
    public boolean createDefaultObjects () { return createDefaultObjects != null && createDefaultObjects; };

    @Getter @Setter private Boolean skipTests = false;
    public boolean skipTests () { return skipTests != null && skipTests; };

    @Getter @Setter private AccountSshKey sshKey;
    public boolean hasSshKey () { return sshKey != null; }

}
