/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.ec2;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.ComputeServiceDriverBase;
import bubble.cloud.compute.OsImage;
import bubble.cloud.compute.PackerImage;
import bubble.cloud.shared.aws.BubbleAwsCredentialsProvider;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.daemon.AwaitResult;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static bubble.model.cloud.BubbleNode.TAG_INSTANCE_ID;
import static bubble.model.cloud.BubbleNode.TAG_TEST;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.daemon.Await.awaitAll;
import static org.cobbzilla.util.daemon.DaemonThreadFactory.fixedPool;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class AmazonEC2Driver extends ComputeServiceDriverBase {

    public static final long PARALLEL_TIMEOUT =  TimeUnit.SECONDS.toMillis(20);
    public static final String TAG_CLOUD_UUID = "cloudUUID";
    public static final String TAG_NODE_UUID = "nodeUUID";
    public static final String KEY_NAME_PREFIX = "keyName_";

    public static final String VPC_CIDR_BLOCK = "10.0.0.0/16";
    public static final String TAG_BUBBLE_CLASS = "BUBBLE_CLASS";
    public static final String TAG_BUBBLE_CLASS_PACKER_VPC = "BUBBLE_PACKER_VPC";
    public static final String TAG_BUBBLE_CLASS_PACKER_SUBNET = "BUBBLE_PACKER_SUBNET";

    public static final Filter[] VPC_FILTERS = new Filter[]{
            new Filter("tag:" + TAG_BUBBLE_CLASS, new SingletonList<>(TAG_BUBBLE_CLASS_PACKER_VPC))
    };
    public static final Filter[] SUBNET_FILTERS = new Filter[]{
            new Filter("tag:" + TAG_BUBBLE_CLASS, new SingletonList<>(TAG_BUBBLE_CLASS_PACKER_SUBNET))
    };
    public static final String IP4_CIDR_ALL = "0.0.0.0/0";
    public static final String IP6_CIDR_ALL = "::/0";

    @Autowired private RedisService redis;

    @Getter(lazy=true) private final String securityGroup = config.getConfig("securityGroup");

    @Getter(lazy=true) private final AWSCredentialsProvider ec2credentials = new BubbleAwsCredentialsProvider(cloud, getCredentials());
    @Getter(lazy=true) private final Map<String, AmazonEC2> ec2ClientMap = initClientMap();

    private Map<String, AmazonEC2> initClientMap() {
        final Map<String, AmazonEC2> clients = new HashMap<>();
        final AWSCredentialsProvider ec2credentials = getEc2credentials();
        for (CloudRegion region : getCloudRegions()) {
            final AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
                    .withRegion(Regions.fromName(region.getInternalName()))
                    .withCredentials(ec2credentials).build();
            clients.put(region.getInternalName(), ec2);
        }
        return clients;
    }

    @Getter(lazy=true) private final List<CloudRegion> cloudRegions = driverConfig("regions");
    @Getter(lazy=true) private final List<ComputeNodeSize> cloudSizes = driverConfig("sizes");

    private <T> List<T> driverConfig(String field) { return Arrays.asList((T[]) ReflectionUtil.get(config, field)); }

    @Getter(lazy=true) private final RedisService imageCache = redis.prefixNamespace(getClass().getSimpleName()+".ec2_ubuntu_image");
    public static final long IMAGE_CACHE_TIME = DAYS.toSeconds(30);

    @Getter(lazy=true) private final ExecutorService perRegionExecutor = fixedPool(getRegions().size(), "AmazonEC2Driver.perRegionExecutor");

    @Getter(lazy=true) private final List<OsImage> cloudOsImages = initImages();
    private List<OsImage> initImages() {
        final ArrayList<Filter> filters = new ArrayList<>();
        filters.add(new Filter("root-device-type", new SingletonList<>("ebs")));
        filters.add(new Filter("state", new SingletonList<>("available")));
        filters.add(new Filter("name", new SingletonList<>(config.getOs())));
        final List<Future<?>> futures = new ArrayList<>();
        for (CloudRegion region : getCloudRegions()) {
            futures.add(getPerRegionExecutor().submit(() -> {
                final String internalName = region.getInternalName();
                final String cachedJson = getImageCache().get(internalName);
                if (cachedJson != null) {
                    return json(cachedJson, OsImage.class);
                }
                final AmazonEC2 ec2 = getEc2Client(region);
                final DescribeImagesRequest imageRequest = new DescribeImagesRequest().withFilters(filters);
                final DescribeImagesResult imagesResult = ec2.describeImages(imageRequest);
                if (empty(imagesResult.getImages())) die("no images found");
                final List<Image> sorted = new ArrayList<>(imagesResult.getImages());
                sorted.sort(comparing(Image::getCreationDate));
                final Image first = sorted.get(0);
                final OsImage image = new OsImage()
                        .setName(first.getName())
                        .setId(first.getImageId())
                        .setRegion(internalName);
                getImageCache().set(internalName, json(image), EX, IMAGE_CACHE_TIME);
                return image;
            }));
        }
        final AwaitResult<Object> awaitResult = awaitAll(futures, PARALLEL_TIMEOUT);
        if (!awaitResult.allSucceeded()) return die("initImages: "+awaitResult.getFailures().values());
        return awaitResult.getSuccesses().values().stream().map(o -> (OsImage) o).collect(Collectors.toList());
    }

    @Getter(lazy=true) private final Map<String, OsImage> imagesByRegion = getCloudOsImages().stream()
            .collect(Collectors.toMap(OsImage::getRegion, Function.identity()));

    public Map<String, Object> getPackerRegionContext(CloudRegion region) {
        final Map<String, Object> ctx = new HashMap<>();
        final String internalName = region.getInternalName();

        final Map<String, OsImage> imagesByRegion = getImagesByRegion();
        if (empty(imagesByRegion)) return die("getPackerRegionContext: getImagesByRegion returned empty map");
        final OsImage imageForRegion = imagesByRegion.get(internalName);
        if (imageForRegion == null) return die("getPackerRegionContext: no image found for region: "+internalName);
        ctx.put("imageForRegion", imageForRegion);

        final Map<String, Vpc> vpcsByRegion = getVpcsByRegion();
        if (empty(vpcsByRegion)) return die("getPackerRegionContext: getVpcsByRegion returned empty map");
        final Vpc vpc = vpcsByRegion.get(internalName);
        if (vpc == null) return die("getPackerRegionContext: no vpc found for region: "+internalName);
        ctx.put("vpcForRegion", vpc);

        final Map<String, Map<String, Subnet>> subnetsByRegion = getSubnetsByRegion();
        if (empty(subnetsByRegion)) return die("getPackerRegionContext: getSubnetsByRegion returned empty map");
        final Map<String, Subnet> subnets = subnetsByRegion.get(internalName);
        if (subnets == null) return die("getPackerRegionContext: no subnets found for region: "+internalName);

        // use the last az/subnet in the region
        final List<String> azNames = new ArrayList<>(new TreeSet<>(subnets.keySet()));
        final String az = azNames.get(azNames.size() - 1);
        ctx.put("availabilityZoneForRegion", az);
        ctx.put("subnetForRegion", subnets.get(az));

        return ctx;
    }

    @Override protected OsImage initOs() { return null; }

    @Override public List<PackerImage> getAllPackerImages() {
        final List<Future<?>> futures = new ArrayList<>();
        for (CloudRegion region : getRegions()) {
            futures.add(getPerRegionExecutor().submit(() -> getPackerImagesForRegion(region.getInternalName())));
        }
        final AwaitResult<Object> awaitResult = awaitAll(futures, PARALLEL_TIMEOUT);
        if (!awaitResult.allSucceeded()) return die("initImages: "+awaitResult.getFailures().values());

        final List<PackerImage> images = new ArrayList<>();
        for (Object o : awaitResult.getSuccesses().values()) images.addAll((List<PackerImage>) o);
        return images;
    }

    @Override public List<PackerImage> getPackerImagesForRegion(String region) {
        final ArrayList<Filter> filters = new ArrayList<>();
        filters.add(new Filter("root-device-type", new SingletonList<>("ebs")));
        filters.add(new Filter("state", new SingletonList<>("available")));
        filters.add(new Filter("name", new SingletonList<>("packer_*_"+packerService.getPackerPublicKeyHash()+"_"+configuration.getShortVersion()+"_*")));
        final AmazonEC2 ec2 = getEc2Client(region);
        final DescribeImagesRequest imageRequest = new DescribeImagesRequest().withFilters(filters);
        final DescribeImagesResult imagesResult = ec2.describeImages(imageRequest);
        if (empty(imagesResult.getImages())) return Collections.emptyList();
        return imagesResult.getImages().stream().map(i -> new PackerImage()
                .setName(i.getName())
                .setId(i.getImageId())
                .setRegions(new CloudRegion[]{getRegion(region)})
        ).collect(Collectors.toList());
    }

    @Override public int getPackerParallelBuilds() { return getRegions().size(); }

    private AmazonEC2 getEc2Client(CloudRegion region) { return getEc2Client(region.getInternalName()); }

    private AmazonEC2 getEc2Client(String internalName) {
        final AmazonEC2 ec2 = getEc2ClientMap().get(internalName);
        if (ec2 == null) return die("getEc2Client: invalid region: "+internalName);
        return ec2;
    }

    private final ExpirationMap<String, Vpc> vpcCache = new ExpirationMap<>(DAYS.toMillis(1));

    public Map<String, Vpc> getVpcsByRegion() {
        final List<Future<?>> futures = new ArrayList<>();
        final Map<String, Vpc> vpcsByRegion = new ConcurrentHashMap<>();
        for (CloudRegion region : getRegions()) {
            final String internalName = region.getInternalName();
            final AmazonEC2 ec2 = getEc2Client(internalName);
            futures.add(getPerRegionExecutor().submit(() -> {
                Vpc vpc = vpcCache.get(internalName);
                if (vpc != null) {
                    vpcsByRegion.put(internalName, vpc);
                    return;
                }
                final DescribeVpcsRequest describeRequest = new DescribeVpcsRequest().withFilters(VPC_FILTERS);
                final DescribeVpcsResult describeVpcsResult = ec2.describeVpcs(describeRequest);
                if (empty(describeVpcsResult.getVpcs())) {
                    final CreateVpcRequest createRequest = new CreateVpcRequest()
                            .withCidrBlock(VPC_CIDR_BLOCK)
                            .withAmazonProvidedIpv6CidrBlock(true);
                    final CreateVpcResult createVpcResult;
                    try {
                        createVpcResult = ec2.createVpc(createRequest);
                    } catch (Exception e) {
                        die("createVpcs("+internalName+"): "+e, e);
                        return;
                    }

                    vpc = createVpcResult.getVpc();
                    ec2.createTags(new CreateTagsRequest()
                            .withResources(vpc.getVpcId())
                            .withTags(new Tag(TAG_BUBBLE_CLASS, TAG_BUBBLE_CLASS_PACKER_VPC)));
                } else {
                    final List<Vpc> vpcs = describeVpcsResult.getVpcs();
                    if (vpcs.size() > 1) die("createVpcs: more than 1 vpc found for region: "+internalName);
                    vpc = vpcs.get(0);
                }
                vpcsByRegion.put(internalName, vpc);
                vpcCache.put(internalName, vpc);
            }));
        }
        final AwaitResult awaitResult = awaitAll(futures, PARALLEL_TIMEOUT);
        if (!awaitResult.allSucceeded()) {
            return die("createVpcs: "+awaitResult.getFailures().values());
        }
        return vpcsByRegion;
    }

    private final ExpirationMap<String, List<String>> azCache = new ExpirationMap<>(DAYS.toMillis(30));
    private final ExpirationMap<String, Map<String, Subnet>> subnetCache = new ExpirationMap<>(DAYS.toMillis(1));

    public Map<String, Map<String, Subnet>> getSubnetsByRegion() {
        final Map<String, Vpc> vpcsByRegion = getVpcsByRegion();
        final List<Future<?>> futures = new ArrayList<>();
        final Map<String, Map<String, Subnet>> subnetsByRegion = new ConcurrentHashMap<>();
        for (CloudRegion region : getRegions()) {
            final String internalName = region.getInternalName();
            final AmazonEC2 ec2 = getEc2Client(internalName);
            futures.add(getPerRegionExecutor().submit(() -> {
                final List<String> availZones = azCache.computeIfAbsent(internalName, k -> {
                    final DescribeAvailabilityZonesResult azResult = ec2.describeAvailabilityZones(new DescribeAvailabilityZonesRequest().withFilters(new Filter().withName("region-name").withValues(k)));
                    return azResult.getAvailabilityZones().stream().map(AvailabilityZone::getZoneName).collect(Collectors.toList());
                });

                Map<String, Subnet> subnets = subnetCache.get(internalName);
                if (subnets == null) {
                    subnets = new HashMap<>();
                } else if (!empty(subnets) && subnets.size() == availZones.size()) {
                    subnetsByRegion.put(internalName, subnets);
                    return;
                }

                final DescribeSubnetsResult subnetsResult = ec2.describeSubnets(new DescribeSubnetsRequest().withFilters(SUBNET_FILTERS));
                for (String az : availZones) {
                    final Vpc vpc = vpcsByRegion.get(internalName);
                    Subnet subnet = subnetsResult.getSubnets().stream().filter(sn -> sn.getAvailabilityZone().equals(az)).findFirst().orElse(null);
                    final String subnetId = subnet.getSubnetId();
                    if (subnet == null) {
                        final CreateSubnetResult createSubnetResult;
                        try {
                            final CreateSubnetRequest createRequest = new CreateSubnetRequest()
                                    .withVpcId(vpc.getVpcId())
                                    .withAvailabilityZone(az)
                                    .withCidrBlock(ip4Slash28(vpc, az));
                            createSubnetResult = ec2.createSubnet(createRequest);
                        } catch (Exception e) {
                            die("createSubnets("+internalName+"/"+az+"): " + e, e);
                            return;
                        }

                        subnet = createSubnetResult.getSubnet();
                        ec2.createTags(new CreateTagsRequest()
                                .withResources(subnetId)
                                .withTags(new Tag(TAG_BUBBLE_CLASS, TAG_BUBBLE_CLASS_PACKER_SUBNET)));
                    }

                    final DescribeInternetGatewaysResult gatewaysResult = ec2.describeInternetGateways();
                    final InternetGateway gateway = gatewaysResult.getInternetGateways().stream()
                            .filter(g -> g.getAttachments().stream().anyMatch(a -> a.getVpcId().equals(vpc.getVpcId())))
                            .findFirst()
                            .or(() -> Optional.ofNullable(ec2.createInternetGateway().getInternetGateway()))
                            .get();
                    final String gatewayId = gateway.getInternetGatewayId();
                    if (gateway.getAttachments().stream().noneMatch(a -> a.getVpcId().equals(vpc.getVpcId()))) {
                        ec2.attachInternetGateway(new AttachInternetGatewayRequest()
                                .withVpcId(vpc.getVpcId())
                                .withInternetGatewayId(gatewayId));
                    }

                    final DescribeRouteTablesResult routes = ec2.describeRouteTables();
                    final RouteTable routeTable = routes.getRouteTables().stream().filter(rt -> rt.getVpcId().equals(vpc.getVpcId())).findFirst().orElse(null);
                    if (routeTable == null) {
                        die("createSubnets("+internalName+"/"+az+"): no route table found for vpc "+vpc.getVpcId());
                        return;
                    }
                    final List<RouteTableAssociation> associations = routeTable.getAssociations();
                    try {
                        if (empty(associations) || associations.stream()
                                .noneMatch(a -> a.getSubnetId() != null && a.getSubnetId().equals(subnetId))) {
                            ec2.associateRouteTable(new AssociateRouteTableRequest()
                                    .withSubnetId(subnetId)
                                    .withRouteTableId(routeTable.getRouteTableId()));
                        }
                        if (routeTable.getRoutes().stream()
                                .noneMatch(r -> r.getDestinationCidrBlock() != null && r.getDestinationCidrBlock().equals(IP4_CIDR_ALL))) {
                            ec2.createRoute(new CreateRouteRequest()
                                    .withDestinationCidrBlock(IP4_CIDR_ALL)
                                    .withGatewayId(gatewayId)
                                    .withRouteTableId(routeTable.getRouteTableId()));
                        }
                        if (routeTable.getRoutes().stream()
                                .noneMatch(r -> r.getDestinationIpv6CidrBlock() != null && r.getDestinationIpv6CidrBlock().equals(IP6_CIDR_ALL))) {
                            ec2.createRoute(new CreateRouteRequest()
                                    .withDestinationIpv6CidrBlock(IP6_CIDR_ALL)
                                    .withGatewayId(gatewayId)
                                    .withRouteTableId(routeTable.getRouteTableId()));
                        }
                    } catch (Exception e) {
                        die("createSubnets("+internalName+"/"+az+"): error adding gateway route vpc "+vpc.getVpcId());
                        return;
                    }

                    subnets.put(az, subnet);
                }
                subnetsByRegion.put(internalName, subnets);
                subnetCache.put(internalName, subnets);
            }));
        }
        final AwaitResult awaitResult = awaitAll(futures, PARALLEL_TIMEOUT);
        if (!awaitResult.allSucceeded()) {
            return die("createSubnets: "+awaitResult.getFailures().values());
        }
        return subnetsByRegion;
    }

    private String ip4Slash28(Vpc vpc, String az) {
        final char zoneId = az.toLowerCase().charAt(az.length()-1);
        final int subnetNumber = zoneId - 'a';
        final String[] parts = vpc.getCidrBlock().split("[./]");
        final String subnet = parts[0] + "." + parts[1] + "." + parts[2] + "." + (subnetNumber * 16) + "/28";
        return subnet;
    }

    @Override public List<BubbleNode> listNodes() throws IOException {
        final List<Future<?>> listNodeJobs = new ArrayList<>();
        for (final AmazonEC2 ec2 : getEc2ClientMap().values()) {
            listNodeJobs.add(getPerRegionExecutor().submit(new ListNodesHelper(ec2)));
        }
        final AwaitResult awaitResult = awaitAll(listNodeJobs, PARALLEL_TIMEOUT);
        if (!awaitResult.allSucceeded()) {
            return die("listNodes: error listing nodes: "+awaitResult.getFailures().values());
        }
        final List<BubbleNode> nodes = new ArrayList<>();
        for (Object o : awaitResult.getSuccesses().values()) nodes.addAll((List<BubbleNode>) o);
        return nodes;
    }

    @AllArgsConstructor
    private class ListNodesHelper implements Callable<List<BubbleNode>> {
        private AmazonEC2 ec2;

        @Override public List<BubbleNode> call() {
            final List<BubbleNode> nodes = new ArrayList<>();
            final DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
                    .withFilters(
                            new Filter("instance.group-id").withValues(getSecurityGroup()),
                            new Filter("tag:" + TAG_CLOUD_UUID).withValues(cloud.getUuid()),
                            new Filter("instance-state-name").withValues("running", "pending")
                    );

            final DescribeInstancesResult result = ec2.describeInstances(describeInstancesRequest);
            if (result.getSdkHttpMetadata().getHttpStatusCode() == OK) {
                for (Reservation reservation : result.getReservations()) {
                    for (Instance instance : reservation.getInstances()) {
                        final String instanceId = instance.getInstanceId();
                        final String ip4 = instance.getPrivateIpAddress();
                        final String ip6 = instance.getPublicIpAddress();
                        nodes.add(new BubbleNode().setIp4(ip4).setIp6(ip6).setTag(TAG_INSTANCE_ID, instanceId));
                    }
                }
            } else {
                return die("list: error describe EC2 instances: "
                        + result.getSdkHttpMetadata().getHttpStatusCode()
                        + ": " + result.getSdkHttpMetadata().getAllHttpHeaders());
            }
            return nodes;
        }
    }

    @Override public BubbleNode start(BubbleNode node) throws Exception {
        final ComputeNodeSize size = config.getSize(node.getSize());
        final AmazonEC2 ec2Client = getEc2Client(node.getRegion());
        final PackerImage packerImage = getOrCreatePackerImage(node);

        final EbsBlockDevice ebs = null; // todo
        final RunInstancesRequest runInstancesRequest = new RunInstancesRequest().withImageId(config.getConfig("imageId"))
                .withInstanceType(size.getInternalName())
                .withImageId(packerImage.getId())
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(KEY_NAME_PREFIX+node.getUuid())
                .withBlockDeviceMappings(new BlockDeviceMapping().withEbs(ebs))
                .withNetworkInterfaces(new InstanceNetworkInterfaceSpecification()
                        .withAssociatePublicIpAddress(true)
                        .withDeviceIndex(0)
                        .withIpv6AddressCount(1)
                        .withGroups(getSecurityGroup()));

        final RunInstancesResult runInstancesResult = ec2Client.runInstances(runInstancesRequest);

        if (runInstancesResult.getSdkHttpMetadata().getHttpStatusCode() != OK) {
            return die("start: error running instance");
        }

        final String instanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();

        node.setState(BubbleNodeState.booting);
        nodeDAO.update(node);

        // Describe instances to check run instance result and get IP addresses
        final DescribeInstancesResult result = ec2Client.describeInstances(
                new DescribeInstancesRequest().withInstanceIds(instanceId));

        if (result.getSdkHttpMetadata().getHttpStatusCode() != OK) {
            return die("start: error describing instance");
        }
        for (final Reservation reservation : result.getReservations()) {
            for (final Instance i : reservation.getInstances()) {
                if (i.getInstanceId().equals(instanceId)) {
                    final String ip4 = i.getPublicIpAddress();
                    if (ip4 != null && ip4.length() > 0 && !ip4.equals("0.0.0.0")) {
                        node.setIp4(ip4);
                        nodeDAO.update(node);
                    }
                    final String ip6 = i.getPublicIpAddress();
                    if (ip6 != null && ip6.length() > 0) {
                        node.setIp6(ip6);
                        nodeDAO.update(node);
                    }
                    break;
                }
            }
        }

        // Setting up the tags for the instance
        final List<Tag> tags = new ArrayList<>();
        tags.add(new Tag(TAG_NODE_UUID, node.getUuid()));
        tags.add(new Tag(TAG_CLOUD_UUID, cloud.getUuid()));
        if (configuration.testMode()) {
            final String testTag = configuration.getEnvironment().get("TEST_TAG_CLOUD");
            if (empty(testTag)) return die("TEST_TAG_CLOUD env var is not defined or is empty");
            tags.add(new Tag(TAG_TEST, testTag));
        }
        try {
            final CreateTagsResult tagsResult = ec2Client.createTags(new CreateTagsRequest()
                    .withResources(instanceId)
                    .withTags(tags));
            if (tagsResult.getSdkHttpMetadata().getHttpStatusCode() != OK) {
                return die("start: error setting tags on instance");
            }
        } catch (AmazonServiceException e) {
            return die("start: error setting tags on instance: "+shortError(e), e);
        }

        return node;
    }

    @Override public BubbleNode cleanupStart(BubbleNode node) { return node; }

    @Override public BubbleNode stop(BubbleNode node) throws Exception {
        if (!node.hasTag(TAG_INSTANCE_ID)) {
            throw notFoundEx(node.id());
        }
        final String instanceID = node.getTag(TAG_INSTANCE_ID);

        //Stop EC2 Instance
        final StopInstancesRequest stopInstancesRequest = new StopInstancesRequest()
                .withInstanceIds(instanceID);

        final AmazonEC2 ec2Client = getEc2Client(node.getRegion());
        try {
            ec2Client.stopInstances(stopInstancesRequest);
        } catch (AmazonServiceException e) {
            log.warn("stop: error stopping instance: " + instanceID + e.getErrorMessage() + e.getErrorCode());
        }
        return node;
    }

    @Override public BubbleNode status(BubbleNode node) throws Exception {
        final List<BubbleNode> found = listNodes();
        if (found.isEmpty()) {
            return node.setState(BubbleNodeState.stopped);
        } else {
            boolean isFound = false;
            for (final BubbleNode foundNode : found) {
                if (foundNode.getTag(TAG_INSTANCE_ID).equals(node.getTag(TAG_INSTANCE_ID))) {
                    node.setState(foundNode.getState());
                    isFound = true;
                    break;
                }
            }
            if (!isFound) node.setState(BubbleNodeState.stopped);
        }
        return node;
    }

}
