/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify;

import bubble.cloud.CloudAndRegion;
import bubble.model.account.AccountContact;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.NetLocation;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;

import static bubble.ApiConstants.newNodeHostname;
import static bubble.model.account.AccountContact.mask;
import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class NewNodeNotification {

    @Getter @Setter private String uuid = randomUUID().toString();
    @Getter @Setter private String account;
    @Getter @Setter private String host;
    @Getter @Setter private String network;
    @Getter @Setter private String networkName;
    @Getter @Setter @JsonIgnore private BubbleNetwork networkObject;
    @Getter @Setter private String domain;
    @Getter @Setter private String fqdn;
    @Getter @Setter private NetLocation netLocation;

    @Transient @JsonIgnore @Getter @Setter private List<CloudAndRegion> excludeRegions;
    public NewNodeNotification excludeRegion (String cloudUuid, String regionInternalName) {
        if (excludeRegions == null) excludeRegions = new ArrayList<>();
        excludeRegions.add(new CloudAndRegion(cloudUuid, regionInternalName));
        return this;
    }
    public NewNodeNotification excludeCurrentRegion (BubbleNode node) { return excludeRegion(node.getCloud(), node.getRegion()); }

    @Getter @Setter private Boolean automated;
    public boolean automated () { return automated != null && automated; }

    @Getter @Setter private Boolean fork;
    public boolean fork() { return fork != null && fork; }

    @Getter @Setter private String restoreKey;
    public boolean hasRestoreKey () { return !empty(restoreKey); }

    @Getter @Setter private String lock;

    @Transient @Getter @Setter private transient AccountContact[] multifactorAuth;

    public static NewNodeNotification requiresAuth(List<AccountContact> contacts) {
        return new NewNodeNotification().setMultifactorAuth(mask(contacts));
    }

    public static String nodeHostname(BubbleNetwork network) {
        return network.fork() ? network.getForkHost() : newNodeHostname();
    }

    public NewNodeNotification setNodeHost(BubbleNetwork network) {
        final String hostname = nodeHostname(network);
        setAccount(network.getAccount());
        setNetworkObject(network);
        setNetwork(network.getUuid());
        setNetworkName(network.getName());
        setDomain(network.getDomain());
        setHost(hostname);
        setFqdn(hostname + "." + network.getNetworkDomain());
        return this;
    }

}
