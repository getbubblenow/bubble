/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.stream;

import bubble.resources.stream.FilterHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.http.HttpContentEncodingType;
import org.cobbzilla.util.io.FilterInputStreamViaOutputStream;
import org.cobbzilla.util.io.FixedByteArrayInputStream;
import org.cobbzilla.util.io.NullInputStream;
import org.cobbzilla.util.io.multi.MultiStream;
import org.cobbzilla.util.system.Bytes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;

@Slf4j
class ActiveStreamState {

    public static final long DEFAULT_BYTE_BUFFER_SIZE = (8 * Bytes.KB);
    public static final long MAX_BYTE_BUFFER_SIZE = (64 * Bytes.KB);

    private FilterHttpRequest request;
    private String requestId;
    private HttpContentEncodingType encoding;
    private MultiStream multiStream;
    private AppRuleHarness firstRule;
    private InputStream output = null;
    private long totalBytesWritten = 0;
    private long totalBytesRead = 0;

    public ActiveStreamState(FilterHttpRequest request,
                             List<AppRuleHarness> rules) {
        this.request = request;
        this.requestId = request.getId();
        this.encoding = request.getEncoding();
        this.firstRule = rules.get(0);
    }

    private String prefix(String s) { return s+"("+requestId+"): "; }

    public static byte[] toBytes(InputStream in, Integer chunkLength) throws IOException {
        if (in == null) return EMPTY_BYTE_ARRAY;
        final ByteArrayOutputStream bout = new ByteArrayOutputStream((int) (chunkLength == null ? DEFAULT_BYTE_BUFFER_SIZE : Math.min(chunkLength, MAX_BYTE_BUFFER_SIZE)));
        IOUtils.copyLarge(in, bout);
        return bout.toByteArray();
    }

    public void addChunk(InputStream in, Integer chunkLength) throws IOException {
        if (request.hasContentLength() && totalBytesWritten + chunkLength >= request.getContentLength()) {
            if (log.isDebugEnabled()) log.debug(prefix("addChunk")+"detected lastChunk, calling addLastChunk");
            addLastChunk(in, chunkLength);
        } else {
            final byte[] chunk = toBytes(in, chunkLength);
            if (log.isDebugEnabled()) log.debug(prefix("addChunk") + "adding " + chunk.length + " bytes");
            totalBytesWritten += chunk.length;
            if (multiStream == null) {
                multiStream = new MultiStream(new ByteArrayInputStream(chunk));
                output = outputStream(firstRule.getDriver().filterResponse(request, inputStream(multiStream)));
            } else {
                multiStream.addStream(new ByteArrayInputStream(chunk));
            }
        }
    }

    public void addLastChunk(InputStream in, Integer chunkLength) throws IOException {
        final byte[] chunk = toBytes(in, chunkLength);
        if (log.isDebugEnabled()) log.debug(prefix("addLastChunk")+"adding "+chunk.length+" bytes");
        totalBytesWritten += chunk.length;
        if (multiStream == null) {
            multiStream = new MultiStream(new ByteArrayInputStream(chunk), true);
            output = outputStream(firstRule.getDriver().filterResponse(request, inputStream(multiStream)));
        } else {
            multiStream.addLastStream(new ByteArrayInputStream(chunk));
        }
    }

    public InputStream getResponseStream(boolean last) throws IOException {
        final String prefix = prefix("getResponseStream");
        if (log.isDebugEnabled()) log.debug(prefix+"starting with last="+last+", totalBytesWritten="+totalBytesWritten+", totalBytesRead="+totalBytesRead);
        // read to end of all streams, there is no more data coming in
        if (last) {
            if (log.isDebugEnabled()) log.debug(prefix+"last==true, returning full output");
            return output;
        }

        if (request.hasContentLength() && totalBytesWritten >= request.getContentLength()) {
            if (log.isDebugEnabled()) log.debug(prefix+"all bytes written, returning full output");
            return output;
        }
        final int bytesToRead = (int) (totalBytesWritten - totalBytesRead - (2 * MAX_BYTE_BUFFER_SIZE));
        if (bytesToRead < 0) {
            // we shouldn't try to read yet, less than 1024 bytes have been written
            if (log.isDebugEnabled()) log.debug(prefix + "not enough data written (bytesToRead=" + bytesToRead + "), can't read anything yet");
            return NullInputStream.instance;
        }

        if (log.isDebugEnabled()) log.debug(prefix+"trying to read "+bytesToRead+" bytes from output="+output.getClass().getSimpleName());
        final byte[] buffer = new byte[bytesToRead];
        final int bytesRead = output.read(buffer);
        if (log.isDebugEnabled()) log.debug(prefix+"actually read "+bytesRead+" bytes");
        if (bytesRead == -1) {
            // nothing to return
            if (log.isDebugEnabled()) log.debug(prefix+"end of stream, returning NullInputStream");
            return NullInputStream.instance;
        }

        if (log.isDebugEnabled()) log.debug(prefix+"read "+bytesRead+", returning buffer");
        totalBytesRead += bytesRead;

        return new FixedByteArrayInputStream(buffer, 0, bytesRead);
    }

    private Map<String, String> doNotWrap = new ExpirationMap<>(TimeUnit.DAYS.toMillis(1), ExpirationEvictionPolicy.atime);

    private InputStream inputStream(MultiStream baseStream) throws IOException {
        final String prefix = prefix("inputStream");
        final String url = request.getUrl();
        if (encoding == null) {
            if (log.isDebugEnabled()) log.debug(prefix + "no encoding, returning baseStream unmodified");
            return baseStream;
        } else if (encoding == HttpContentEncodingType.identity) {
            if (log.isDebugEnabled()) log.debug(prefix+"identity encoding, returning baseStream unmodified");
            return baseStream;

        } else if (doNotWrap.containsKey(url)) {
            if (log.isDebugEnabled()) log.debug(prefix+"previous error wrapping encoding, returning baseStream unmodified");
            encoding = null;
            return baseStream;
        }
        try {
            final InputStream wrapped = encoding.wrapInput(baseStream);
            if (log.isDebugEnabled()) log.debug(prefix+"returning baseStream wrapped in " + wrapped.getClass().getSimpleName());
            return wrapped;
        } catch (IOException e) {
            if (log.isWarnEnabled()) log.warn(prefix+"error wrapping with "+encoding+", sending as-is (perhaps missing a byte or two)");
            doNotWrap.put(url, url);
            return baseStream;
        }
    }

    private InputStream outputStream(InputStream in) throws IOException {
        final String prefix = prefix("outputStream");
        if (encoding == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"no encoding, returning baseStream unmodified");
            return in;
        } else if (encoding == HttpContentEncodingType.identity) {
            if (log.isDebugEnabled()) log.debug(prefix+"identity encoding, returning baseStream unmodified");
            return in;
        }
        final FilterInputStreamViaOutputStream wrapped = encoding.wrapInputAsOutput(in);
        if (log.isDebugEnabled()) log.debug(prefix+"returning baseStream wrapped in "+wrapped.getOutputStreamClass().getSimpleName());
        return wrapped;
    }
}
