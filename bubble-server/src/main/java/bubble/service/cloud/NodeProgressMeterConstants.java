package bubble.service.cloud;

import org.cobbzilla.util.collection.MapBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.reflect.FieldUtils.getAllFields;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.constValue;
import static org.cobbzilla.util.reflect.ReflectionUtil.isStaticFinalString;

public class NodeProgressMeterConstants {

    public static final String TICKS_JSON = "bubble/node_progress_meter_ticks.json";

    public static final String TICK_PREFIX = "METER_TICK_";

    public static final String METER_TICK_CONFIRMING_NETWORK_LOCK = "BUBBLE: CONFIRMING NETWORK LOCK...";
    public static final String METER_TICK_VALIDATING_NODE_NETWORK_AND_PLAN = "BUBBLE: VALIDATING NODE, NETWORK, AND PLAN...";
    public static final String METER_TICK_CREATING_NODE = "BUBBLE: CREATING NODE...";
    public static final String METER_TICK_LAUNCHING_NODE = "BUBBLE: LAUNCHING NODE...";
    public static final String METER_TICK_PREPARING_ROLES = "BUBBLE: PREPARING ANSIBLE ROLES...";
    public static final String METER_TICK_WRITING_DNS_RECORDS = "BUBBLE: WRITING DNS RECORDS...";
    public static final String METER_TICK_PREPARING_INSTALL = "BUBBLE: PREPARING INSTALL FILES...";
    public static final String METER_TICK_AWAITING_DNS = "BUBBLE: AWAITING DNS RECORDS...";
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
    public static final String METER_ERROR_BUBBLE_JAR_NOT_FOUND = "BUBBLE-ERROR: BUBBLE JAR NOT FOUND";
    public static final String METER_ERROR_ROLES_NOT_FOUND = "BUBBLE-ERROR: ANSIBLE ROLES NOT FOUND";
    public static final String METER_ERROR_NO_IP_OR_SSH_KEY = "BUBBLE-ERROR: NODE STARTED BUT HAS NO IP ADDRESS OR SSH KEY";
    public static final String METER_ERROR_ROLE_VALIDATION_ERRORS = "BUBBLE-ERROR: ROLE VALIDATION FAILED";

    public static final String METER_COMPLETED = "meter_completed";
    public static final String METER_UNKNOWN_ERROR = "meter_unknown_error";

    private static final Map<String, Integer> STANDARD_TICKS = MapBuilder.build(new Object[][] {
            {METER_TICK_CONFIRMING_NETWORK_LOCK, 1},
            {METER_TICK_VALIDATING_NODE_NETWORK_AND_PLAN, 2},
            {METER_TICK_CREATING_NODE, 3},
            {METER_TICK_LAUNCHING_NODE, 4},
            {METER_TICK_PREPARING_ROLES, 9},
            {METER_TICK_WRITING_DNS_RECORDS, 10},
            {METER_TICK_PREPARING_INSTALL, 12},
            {METER_TICK_AWAITING_DNS, 13},
            {METER_TICK_STARTING_INSTALL, 19},
            {METER_TICK_COPYING_ANSIBLE, 20},
            {METER_TICK_RUNNING_ANSIBLE, 24}
    });

    public static List<NodeProgressMeterTick> getStandardTicks() {
        final List<NodeProgressMeterTick> ticks = new ArrayList<>();
        for (Field f : getAllFields(NodeProgressMeterConstants.class)) {
            if (isStaticFinalString(f, TICK_PREFIX)) {
                final String value = constValue(f);
                final Integer percent = STANDARD_TICKS.get(value);
                if (percent == null) return die("getStandardTicks: "+f.getName()+" entry missing from STANDARD_TICKS");
                ticks.add(new NodeProgressMeterTick()
                        .setPattern(value)
                        .setExact(true)
                        .setStandard(true)
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

}
