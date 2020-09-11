/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.filter;

import bubble.cloud.geoLocation.GeoLocation;
import bubble.model.device.DeviceStatus;
import bubble.model.device.FlexRouter;
import bubble.service.device.FlexRouterInfo;
import bubble.service.device.FlexRouterProximityComparator;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class FlexRouterProximityComparatorTest {

    public static final GeoLocation GEO_NULL = new GeoLocation().setLat(null).setLon(null);
    public static final GeoLocation GEO_NEW_YORK = new GeoLocation().setLat("40.661").setLon("-73.944");
    public static final GeoLocation GEO_SINGAPORE = new GeoLocation().setLat("1.283333").setLon("103.833333");
    public static final GeoLocation GEO_LONDON = new GeoLocation().setLat("51.507222").setLon("-0.1275");
    public static final GeoLocation GEO_ATLANTA = new GeoLocation().setLat("33.755").setLon("-84.39");
    public static final GeoLocation GEO_CHICAGO = new GeoLocation().setLat("41.881944").setLon("-87.627778");

    private static FlexRouter router(int port) {
        return new FlexRouter().setPort(port).setIp("127.0.0."+port);
    }

    private static int deviceIp = 1;
    private static DeviceStatus device(GeoLocation geo) { return new DeviceStatus().setLocation(geo).setIp("127.2.2."+(deviceIp++)); }

    public static final FlexRouterInfo ROUTER_NEW_YORK = new FlexRouterInfo(router(200), device(GEO_NEW_YORK));
    public static final FlexRouterInfo ROUTER_SINGAPORE = new FlexRouterInfo(router(201), device(GEO_SINGAPORE));
    public static final FlexRouterInfo ROUTER_LONDON = new FlexRouterInfo(router(202), device(GEO_LONDON));
    public static final FlexRouterInfo ROUTER_ATLANTA = new FlexRouterInfo(router(203), device(GEO_ATLANTA));
    public static final FlexRouterInfo ROUTER_CHICAGO = new FlexRouterInfo(router(204), device(GEO_CHICAGO));
    public static final FlexRouterInfo ROUTER_NULL = new FlexRouterInfo(router(205), device(GEO_NULL));

    private static final List<FlexRouterInfo> TEST_INFO = Arrays.asList(
            ROUTER_NEW_YORK,
            ROUTER_SINGAPORE,
            ROUTER_LONDON,
            ROUTER_ATLANTA,
            ROUTER_CHICAGO,
            ROUTER_NULL);

    private static final List<FlexRouterInfo> EXPECTED_ATLANTA = Arrays.asList(
            ROUTER_ATLANTA,
            ROUTER_CHICAGO,
            ROUTER_NEW_YORK,
            ROUTER_LONDON,
            ROUTER_SINGAPORE,
            ROUTER_NULL);

    private static final List<FlexRouterInfo> EXPECTED_LONDON = Arrays.asList(
            ROUTER_LONDON,
            ROUTER_NEW_YORK,
            ROUTER_CHICAGO,
            ROUTER_ATLANTA,
            ROUTER_SINGAPORE,
            ROUTER_NULL);

    private static final List<FlexRouterInfo> EXPECTED_ATLANTA_PREFER_SINGAPORE = Arrays.asList(
            ROUTER_SINGAPORE,
            ROUTER_ATLANTA,
            ROUTER_CHICAGO,
            ROUTER_NEW_YORK,
            ROUTER_LONDON,
            ROUTER_NULL);

    @Test public void testProximitySortAtlanta () throws Exception {
        testProximitySort(GEO_ATLANTA, EXPECTED_ATLANTA);
    }

    @Test public void testProximitySortLondon () throws Exception {
        testProximitySort(GEO_LONDON, EXPECTED_LONDON);
    }

    @Test public void testProximitySortFromAtlantaWithPreferredIpInSingapore () throws Exception {
        testProximitySort(GEO_ATLANTA, EXPECTED_ATLANTA_PREFER_SINGAPORE, ROUTER_SINGAPORE.getVpnIp());
    }

    private void testProximitySort(GeoLocation geo, List<FlexRouterInfo> expected) throws Exception {
        testProximitySort(geo, expected, "127.3.3.3");
    }

    private void testProximitySort(GeoLocation geo, List<FlexRouterInfo> expected, String preferredIp) throws Exception {
        final List<FlexRouterInfo> test = new ArrayList<>(TEST_INFO);
        Collections.shuffle(test);
        final FlexRouterProximityComparator comparator = new FlexRouterProximityComparator(geo, preferredIp);
        final Set<FlexRouterInfo> sorted = new TreeSet<>(comparator);
        sorted.addAll(test);
        final List<FlexRouterInfo> actual = new ArrayList<>(sorted);
        assertEquals("wrong number of results", expected.size(), actual.size());
        for (int i=0; i<expected.size(); i++) {
            assertEquals("incorrect sort at index "+i, expected.get(i), actual.get(i));
        }
    }
}
