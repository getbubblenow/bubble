/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.device;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;

import static bubble.ApiConstants.enumFromString;

@AllArgsConstructor
public enum DeviceSecurityLevel {

    /**
     * Maximum security
     * ----------------
     * Force SSL interception on everything. If intercept fails, drop the connection.
     *
     * Behavior:
     *   TLS         : never passthru, always intercept
     *   TLS Failure : don't allow the connection
     *   DNS         : DNS-based blocks are active
     *   Block Stats : show when possible
     */
    maximum  (true, BlockStatsDisplayMode.default_on),

    /**
     * Strict security
     * ----------------
     * SSL interception enabled by default. Passthru hostnames read from whitelist.
     *
     * Behavior:
     *   TLS         : use passthru whitelist, otherwise intercept
     *   TLS Failure : try passthru on the subsequent requests
     *   DNS         : DNS-based blocks are active
     *   Block Stats : show when possible
     */
    strict   (true, BlockStatsDisplayMode.default_on),

    /**
     * Standard security
     * ----------------
     * SSL interception only enabled for specific hostnames.
     *
     * Behavior:
     *   TLS         : passthru by default, unless explicitly filtered
     *   TLS Failure : try passthru on the subsequent requests
     *   DNS         : DNS-based blocks are active
     *   Block Stats : show only for enabled domains
     */
    standard (true, BlockStatsDisplayMode.default_off),

    /**
     * Basic security
     * ----------------
     * Only perform DNS blocking. Use on devices where installation of the Bubble CA Cert is not allowed (Android)
     *
     * Behavior:
     *   TLS         : always passthru, nothing filtered
     *   TLS Failure : should never fail, always passthru
     *   DNS         : DNS-based blocks are active
     *   Block Stats : never shown
     */
    basic    (false, BlockStatsDisplayMode.disabled),

    /**
     * Disabled security
     * Turn off all security, use Bubble like a regular VPN with no interception and no DNS blocking.
     *
     * Behavior:
     *   TLS         : always passthru, nothing filtered
     *   TLS Failure : should never fail, always passthru
     *   DNS         : DNS-based blocks are disabled
     *   Block Stats : never shown
     */
    disabled (false, BlockStatsDisplayMode.disabled);

    @JsonCreator public static DeviceSecurityLevel fromString (String v) { return enumFromString(DeviceSecurityLevel.class, v); }

    private final boolean supportsRequestModification;
    public boolean supportsRequestModification() { return supportsRequestModification; }

    private final BlockStatsDisplayMode blockStatsDisplayMode;
    public BlockStatsDisplayMode blockStatsDisplayMode() { return blockStatsDisplayMode; }
    public boolean statsEnabled () { return blockStatsDisplayMode.enabled(); }
    public boolean statsDisabled () { return !statsEnabled(); }
    public boolean preferStatsOn () { return blockStatsDisplayMode == BlockStatsDisplayMode.default_on; }
    public boolean preferStatsOff () { return blockStatsDisplayMode == BlockStatsDisplayMode.default_off; }

}
