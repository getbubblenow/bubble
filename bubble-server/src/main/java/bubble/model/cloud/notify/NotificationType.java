/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud.notify;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.OsImage;
import bubble.cloud.geoCode.GeoCodeResult;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.cloud.geoTime.GeoTimeZone;
import bubble.model.cloud.BubbleNode;
import bubble.notify.ReceivedNotificationHandler;
import bubble.notify.payment.PaymentResult;
import bubble.notify.payment.PaymentValidationResult;
import bubble.notify.storage.StorageResult;
import bubble.notify.upgrade.AppsUpgradeNotification;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import org.cobbzilla.util.dns.DnsRecord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.ApiConstants.enumFromString;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.getSystemTimeOffset;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

public enum NotificationType {

    // network-level notifications
    health_check, hello_to_sage, hello_from_sage, peer_hello, sync_account,
    register_backup, retrieve_backup, backup_response, restore_complete, fork,

    // upgrade notifications
    upgrade_request (String.class),
    upgrade_response (true),
    upgrade_apps_request (AppsUpgradeNotification.class),
    upgrade_apps_response (true),

    // driver-level notifications

    // delegated dns driver notifications
    dns_driver_set_network (DnsRecord[].class),
    dns_driver_set_node (DnsRecord[].class),
    dns_driver_delete_node (DnsRecord[].class),
    dns_driver_create (DnsRecord[].class),
    dns_driver_list (DnsRecord[].class),
    dns_driver_update (DnsRecord.class),
    dns_driver_remove (DnsRecord.class),
    dns_driver_response (true),

    // delegated geo driver notifications
    geoLocation_driver_geolocate (GeoLocation.class),
    geoLocation_driver_response (true),

    // delegate geo code driver notifications
    geoCode_driver_geocode (GeoCodeResult.class),
    geoCode_driver_response (true),

    // delegate geo time driver notifications
    geoTime_driver_geotime (GeoTimeZone.class),
    geoTime_driver_response (true),

    // delegated compute driver notifications
    compute_driver_get_sizes (ComputeNodeSize[].class),
    compute_driver_get_regions (CloudRegion[].class),
    compute_driver_get_os (OsImage.class),
    compute_driver_start (BubbleNode.class),
    compute_driver_cleanup_start (BubbleNode.class),
    compute_driver_stop (BubbleNode.class),
    compute_driver_status (BubbleNode.class),
    compute_driver_response (true),

    // delegated email driver notifications
    email_driver_send (Boolean.class),
    email_driver_response (true),

    // delegated email driver notifications
    sms_driver_send (Boolean.class),
    sms_driver_response (true),

    // delegated authenticator driver notifications
    authenticator_driver_send (Boolean.class),
    authenticator_driver_response (true),

    // delegated storage driver notifications
    storage_driver_exists (StorageResult.class),
    storage_driver_read_metadata (StorageResult.class),
    storage_driver_read (String.class),
    storage_driver_write (StorageResult.class),
    storage_driver_list (StorageResult.class),
    storage_driver_list_next (StorageResult.class),
    storage_driver_delete (StorageResult.class),
    storage_driver_delete_network (StorageResult.class),
    storage_driver_response (true),

    // delegated payment driver notifications
    payment_driver_validate (PaymentValidationResult.class),
    payment_driver_claim (PaymentValidationResult.class),
    payment_driver_amount_due (Long.class),
    payment_driver_authorize (PaymentResult.class),
    payment_driver_cancel_authorization (PaymentResult.class),
    payment_driver_purchase (PaymentResult.class),
    payment_driver_refund (PaymentResult.class),
    payment_driver_response (true);

    private String packageName = "bubble.notify";

    @Getter private Class<?> responseClass = null;
    @Getter private boolean response = false;

    NotificationType () {}

    NotificationType (boolean response) { this(null, response); }

    NotificationType (Class<?> responseClass) { this(responseClass, false); }

    NotificationType (Class<?> responseClass, boolean response) {
        this.packageName = "bubble.notify." + name().substring(0, name().indexOf("_"));
        this.responseClass = responseClass;
        this.response = response;
    }

    @JsonCreator public static NotificationType fromString (String v) { return enumFromString(NotificationType.class, v); }

    private static final Map<NotificationType, Class<? extends ReceivedNotificationHandler>> handlerClasses = new ConcurrentHashMap<>();
    private static final Map<Class<? extends ReceivedNotificationHandler>, ReceivedNotificationHandler> handlers = new ConcurrentHashMap<>();

    public Class<? extends ReceivedNotificationHandler> getHandlerClass() {
        return handlerClasses.computeIfAbsent(this, k -> forName(packageName+".NotificationHandler_" + k.name()));
    }

    public ReceivedNotificationHandler getHandler (BubbleConfiguration configuration) {
        return handlers.computeIfAbsent(getHandlerClass(), k -> configuration.autowire(instantiate(k)));
    }

    public <T> T toResponse(JsonNode response) {
        return responseClass != null
                ? (T) json(response, responseClass)
                : die("toResponse: no responseClass defined for "+this.name());
    }

    public boolean canReturnCachedResponse() {
        // payment validation requests are never cached, because the response depends on data outside the message
        // specifically, if the user does not have any validated email address, an error is returned
        // but an identical notification can later return a different response, after the email has been validated
        // also, do not cache if the system time has been altered
        return this != payment_driver_validate && getSystemTimeOffset() == 0;
    }
}
