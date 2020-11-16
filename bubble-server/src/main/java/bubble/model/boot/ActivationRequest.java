/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.boot;

import bubble.model.account.AccountSshKey;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.validation.HasValue;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class ActivationRequest {

    @HasValue(message="err.email.required")
    @Getter private String email;
    public ActivationRequest setEmail(String e) { this.email = empty(e) ? e : e.trim(); return this; }
    public boolean hasEmail() { return !empty(email); }

    public String getName() { return getEmail(); }
    public ActivationRequest setName(String n) { return setEmail(n); }

    @HasValue(message="err.password.required")
    @Getter @Setter private String password;
    public boolean hasPassword() { return !empty(password); }

    @Getter @Setter private String description;
    public boolean hasDescription() { return !empty(description); }

    @HasValue(message="err.networkName.required")
    @Getter @Setter private String networkName = "boot-network";

    @Getter @Setter private Map<String, CloudServiceConfig> cloudConfigs = new LinkedHashMap<>();
    public boolean hasCloudConfigs () { return !empty(cloudConfigs); }
    public ActivationRequest addCloudConfig(CloudService cloud) {
        cloudConfigs.put(cloud.getName(), cloud.toCloudConfig());
        return this;
    }

    @HasValue(message="err.domain.required")
    @Getter @Setter private BubbleDomain domain;

    @Getter @Setter private Boolean createDefaultObjects = true;
    public boolean createDefaultObjects () { return bool(createDefaultObjects); };

    @Getter @Setter private Boolean skipTests = false;
    public boolean skipTests () { return bool(skipTests); };

    @Getter @Setter private Boolean skipPacker = false;
    public boolean skipPacker () { return bool(skipPacker); };

    @Getter @Setter private AccountSshKey sshKey;
    public boolean hasSshKey () { return sshKey != null; }

}
