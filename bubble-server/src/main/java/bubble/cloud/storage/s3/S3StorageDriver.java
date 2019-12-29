package bubble.cloud.storage.s3;

import bubble.cloud.storage.StorageCryptStream;
import bubble.cloud.storage.StorageServiceDriver;
import bubble.cloud.storage.StorageServiceDriverBase;
import bubble.cloud.storage.WriteRequest;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.StorageMetadata;
import bubble.notify.storage.StorageListing;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import fr.opensagres.xdocreport.core.io.IOUtils;
import lombok.Cleanup;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static bubble.model.cloud.StorageMetadata.*;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.CryptStream.BUFFER_SIZE;
import static org.cobbzilla.util.security.ShaUtil.sha256_file;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Slf4j
public class S3StorageDriver extends StorageServiceDriverBase<S3StorageConfig> {

    public static final long STALE_REQUEST_TIMEOUT = HOURS.toMillis(1);
    public static final long LISTING_TIMEOUT = MINUTES.toMillis(10);

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private RedisService redis;

    @Getter(lazy=true) private final AtomicReference<StorageCryptStream> cryptStream
            = new AtomicReference<>(new StorageCryptStream(getCredentials()));

    @Getter(lazy=true) private final AmazonS3 s3client = initS3Client();
    private AmazonS3 initS3Client() {
        final Regions region;
        final String regionName = config.getRegion().name();
        try {
            region = Regions.valueOf(regionName);
        } catch (Exception e) {
            return die("initS3Client: invalid region: "+ regionName);
        }
        return AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(getS3credentials())
                .build();
    }

    @Getter(lazy=true) private final AWSCredentialsProvider s3credentials = initS3Credentials();
    private AWSCredentialsProvider initS3Credentials() {
        return new AWSCredentialsProvider() {
            @Override public AWSCredentials getCredentials() {
                return new AWSCredentials() {
                    @Override public String getAWSAccessKeyId() {
                        final String key = credentials.getParam("AWS_ACCESS_KEY_ID");
                        return empty(key)
                                ? die("getAWSAccessKeyId: no AWS_ACCESS_KEY_ID defined in credentials for cloud: "+cloud.getUuid())
                                : key;
                    }
                    @Override public String getAWSSecretKey() {
                        final String key = credentials.getParam("AWS_SECRET_KEY");
                        return empty(key)
                                ? die("getAWSSecretKey: no AWS_SECRET_KEY defined in credentials for cloud: "+cloud.getUuid())
                                : key;
                    }
                };
            }
            @Override public void refresh() {}
        };
    }

    protected String s3path(BubbleNode from, String key) {
        return s3path_network(from.getNetwork()) + "/" + key;
    }

    protected String s3path_network(String network) {
        return config.getPrefix() + "/" + network;
    }

    @Override public boolean _exists(String fromNode, String key) throws IOException {
        try {
            final BubbleNode from = getFromNode(fromNode);
            return getS3client().doesObjectExist(config.getBucket(), s3path(from, key));
        } catch (Exception e) {
            // todo: catch a specialized exception that indicates "file not found", return null
            // otherwise, throw error
            log.warn("_exists: "+e);
            return false;
        }
    }

    @Override public StorageMetadata readMetadata(String fromNode, String key) {
        final BubbleNode from = getFromNode(fromNode);
        if (getS3client().doesObjectExist(config.getBucket(), s3path(from, key))) {
            final ObjectMetadata metadata = getObjectMetadata(from, key);
            if (metadata == null) return null;
            return StorageMetadata.fromMap(metadata.getUserMetadata());
        } else {
            log.warn("readMetadata: key does not exist: "+key);
            return null;
        }
    }

    protected ObjectMetadata getObjectMetadata(BubbleNode from, String key) {
        try {
            return getS3client().getObjectMetadata(config.getBucket(), s3path(from, key));
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                log.debug("getObjectMetadata: 404 Not Found: "+key);
            } else {
                log.error("getObjectMetadata: "+e);
            }
        } catch (Exception e) {
            log.error("getObjectMetadata: "+e);
        }
        return null;
    }

    @Override public InputStream _read(String fromNode, String key) throws IOException {
        try {
            final BubbleNode from = getFromNode(fromNode);
            final S3Object object = getS3client().getObject(config.getBucket(), s3path(from, key));
            final StorageMetadata metadata = readMetadata(fromNode, key);
            return getCryptStream().get().wrapRead(object.getObjectContent(), key, metadata);

        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                log.warn("_read: key not found: " + key);
                return null;
            }
            throw new IOException("_read: "+e, e);

        } catch (Exception e) {
            throw new IOException("_read: "+e);
        }
    }

    @Override protected boolean writeStorage(String fromNode, String key, WriteRequest writeRequest, StorageMetadata metadata) throws IOException {
        try {
            final BubbleNode from = getFromNode(fromNode);
            final AmazonS3 s3client = getS3client();

            if (!s3client.doesBucketExistV2(config.getBucket())) {
                s3client.createBucket(config.getBucket());
            }

            if (metadata == null) metadata = new StorageMetadata();
            if (!metadata.hasSha256()) metadata.setSha256(sha256_file(writeRequest.file));
            if (!metadata.hasCnode()) metadata.setCnode(from.getUuid());
            if (!metadata.hasMnode()) metadata.setMnode(from.getUuid());

            ObjectMetadata objectMetadata = getObjectMetadata(from, key);
            if (objectMetadata != null) {
                final Map<String, String> userMetadata = objectMetadata.getUserMetadata();
                final StorageMetadata remoteMeta = StorageMetadata.fromMap(userMetadata);
                if (remoteMeta.sameSha(metadata.getSha256()) && !metadata.isForceWrite()) {
                    log.info("_write: sha256 matches, not writing (but returning true): for key="+key);
                    return true;
                } else {
                    userMetadata.put(META_MTIME, ""+now());
                    userMetadata.put(META_MNODE, ""+now());
                    userMetadata.put(META_SHA256, metadata.getSha256());
                    userMetadata.put(META_NONCE, metadata.getOrCreateNonce());
                }
            } else {
                // remote metadata was null, initialize it
                objectMetadata = new ObjectMetadata();
                objectMetadata.setContentType(metadata.getContentType());
                objectMetadata.setUserMetadata(metadata.toMap());
            }

            // write encrypted file
            final File encFile = new File(writeRequest.tempDir, writeRequest.file.getName()+".enc");
            try (InputStream in = getCryptStream().get().wrapWrite(new FileInputStream(writeRequest.file), key, metadata)) {
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(encFile), BUFFER_SIZE)) {
                    IOUtils.copyLarge(in, out);
                }
                // update content-length or else write to S3 will fail
                objectMetadata.setContentLength(encFile.length());
            }
            final PutObjectRequest putRequest = new PutObjectRequest(config.getBucket(), s3path(from, key), encFile)
                    .withMetadata(objectMetadata);
            s3client.putObject(putRequest);
            return true;

        } catch (Exception e) {
            throw new IOException("_write: "+e);
        }
    }

    @Override public boolean delete(String fromNode, String uri) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        final AmazonS3 s3client = getS3client();
        final String key = s3path(from, uri);
        listAndDo(key, o -> s3client.deleteObject(config.getBucket(), o.getKey()));
        return true;
    }

    @Override public boolean deleteNetwork(String networkUuid) throws IOException {
        final AmazonS3 s3client = getS3client();
        final String key = s3path_network(networkUuid);
        listAndDo(key, o -> s3client.deleteObject(config.getBucket(), o.getKey()));
        return true;
    }

    @Getter(lazy=true) private final RedisService activeListings = redis.prefixNamespace(getClass().getSimpleName()+"_list");

    @NoArgsConstructor
    private static class ListingRequest {
        public String prefix;
        public ObjectListing objectListing;
        public ListingRequest(String prefix, ObjectListing request) {
            this.prefix = prefix;
            this.objectListing = request;
        }
        public long ctime = now();
        public long age() { return now() - ctime; }
    }

    @Override public StorageListing list(String fromNode, String prefix) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        final AmazonS3 s3client = getS3client();
        final String rootPrefix = s3path(from, "");
        final String key = s3path(from, prefix);
        final List<String> keys = new ArrayList<>();

        final ListObjectsRequest request = new ListObjectsRequest(config.getBucket(), key, null, null, config.getListFetchSize());

        final String listRequestId = randomUUID().toString();

        final ObjectListing listing = s3client.listObjects(request);
        listing.getObjectSummaries().forEach(o -> keys.add(o.getKey().substring(rootPrefix.length())));

        final ListingRequest listingRequest = new ListingRequest(key, listing);
        getActiveListings().set(listRequestId, json(listingRequest), EX, LISTING_TIMEOUT);

        return new StorageListing()
                .setListingId(listRequestId)
                .setKeys(keys.toArray(new String[0]))
                .setTruncated(listing.isTruncated());
    }

    @Override public StorageListing listNext(String fromNode, String listingId) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        final AmazonS3 s3client = getS3client();
        final String rootPrefix = s3path(from, "");
        final List<String> keys = new ArrayList<>();
        final RedisService activeListings = getActiveListings();
        final String json = activeListings.get(listingId);
        if (json == null) return die("listNext: listingId not found: "+listingId);
        final ListingRequest listingRequest = json(json, ListingRequest.class);

        listingRequest.objectListing = s3client.listNextBatchOfObjects(listingRequest.objectListing);
        listingRequest.objectListing.getObjectSummaries().forEach(o -> keys.add(o.getKey().substring(rootPrefix.length())));
        activeListings.set(listingId, json(listingRequest), EX, LISTING_TIMEOUT);

        return new StorageListing()
                .setListingId(listingId)
                .setKeys(keys.toArray(new String[0]))
                .setTruncated(listingRequest.objectListing.isTruncated());
    }

    @Override public boolean rekey(String fromNode, CloudService newCloud) throws IOException {
        final BubbleNode from = getFromNode(fromNode);
        final String prefix = s3path(from, "");
        final AmazonS3 s3client = getS3client();
        final StorageServiceDriver newStorage = newCloud.getStorageDriver(configuration);

        // create new crypt stream
        final StorageCryptStream currentCryptStream = getCryptStream().get();

        @Cleanup final TempDir temp = new TempDir();
        listAndDo(prefix, o -> {
            try {
                final S3Object object = s3client.getObject(config.getBucket(), o.getKey());
                final ObjectMetadata objectMetadata =  s3client.getObjectMetadata(config.getBucket(), o.getKey());
                final StorageMetadata metadata = StorageMetadata.fromMap(objectMetadata.getUserMetadata());
                final InputStream in = currentCryptStream.wrapRead(object.getObjectContent(), o.getKey().substring(prefix.length()), metadata);

                // store in temp file
                @Cleanup("delete") final File t = new File(temp, sha256_hex(o.getKey())+"_"+now());
                FileUtil.toFile(t, in);

                // use new storage to write, will use new key
                metadata.setNonce(null);
                try (InputStream encIn = new FileInputStream(t)) {
                    newStorage.write(fromNode, o.getKey().substring(prefix.length()), encIn, metadata.setForceWrite(true));
                }

            } catch (Exception e) {
                die("rekey: "+e);
            }
        });

        return true;
    }

    protected interface KeyVisitor { void visit(S3ObjectSummary objectSummary); }

    protected void listAndDo(String prefix, KeyVisitor visitor) {
        final ListObjectsRequest request = new ListObjectsRequest(config.getBucket(), prefix, null, null, config.getListFetchSize());
        final AmazonS3 s3client = getS3client();
        ObjectListing listing = s3client.listObjects(request);
        do {
            final List<S3ObjectSummary> objectSummaries = listing.getObjectSummaries();
            log.info("listAndDo: processing "+objectSummaries.size()+" objects...");
            objectSummaries.forEach(visitor::visit);
            if (!listing.isTruncated()) break;
            log.info("listAndDo: listing was truncated, fetching "+config.getListFetchSize()+" more objects...");
            listing = s3client.listNextBatchOfObjects(listing);
        } while (true);
    }

    protected BubbleNode getFromNode(String fromNode) {
        final BubbleNode from = nodeDAO.findByUuid(fromNode);
        return from != null ? from : die("fromNode not found: "+fromNode);
    }

    @Override public boolean canWrite(String fromNode, String toNode, String key) {
        final BubbleNode from = nodeDAO.findByUuid(fromNode);
        if (from == null) return false;

        // todo: enforce GB storage quota per network
        return true;
    }

}
