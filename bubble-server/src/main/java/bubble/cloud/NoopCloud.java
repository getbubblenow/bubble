/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud;

import bubble.cloud.auth.AuthenticationDriver;
import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.compute.*;
import bubble.cloud.dns.DnsServiceDriver;
import bubble.cloud.email.EmailServiceDriver;
import bubble.cloud.email.RenderedEmail;
import bubble.cloud.geoCode.GeoCodeResult;
import bubble.cloud.geoCode.GeoCodeServiceDriver;
import bubble.cloud.geoLocation.GeoLocateServiceDriver;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.cloud.geoTime.GeoTimeServiceDriver;
import bubble.cloud.geoTime.GeoTimeZone;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.cloud.sms.SmsServiceDriver;
import bubble.cloud.storage.StorageServiceDriver;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.BubblePlan;
import bubble.model.bill.PaymentMethodType;
import bubble.model.cloud.*;
import bubble.notify.payment.PaymentValidationResult;
import bubble.notify.storage.StorageListing;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
public class NoopCloud implements
        AuthenticationDriver, ComputeServiceDriver, DnsServiceDriver, EmailServiceDriver,
        GeoCodeServiceDriver, GeoLocateServiceDriver, GeoTimeServiceDriver, PaymentServiceDriver,
        SmsServiceDriver, StorageServiceDriver {

    public static final String NOOP_CLOUD_NAME = NoopCloud.class.getSimpleName();
    public static final String NOOP_CLOUD = NoopCloud.class.getName();

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {
        if (log.isDebugEnabled()) log.debug("send(account=" + account + ")");
        return false;
    }

    @Override public RenderedEmail renderMessage(Account account, AccountMessage message, AccountContact contact) {
        if (log.isDebugEnabled()) log.debug("renderMessage(account=" + account + ")");
        return null;
    }

    @Override public boolean send(RenderedMessage renderedMessage) {
        if (log.isDebugEnabled()) log.debug("send(renderedMessage=" + renderedMessage + ")");
        return false;
    }

    @Override public boolean test() {
        if (log.isDebugEnabled()) log.debug("test()");
        return false;
    }

    @Override public List<PackerImage> getAllPackerImages() { return null; }

    @Override public List<PackerImage> getPackerImagesForRegion(String region) { return null; }

    @Override public boolean _write(String fromNode, String key, InputStream data, StorageMetadata metadata, String requestId) throws IOException {
        if (log.isDebugEnabled()) log.debug("_write(fromNode=" + fromNode + ")");
        return false;
    }

    @Override public boolean canWrite(String fromNode, String toNode, String key) {
        if (log.isDebugEnabled()) log.debug("canWrite(fromNode=" + fromNode + ")");
        return false;
    }

    @Override public boolean delete(String fromNode, String uri) {
        if (log.isDebugEnabled()) log.debug("delete(fromNode=" + fromNode + ")");
        return false;
    }

    @Override public boolean deleteNetwork(String networkUuid) throws IOException {
        if (log.isDebugEnabled()) log.debug("deleteNetwork(networkUuid=" + networkUuid + ")");
        return false;
    }

    @Override public boolean rekey(String fromNode, CloudService newCloud) throws IOException {
        if (log.isDebugEnabled()) log.debug("rekey(fromNode=" + fromNode + ")");
        return false;
    }

    @Override public StorageListing list(String fromNode, String prefix) throws IOException {
        if (log.isDebugEnabled()) log.debug("list(fromNode=" + fromNode + ")");
        return null;
    }

    @Override public StorageListing listNext(String fromNode, String listingId) throws IOException {
        if (log.isDebugEnabled()) log.debug("listNext(fromNode=" + fromNode + ")");
        return null;
    }

    @Override public void setConfig(JsonNode json, CloudService cloudService) {
        if (log.isDebugEnabled()) log.debug("setConfig(json=" + json + ")");

    }

    @Override public CloudCredentials getCredentials() {
        if (log.isDebugEnabled()) log.debug("getCredentials()");
        return null;
    }

    @Override public void setCredentials(CloudCredentials creds) {
        if (log.isDebugEnabled()) log.debug("setCredentials(creds=" + creds + ")");

    }

    @Override public CloudServiceType getType() {
        if (log.isDebugEnabled()) log.debug("getType()");
        return null;
    }

    @Override public boolean _exists(String fromNode, String key) throws IOException {
        if (log.isDebugEnabled()) log.debug("_exists(fromNode=" + fromNode + ")");
        return false;
    }

    @Override public StorageMetadata readMetadata(String fromNode, String key) {
        if (log.isDebugEnabled()) log.debug("readMetadata(fromNode=" + fromNode + ")");
        return null;
    }

    @Override public InputStream _read(String fromNode, String key) throws IOException {
        if (log.isDebugEnabled()) log.debug("_read(fromNode=" + fromNode + ")");
        return null;
    }

    @Override public PaymentMethodType getPaymentMethodType() {
        if (log.isDebugEnabled()) log.debug("getPaymentMethodType()");
        return null;
    }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        if (log.isDebugEnabled()) log.debug("validate(paymentMethod=" + paymentMethod + ")");
        return null;
    }

    @Override public boolean authorize(BubblePlan plan, String accountPlanUuid, String billUuid, AccountPaymentMethod paymentMethod) {
        if (log.isDebugEnabled()) log.debug("authorize(plan=" + plan + ")");
        return false;
    }

    @Override public boolean cancelAuthorization(BubblePlan plan, String accountPlanUuid, AccountPaymentMethod paymentMethod) {
        if (log.isDebugEnabled()) log.debug("cancelAuthorization(plan=" + plan + ")");
        return false;
    }

    @Override public boolean purchase(String accountPlanUuid, String paymentMethodUuid, String billUuid) {
        if (log.isDebugEnabled()) log.debug("purchase(accountPlanUuid=" + accountPlanUuid + ")");
        return false;
    }

    @Override public boolean refund(String accountPlanUuid) {
        if (log.isDebugEnabled()) log.debug("refund(accountPlanUuid=" + accountPlanUuid + ")");
        return false;
    }

    @Override public GeoTimeZone getTimezone(String lat, String lon) {
        if (log.isDebugEnabled()) log.debug("getTimezone(lat=" + lat + ")");
        return null;
    }

    @Override public GeoLocation geolocate(String ip) {
        if (log.isDebugEnabled()) log.debug("geolocate(ip=" + ip + ")");
        return null;
    }

    @Override public GeoCodeResult lookup(GeoLocation location) {
        if (log.isDebugEnabled()) log.debug("lookup(location=" + location + ")");
        return null;
    }

    @Override public Collection<DnsRecord> create(BubbleDomain domain) {
        if (log.isDebugEnabled()) log.debug("create(domain=" + domain + ")");
        return null;
    }

    @Override public Collection<DnsRecord> setNetwork(BubbleNetwork network) {
        if (log.isDebugEnabled()) log.debug("setNetwork(network=" + network + ")");
        return null;
    }

    @Override public Collection<DnsRecord> setNode(BubbleNode node) {
        if (log.isDebugEnabled()) log.debug("setNode(node=" + node + ")");
        return null;
    }

    @Override public Collection<DnsRecord> deleteNode(BubbleNode node) {
        if (log.isDebugEnabled()) log.debug("deleteNode(node=" + node + ")");
        return null;
    }

    @Override public DnsRecord update(DnsRecord record) {
        if (log.isDebugEnabled()) log.debug("update(record=" + record + ")");
        return null;
    }

    @Override public DnsRecord remove(DnsRecord record) {
        if (log.isDebugEnabled()) log.debug("remove(record=" + record + ")");
        return null;
    }

    @Override public Collection<DnsRecord> list(DnsRecordMatch matcher) {
        if (log.isDebugEnabled()) log.debug("list(matcher=" + matcher + ")");
        return null;
    }

    @Override public List<ComputeNodeSize> getSizes() {
        if (log.isDebugEnabled()) log.debug("getSizes()");
        return null;
    }

    @Override public ComputeNodeSize getSize(ComputeNodeSizeType type) {
        if (log.isDebugEnabled()) log.debug("getSize(type=" + type + ")");
        return null;
    }

    @Override public BubbleNode start(BubbleNode node) throws Exception {
        if (log.isDebugEnabled()) log.debug("start(node=" + node + ")");
        return null;
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) throws Exception {
        if (log.isDebugEnabled()) log.debug("cleanupStart(node=" + node + ")");
        return null;
    }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        if (log.isDebugEnabled()) log.debug("stop(node=" + node + ")");
        return null;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        if (log.isDebugEnabled()) log.debug("status(node=" + node + ")");
        return null;
    }

    @Override public OsImage getOs() { return null; }

    @Override public Map<String, ComputeNodeSize> getSizesMap() { return null; }

    @Override public List<CloudRegion> getRegions() {
        if (log.isDebugEnabled()) log.debug("getRegions()");
        return null;
    }

    @Override public CloudRegion getRegion(String region) {
        if (log.isDebugEnabled()) log.debug("getRegion(region=" + region + ")");
        return null;
    }
}
