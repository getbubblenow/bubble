/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.notify.NewNodeNotification;
import org.cobbzilla.util.collection.MapBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bubble.service.cloud.NodeProgressMeterTick.TickMatchType.prefix;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.reflect.FieldUtils.getAllFields;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.constValue;
import static org.cobbzilla.util.reflect.ReflectionUtil.isStaticFinalString;

public class NodeProgressMeterConstants {

    public static final String TICK_PREFIX = "METER_TICK_";

    public static final String METER_TICK_CONFIRMING_NETWORK_LOCK = "BUBBLE: CONFIRMING NETWORK LOCK...";
    public static final String METER_TICK_VALIDATING_NODE_NETWORK_AND_PLAN = "BUBBLE: VALIDATING NODE, NETWORK, AND PLAN...";
    public static final String METER_TICK_CREATING_NODE = "BUBBLE: CREATING NODE...";
    public static final String METER_TICK_LAUNCHING_NODE = "BUBBLE: LAUNCHING NODE...";
    public static final String METER_TICK_PREPARING_ROLES = "BUBBLE: PREPARING ANSIBLE ROLES...";
    public static final String METER_TICK_PREPARING_INSTALL = "BUBBLE: PREPARING INSTALL FILES...";
    public static final String METER_TICK_AWAIT_1  = "AWAIT-1";
    public static final String METER_TICK_AWAIT_6  = "AWAIT-6";
    public static final String METER_TICK_AWAIT_11 = "AWAIT-11";
    public static final String METER_TICK_AWAIT_16 = "AWAIT-16";
    public static final String METER_TICK_AWAIT_21 = "AWAIT-21";
    public static final String METER_TICK_AWAIT_26 = "AWAIT-26";
    public static final String METER_TICK_AWAIT_31 = "AWAIT-31";
    public static final String METER_TICK_AWAIT_36 = "AWAIT-36";
    public static final String METER_TICK_AWAIT_41 = "AWAIT-41";
    public static final String METER_TICK_AWAIT_46 = "AWAIT-46";
    public static final String METER_TICK_AWAIT_51 = "AWAIT-51";
    public static final String METER_TICK_AWAIT_56 = "AWAIT-56";
    public static final String METER_TICK_AWAIT_61 = "AWAIT-61";
    public static final String METER_TICK_AWAIT_66 = "AWAIT-66";
    public static final String METER_TICK_AWAIT_71 = "AWAIT-71";
    public static final String METER_TICK_AWAIT_76 = "AWAIT-76";
    public static final String METER_TICK_AWAIT_81 = "AWAIT-81";
    public static final String METER_TICK_AWAIT_86 = "AWAIT-86";
    public static final String METER_TICK_STARTING_INSTALL = "BUBBLE: STARTING INSTALLATION...";
    public static final String METER_TICK_COPYING_ANSIBLE = "BUBBLE: COPYING ANSIBLE FILES...";
    public static final String METER_TICK_RUNNING_ANSIBLE = "BUBBLE: RUNNING ANSIBLE PLAYBOOK...";

    public static final String ERROR_PREFIX = "METER_ERROR_";

    public static final String METER_ERROR_CONFIRMING_NETWORK_LOCK = "BUBBLE-ERROR: ERROR CONFIRMING NETWORK LOCK";
    public static final String METER_ERROR_NETWORK_NOT_READY_FOR_SETUP = "BUBBLE-ERROR: NETWORK NOT READY FOR SETUP";
    public static final String METER_ERROR_NO_CURRENT_NODE_OR_NETWORK = "BUBBLE-ERROR: NO CURRENT NODE OR NETWORK";
    public static final String METER_ERROR_PLAN_NOT_ENABLED = "BUBBLE-ERROR: PLAN NOT ENABLED";
    public static final String METER_ERROR_PEER_LIMIT_REACHED = "BUBBLE-ERROR: PEER LIMIT REACHED";
    public static final String METER_ERROR_NODE_CLOUD_NOT_FOUND = "BUBBLE-ERROR: NODE CLOUD NOT FOUND";
    public static final String METER_ERROR_STARTING_NODE = "BUBBLE-ERROR: ERROR STARTING NODE";
    public static final String METER_ERROR_DNS = "BUBBLE-ERROR: ERROR SETTING DNS ENTRIES FOR NODE";
    public static final String METER_ERROR_NO_IP = "BUBBLE-ERROR: NODE STARTED BUT HAS NO IP ADDRESS";
    public static final String METER_ERROR_ROLE_VALIDATION_ERRORS = "BUBBLE-ERROR: ROLE VALIDATION FAILED";

    public static final String METER_COMPLETED = "meter_completed";
    public static final String METER_CANCELED = "meter_canceled";
    public static final String METER_START_OR_DNS_ERROR = "meter_start_or_dns_error";
    public static final String METER_UNKNOWN_ERROR = "meter_unknown_error";

    private static final Map<String, Integer> STANDARD_TICKS = MapBuilder.build(new Object[][] {
            {METER_TICK_CONFIRMING_NETWORK_LOCK, 1},
            {METER_TICK_VALIDATING_NODE_NETWORK_AND_PLAN, 1},
            {METER_TICK_CREATING_NODE, 1},
            {METER_TICK_LAUNCHING_NODE, 1},
            {METER_TICK_PREPARING_ROLES, 5},
            {METER_TICK_PREPARING_INSTALL, 7},
            {METER_TICK_AWAIT_1, 8},
            {METER_TICK_AWAIT_6, 9},
            {METER_TICK_AWAIT_11, 10},
            {METER_TICK_AWAIT_16, 12},
            {METER_TICK_AWAIT_21, 13},
            {METER_TICK_AWAIT_26, 15},
            {METER_TICK_AWAIT_31, 16},
            {METER_TICK_AWAIT_36, 18},
            {METER_TICK_AWAIT_41, 19},
            {METER_TICK_AWAIT_46, 21},
            {METER_TICK_AWAIT_51, 22},
            {METER_TICK_AWAIT_56, 24},
            {METER_TICK_AWAIT_61, 25},
            {METER_TICK_AWAIT_66, 27},
            {METER_TICK_AWAIT_71, 28},
            {METER_TICK_AWAIT_76, 30},
            {METER_TICK_AWAIT_81, 31},
            {METER_TICK_AWAIT_86, 32},
            {METER_TICK_STARTING_INSTALL, 34},
            {METER_TICK_COPYING_ANSIBLE, 35},
            {METER_TICK_RUNNING_ANSIBLE, 37}
    });

    public static List<NodeProgressMeterTick> getStandardTicks(NewNodeNotification nn) {
        final List<NodeProgressMeterTick> ticks = new ArrayList<>();
        for (Field f : getAllFields(NodeProgressMeterConstants.class)) {
            if (isStaticFinalString(f, TICK_PREFIX)) {
                final String value = constValue(f);
                final Integer percent = STANDARD_TICKS.get(value);
                if (percent == null) return die("getStandardTicks: "+f.getName()+" entry missing from STANDARD_TICKS");
                ticks.add(new NodeProgressMeterTick()
                        .setAccount(nn.getAccount())
                        .setNetwork(nn.getNetwork())
                        .setPattern(value)
                        .setMatch(prefix)
                        .setMessageKey(f.getName().toLowerCase())
                        .setPercent(percent));
            }
        }
        return ticks;
    }

    public static final Map<String, String> ERRORS = initErrors();
    private static Map<String, String> initErrors() {
        final Map<String, String> errors = new HashMap<>();
        for (Field f : getAllFields(NodeProgressMeterConstants.class)) {
            if (isStaticFinalString(f, ERROR_PREFIX)) {
                errors.put(constValue(f), f.getName().toLowerCase());
            }
        }
        return errors;
    }

    public static String getErrorMessageKey (String error) {
        final String messageKey = ERRORS.get(error);
        return messageKey != null ? messageKey : METER_UNKNOWN_ERROR;
    }


    public static final String TICKS_JSON = "bubble/node_progress_meter_ticks.json";
    public static final String INSTALL_TICK_PREFIX = "meter_tick_";

    public static List<NodeProgressMeterTick> getInstallTicks(NewNodeNotification nn) {
        final NodeProgressMeterTick[] installTicks = json(stream2string(TICKS_JSON), NodeProgressMeterTick[].class);
        for (NodeProgressMeterTick tick : installTicks) {
            tick.setNetwork(nn.getNetwork())
                    .setAccount(nn.getAccount())
                    .setMessageKey(INSTALL_TICK_PREFIX + tick.getMessageKey());
        }
        return asList(installTicks);
    }
}
