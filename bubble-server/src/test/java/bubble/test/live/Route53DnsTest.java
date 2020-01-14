package bubble.test.live;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.dns.route53.Route53DnsDriver;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import bubble.test.NetworkTestBase;
import org.junit.Test;

import java.util.Arrays;
import java.util.function.Predicate;

public class Route53DnsTest extends NetworkTestBase {

    @Override protected Class<? extends CloudServiceDriver> getPublicDnsDriver() { return Route53DnsDriver.class; }

    @Override protected Predicate<? super BubbleDomain> getDomainFilter(CloudService[] clouds) {
        // find domains that use the Route53 driver
        return bubbleDomain -> Arrays.stream(clouds)
                .filter(c -> c.usesDriver(getPublicDnsDriver()))
                .map(CloudService::getName)
                .anyMatch(n -> n.equals(bubbleDomain.getPublicDns()));
    }

    @Test public void testRoute53Dns () throws Exception { modelTest("network/dns_crud"); }
}
