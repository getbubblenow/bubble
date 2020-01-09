package bubble.test;

import bubble.cloud.CloudServiceType;
import bubble.cloud.storage.s3.S3StorageConfig;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.RekeyRequest;
import bubble.model.cloud.StorageMetadata;
import bubble.notify.storage.StorageListing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.HttpHeaders;
import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.security.CryptoUtil;
import org.cobbzilla.wizard.api.NotFoundException;
import org.junit.Test;

import java.io.*;
import java.util.List;

import static bubble.ApiConstants.*;
import static bubble.cloud.storage.StorageCryptStream.MIN_DISTINCT_LENGTH;
import static bubble.cloud.storage.StorageCryptStream.MIN_KEY_LENGTH;
import static bubble.model.cloud.CloudCredentials.PARAM_KEY;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpMethods.DELETE;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.junit.Assert.*;

@Slf4j
public class S3StorageTest extends NetworkTestBase {

    public static final int NUM_FILES_IN_DIR = 13;
    public static final int LIST_FETCH_SIZE = 5;

    public static final String S3_CLOUD_NAME = "S3Storage";
    public static final String REKEY_S3_CLOUD = "RekeyS3Cloud";

    public static String comment = "not running";

    @Test public void testS3Storage () throws Exception {

        // sample data
        final String testData = randomAlphanumeric(1000);
        final String path1 = "test_file_" + randomAlphanumeric(10);
        final String storageUri = ME_ENDPOINT + EP_CLOUDS + "/" + S3_CLOUD_NAME + EP_STORAGE;

        comment = "update S3 config, set listFetchSize to a small number";
        final CloudServiceDAO cloudDAO = getConfiguration().getBean(CloudServiceDAO.class);
        final List<CloudService> clouds = cloudDAO.findByAccountAndType(getConfiguration().getThisNode().getAccount(), CloudServiceType.storage);
        final CloudService s3cloud = clouds.stream()
                .filter(c -> c.getName().equals(S3_CLOUD_NAME))
                .findFirst()
                .orElse(null);
        assertNotNull("No S3 cloud found", s3cloud);
        final S3StorageConfig config = json(s3cloud.getDriverConfigJson(), S3StorageConfig.class);
        cloudDAO.update(s3cloud.setDriverConfigJson(json(config.setListFetchSize(LIST_FETCH_SIZE))));

        comment = "start with empty storage";
        final HttpRequestBean predelete = new HttpRequestBean()
                .setMethod(DELETE)
                .setUri(storageUri+EP_DELETE+"/*");
        final HttpResponseBean predeleteResp = getApi().getResponse(predelete);
        assertTrue(predeleteResp.isOk());

        comment = "write a file to s3 storage with precalculated sha";
        final HttpRequestBean writeRequest1 = new HttpRequestBean()
                .setMethod(HttpMethods.POST)
                .setUri(storageUri+EP_WRITE+"/"+path1+"?sha256="+sha256_hex(testData))
                .setEntity(path1)
                .setEntityInputStream(new ByteArrayInputStream(testData.getBytes()));
        final HttpResponseBean writeResponse1 = getApi().getResponse(writeRequest1);
        assertTrue(writeResponse1.isOk());

        comment = "read file from s3";
        final HttpRequestBean readRequest1 = new HttpRequestBean(storageUri + EP_READ + "/" + path1);
        final String savedData = getApi().getStreamedString(readRequest1);
        assertEquals("data was not saved correctly", testData, savedData);

        comment = "read metadata from s3";
        final HttpRequestBean metaRequest = new HttpRequestBean()
                .setUri(storageUri+EP_READ_METADATA+"/"+path1);
        final HttpResponseBean metaResponse1 = getApi().getResponse(metaRequest);
        assertTrue(metaResponse1.isOk());

        final StorageMetadata meta1 = metaResponse1.getEntity(StorageMetadata.class);
        assertEquals("wrong node", getConfiguration().getThisNode().getUuid(), meta1.getCnode());

        comment = "write same file without sha, should not actually write since the file is the same";
        final HttpRequestBean writeRequest2 = new HttpRequestBean()
                .setMethod(HttpMethods.POST)
                .setUri(storageUri+EP_WRITE+"/"+path1)
                .setEntity(path1)
                .setEntityInputStream(new ByteArrayInputStream(testData.getBytes()));
        final HttpResponseBean writeResponse2 = getApi().getResponse(writeRequest2);
        assertTrue(writeResponse2.isOk());

        comment = "read file from s3 again, mtime is unchanged";
        final HttpResponseBean metaResponse2 = getApi().getResponse(metaRequest);
        assertTrue(metaResponse2.isOk());
        final StorageMetadata meta2 = metaResponse2.getEntity(StorageMetadata.class);
        assertEquals("wrong node", getConfiguration().getThisNode().getUuid(), meta2.getCnode());
        assertEquals("ctime changed", meta1.getCtime(), meta2.getCtime());
        assertEquals("mtime changed", meta1.getMtime(), meta2.getMtime());

        comment = "write same key with ~40MB of different content, should update mtime";
        final File testData2 = FileUtil.temp(".tmp");
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(testData2))) {
            for (int i=0; i<5000; i++) {
                out.write(RandomUtils.nextBytes(8192));
            }
        }
        try (InputStream in = new FileInputStream(testData2)) {
            final HttpRequestBean writeRequest3 = new HttpRequestBean()
                    .setMethod(HttpMethods.POST)
                    .setUri(storageUri + EP_WRITE + "/" + path1)
                    .setEntity(path1)
                    .setEntityInputStream(in);
            final HttpResponseBean writeResponse3 = getApi().getResponse(writeRequest3);
            assertTrue(writeResponse3.isOk());
        }

        comment = "read file from s3 again, mtime is unchanged";
        final HttpResponseBean metaResponse3 = getApi().getResponse(metaRequest);
        assertTrue(metaResponse3.isOk());
        final StorageMetadata meta3 = metaResponse3.getEntity(StorageMetadata.class);
        assertEquals("wrong node", getConfiguration().getThisNode().getUuid(), meta2.getCnode());
        assertEquals("ctime changed", meta1.getCtime(), meta2.getCtime());
        assertTrue("mtime unchanged", meta3.getMtime() > meta2.getMtime());

        comment = "delete test file";
        final HttpRequestBean del1 = new HttpRequestBean()
                .setMethod(DELETE)
                .setUri(storageUri+EP_DELETE+"/"+path1);
        final HttpResponseBean del1resp = getApi().getResponse(del1);
        assertTrue(del1resp.isOk());

        comment = "create a few files in a directory";
        for (int i=0; i<NUM_FILES_IN_DIR; i++) {
            final HttpRequestBean write = new HttpRequestBean()
                    .setMethod(HttpMethods.POST)
                    .setUri(storageUri+EP_WRITE+"/dir1/"+path1+i+"?sha256="+sha256_hex(testData))
                    .setEntity("dir1/"+path1+i)
                    .setEntityInputStream(new ByteArrayInputStream(testData.getBytes()));
            final HttpResponseBean resp = getApi().getResponse(write);
            assertTrue(resp.isOk());
        }

        comment = "make sure they are all there";
        for (int i=0; i<NUM_FILES_IN_DIR; i++) {
            final HttpRequestBean read = new HttpRequestBean(storageUri+EP_READ+"/dir1/"+path1+i);
            final String checkData = getApi().getStreamedString(read);
            assertEquals("data was not saved correctly", testData, checkData);
        }

        comment = "list files, we should see first batch";
        final HttpRequestBean list1request = new HttpRequestBean(storageUri+EP_LIST+"/dir1/"+path1);
        final HttpResponseBean list1response = getApi().getResponse(list1request);
        assertTrue(list1response.isOk());
        StorageListing listing = json(list1response.getEntityString(), StorageListing.class);
        assertEquals("expected max batch size for batch 1", LIST_FETCH_SIZE, listing.getKeys().length);
        assertTrue("expected listing1 to be truncated", listing.isTruncated());

        comment = "list second batch of files, still truncated";
        final HttpRequestBean list2request = new HttpRequestBean(storageUri+EP_LIST_NEXT+"/"+listing.getListingId());
        final HttpResponseBean list2response = getApi().getResponse(list2request);
        assertTrue(list2response.isOk());
        listing = json(list2response.getEntityString(), StorageListing.class);
        assertEquals("expected max batch size for batch 2", LIST_FETCH_SIZE, listing.getKeys().length);
        assertTrue("expected listing2 to be truncated", listing.isTruncated());

        comment = "list third batch of files, last batch, not truncated";
        final HttpRequestBean list3request = new HttpRequestBean(storageUri+EP_LIST_NEXT+"/"+listing.getListingId());
        final HttpResponseBean list3response = getApi().getResponse(list3request);
        assertTrue(list3response.isOk());
        listing = json(list3response.getEntityString(), StorageListing.class);
        assertEquals("expected smaller batch size for last batch", NUM_FILES_IN_DIR - (LIST_FETCH_SIZE*2), listing.getKeys().length);
        assertFalse("expected listing3 to be NOT truncated", listing.isTruncated());

        comment = "create a new storage service, identical except for new key and new name: "+REKEY_S3_CLOUD;
        final CloudService newS3cloud = new CloudService();
        copy(newS3cloud, s3cloud);
        newS3cloud.setUuid(null);
        newS3cloud.setName(REKEY_S3_CLOUD);
        newS3cloud.setCredentials(newS3cloud.getCredentials().setParam(PARAM_KEY, CryptoUtil.generatePassword(MIN_KEY_LENGTH, MIN_DISTINCT_LENGTH)));
        final CloudService rekeyCloud = cloudDAO.create(newS3cloud);

        comment = "re-key the entire storage service";
        final HttpRequestBean rekeyRequest = new HttpRequestBean()
                .setMethod(HttpMethods.POST)
                .setUri(storageUri+EP_REKEY)
                .setHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .setEntity(json(new RekeyRequest()
                        .setPassword(ROOT_PASSWORD)
                        .setNewCloud(rekeyCloud.getUuid())));
        final HttpResponseBean rekeyResponse = getApi().getResponse(rekeyRequest);
        assertTrue(rekeyResponse.isOk());

        comment = "after rekey, make sure files are still readable";
        final String rekeyStorageUri = ME_ENDPOINT + EP_CLOUDS + "/" + REKEY_S3_CLOUD + EP_STORAGE;
        for (int i=0; i<NUM_FILES_IN_DIR; i++) {
            final HttpRequestBean read = new HttpRequestBean(rekeyStorageUri+EP_READ+"/dir1/"+path1+i);
            final String checkData = getApi().getStreamedString(read);
            assertEquals("data was not rekeyed correctly", testData, checkData);
        }

        comment = "after rekey, ensure files are NOT readable from old cloud";
        for (int i=0; i<NUM_FILES_IN_DIR; i++) {
            final HttpRequestBean read = new HttpRequestBean(storageUri+EP_READ+"/dir1/"+path1+i);
            try {
                final String checkData = getApi().getStreamedString(read);
                fail("data was not rekeyed correctly, we can still read it with the old cloud");
            } catch (Exception e) {
                log.info("OK, expected failure: "+e);
            }
        }

        comment = "delete the entire directory";
        final HttpRequestBean del2 = new HttpRequestBean()
                .setMethod(DELETE)
                .setUri(rekeyStorageUri+EP_DELETE+"/dir1");
        final HttpResponseBean del2resp = getApi().getResponse(del2);
        assertTrue(del2resp.isOk());

        comment = "ensure they are all gone";
        for (int i=0; i<NUM_FILES_IN_DIR; i++) {
            final String key = "/dir1/" + path1 + i;
            final HttpRequestBean read = new HttpRequestBean(rekeyStorageUri+EP_READ+key);
            try {
                getApi().getStreamedString(read);
                fail("expected key to be deleted: "+key);
            } catch (NotFoundException e) {
                log.info("ok we failed with NotFoundException: "+e);
            }
        }
    }

}
