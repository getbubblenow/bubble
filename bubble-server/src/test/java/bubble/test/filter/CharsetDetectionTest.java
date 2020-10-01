/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.filter;

import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.resources.stream.FilterHttpRequest;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.resources.stream.FilterMatchersResponse;
import bubble.service.stream.ActiveStreamState;
import bubble.service.stream.AppRuleHarness;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.http.HttpContentEncodingType;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.http.HttpContentEncodingType.*;
import static org.cobbzilla.util.http.HttpContentTypes.TEXT_HTML;
import static org.cobbzilla.util.io.StreamUtil.stream2bytes;
import static org.cobbzilla.util.security.ShaUtil.sha256_base64;
import static org.junit.Assert.*;

public class CharsetDetectionTest {

    public static final byte[] WIN_1250_TEST = stream2bytes("charset_detection/meta-windows-1250.html");
    public static final byte[] WIN_1250_LATE_TEST = stream2bytes("charset_detection/meta-windows-1250-late.html");
    public static final byte[] WIN_1250_EQUIV_TEST = stream2bytes("charset_detection/equiv-windows-1250.html");

    @Test public void testNonUTF8Charset () throws Exception {
        // read first chunk exactly 288 bytes, so it ends in the middle of "charset"
        _testNonUTF8Charset(WIN_1250_TEST, 288, null);
    }

    @Test public void testNonUTF8Charset_gzip () throws Exception {
        // for gzip we won't be able to break exactly on charset,
        // but try a small read anyway to make sure nothing breaks
        _testNonUTF8Charset(gzip.encode(WIN_1250_TEST), 288, gzip);
    }

    @Test public void testNonUTF8CharsetLate () throws Exception {
        _testNonUTF8Charset(WIN_1250_LATE_TEST, 1024, null);
    }

    @Test public void testNonUTF8CharsetLate_brotli () throws Exception {
        _testNonUTF8Charset(br.encode(WIN_1250_LATE_TEST), 1024, br);
    }

    @Test public void testNonUTF8CharsetEquiv () throws Exception {
        _testNonUTF8Charset(WIN_1250_EQUIV_TEST, 1024, null);
    }

    @Test public void testNonUTF8CharsetEquiv_deflate () throws Exception {
        _testNonUTF8Charset(deflate.encode(WIN_1250_EQUIV_TEST), 1024, deflate);
    }

    private void _testNonUTF8Charset(byte[] test, int initialReadSize, HttpContentEncodingType encoding) throws Exception {
        final FilterHttpRequest request = new FilterHttpRequest()
                .setMatchersResponse(new FilterMatchersResponse()
                        .setMatchers(new SingletonList<>(new AppMatcher()))
                        .setRequest(new FilterMatchersRequest()
                                .setFqdn("example.com")
                                .setUri("/test_"+sha256_base64(test)+".html")))
                .setContentType(TEXT_HTML)
                .setEncoding(encoding);
        final List<AppRuleHarness> rules = new ArrayList<>();
        final PassthruDriver driver = new PassthruDriver();
        final AppRuleHarness passthruRuleHarness = passthruRuleHarness(driver);
        rules.add(passthruRuleHarness);

        final ActiveStreamState streamState = new ActiveStreamState(request, rules);

        final byte[] buffer = new byte[8192];
        final ByteArrayInputStream in = new ByteArrayInputStream(test);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(test.length);

        // add first chunk, no charset yet found
        final byte[] buf = new byte[8192];
        final int initialActualRead = in.read(buffer, 0, initialReadSize);
        assertEquals("expected first read to read "+initialReadSize+"bytes", initialReadSize, initialActualRead);
        System.arraycopy(buffer, 0, buf, 0, initialReadSize);
        streamState.addChunk(new ByteArrayInputStream(buf, 0, initialReadSize), initialReadSize);
        Charset charset = driver.getLastSeenCharset();
        assertNull("expected no charset to be found in the first chunk", charset);

        // do not expect to have found a charset yet
        InputStream response = streamState.getResponseStream(false);
        IOUtils.copyLarge(response, out);

        // add remaining chunks, while reading data back
        int count;
        Charset lastSeenCharset = null;
        int responseCount = 0;
        while ((count = in.read(buffer)) != -1) {
            System.arraycopy(buffer, 0, buf, 0, count);
            streamState.addChunk(new ByteArrayInputStream(buf, 0, count), count);
            response = streamState.getResponseStream(false);
            responseCount++;
            charset = driver.getLastSeenCharset();
            if (charset != null) {
                if (lastSeenCharset == null) {
                    lastSeenCharset = charset;
                } else {
                    // charset cannot change
                    assertEquals("expected charset to be same as lastSeenCharset", lastSeenCharset, charset);
                }
                // charset must be windows-1250
                assertEquals("expected windows-1250 charset", "windows-1250", charset.name());
            }
            IOUtils.copyLarge(response, out);
        }
        assertNotNull("expected to find a charset", lastSeenCharset);
        assertEquals("expected windows-1250 charset", "windows-1250", lastSeenCharset.name());

        // add last empty chunk
        streamState.addLastChunk(new ByteArrayInputStream(new byte[0]), 0);

        // read the data back
        response = streamState.getResponseStream(true);
        IOUtils.copyLarge(response, out);

        final byte[] actualBytes = out.toByteArray();
        final String expectedHtml = new String(encoding == null ? test : encoding.decode(test), lastSeenCharset);
        final String actualHtml = new String(encoding == null ? actualBytes : encoding.decode(actualBytes), lastSeenCharset);
        assertEquals("expected output to be same as input", expectedHtml, actualHtml);
    }

    private AppRuleHarness passthruRuleHarness(PassthruDriver driver) {
        final AppRuleHarness appRuleHarness = new AppRuleHarness(new AppMatcher(), new AppRule());
        appRuleHarness.setDriver(driver);
        return appRuleHarness;
    }

}
