/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import bubble.resources.stream.FilterHttpRequest;
import bubble.service.stream.charset.BubbleCharSet;
import bubble.service.stream.charset.CharsetDetector;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.http.HttpContentEncodingType;
import org.cobbzilla.util.io.FilterInputStreamViaOutputStream;
import org.cobbzilla.util.io.FixedByteArrayInputStream;
import org.cobbzilla.util.io.multi.MultiStream;
import org.cobbzilla.util.system.Bytes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static bubble.service.stream.StreamConstants.MIN_BYTES_BEFORE_WRAP;
import static bubble.service.stream.charset.CharsetDetector.charSetDetectorForContentType;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.io.NullInputStream.NULL_STREAM;
import static org.cobbzilla.util.io.multi.MultiUnderflowHandlerMonitor.DEFAULT_UNDERFLOW_MONITOR;

@Slf4j
public class ActiveStreamState {

    public static final int DEFAULT_BYTE_BUFFER_SIZE = (int) (8 * Bytes.KB);
    public static final long MAX_BYTE_BUFFER_SIZE = (64 * Bytes.KB);

    // If no data is readable for this long, shut down the underlying MultiStream
    public static final long UNDERFLOW_TIMEOUT = SECONDS.toMillis(60);

    public static final String GRIZZLY_WORKER_STACK_LINE
            = "org.glassfish.grizzly.threadpool.AbstractThreadPool$Worker.run";
    static {
        // do not terminate threads that are idling just fine
        // only terminate threads with abnormally long or short stacks
        DEFAULT_UNDERFLOW_MONITOR.setTerminateThreadFunc(t -> {
            final StackTraceElement[] stack = t.getStackTrace();
            if (stack.length < 5) return false;
            if (stack.length > 15) return true;
            for (int i=0; i<5; i++) {
                if (stack[i].toString().contains(GRIZZLY_WORKER_STACK_LINE)) {
                    return false;
                }
            }
            return true;
        });
    }

    private final FilterHttpRequest request;
    private final String requestId;
    private final AppRuleHarness firstRule;
    @Getter private final boolean passthru;
    private HttpContentEncodingType encoding;
    private MultiStream multiStream;
    private InputStream output = null;
    private long totalBytesWritten = 0;
    private long totalBytesRead = 0;
    private final CharsetDetector charsetDetector;

    public ActiveStreamState(FilterHttpRequest request,
                             List<AppRuleHarness> rules) {
        this.request = request;
        this.requestId = request.getId();
        this.encoding = request.getEncoding();
        this.firstRule = rules.get(0);
        this.charsetDetector = charSetDetectorForContentType(request.getContentType());

        final String prefix = "ActiveStreamState("+reqId()+"): ";
        if (empty(rules)) {
            if (log.isDebugEnabled()) log.debug(prefix+"no rules, returning passthru");
            passthru = true;

        } else if (noApplicableRules(rules)) {
            if (log.isDebugEnabled()) log.debug(prefix+"no applicable rules, returning passthru");
            passthru = true;

        } else {
            passthru = false;
        }
    }

    private String reqId() { return requestId+":"+request.getUrl(); }

    public boolean noApplicableRules(List<AppRuleHarness> rules) {
        for (AppRuleHarness appRule : rules) {
            if (appRule.getDriver().couldModify(request)) {
                if (log.isTraceEnabled()) log.trace("noApplicableRules("+requestId+"): appRule "+appRule.getRule().getName()+"/"+appRule.getDriver().getClass().getName()+" could modify request, returning false");
                return false;
            } else {
                if (log.isTraceEnabled()) log.trace("noApplicableRules("+requestId+"): appRule "+appRule.getRule().getName()+"/"+appRule.getDriver().getClass().getName()+" could NOT modify request");
            }
        }
        return true;
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
            final ByteArrayInputStream chunkStream = new ByteArrayInputStream(chunk);
            if (multiStream == null) {
                multiStream = new MultiStream(chunkStream, reqId()).setUnderflowTimeout(UNDERFLOW_TIMEOUT);
            } else {
                multiStream.addStream(chunkStream);
            }
            // do not wrap input with encoding stream until we have received at least MIN_BYTES_BEFORE_WRAP bytes
            // this avoids errors when creating a GZIPInputStream when only one or a few bytes are available
            if (output == null && totalBytesWritten > MIN_BYTES_BEFORE_WRAP) {
                log.info("addChunk: detecting charset using "+charsetDetector.getClass().getSimpleName());
                final BubbleCharSet cs = getCharSet(false);
                log.info("addChunk: detected charset: "+cs);
                if (cs != null) {
                    output = outputStream(firstRule.getDriver().filterResponse(request, inputStream(multiStream), cs.getCharset()));
                }
            }
        }
    }

    public void addLastChunk(InputStream in, Integer chunkLength) throws IOException {
        final byte[] chunk = toBytes(in, chunkLength);
        if (log.isDebugEnabled()) log.debug(prefix("addLastChunk")+"adding "+chunk.length+" bytes");
        totalBytesWritten += chunk.length;
        final ByteArrayInputStream chunkStream = new ByteArrayInputStream(chunk);
        if (multiStream == null) {
            multiStream = new MultiStream(chunkStream, true, reqId()).setUnderflowTimeout(UNDERFLOW_TIMEOUT);;
        } else {
            multiStream.addLastStream(chunkStream);
        }
        if (output == null) {
            log.info("addLastChunk: detecting charset using "+charsetDetector.getClass().getSimpleName());
            final BubbleCharSet cs = getCharSet(true);
            log.info("addLastChunk: detected charset: "+cs);
            final Charset charset;
            if (cs == null) {
                log.warn(prefix("addLastChunk")+"no charset could be determined");
                charset = null;
            } else {
                charset = cs.getCharset();
            }
            output = outputStream(firstRule.getDriver().filterResponse(request, inputStream(multiStream), charset));
        }
    }

    public BubbleCharSet getCharSet(boolean last) throws IOException {
        try {
            multiStream.mark((int) totalBytesWritten);
            @Cleanup final InputStream in = inputStream(multiStream);
            return charsetDetector.getCharSet(in, totalBytesWritten, last);
        } finally {
            multiStream.reset();
        }
    }

    public InputStream getResponseStream(boolean last) throws IOException {
        final String prefix = prefix("getResponseStream");
        if (log.isDebugEnabled()) log.debug(prefix+"starting with last="+last+", totalBytesWritten="+totalBytesWritten+", totalBytesRead="+totalBytesRead);
        // read to end of all streams, there is no more data coming in
        if (last) {
            if (log.isDebugEnabled()) log.debug(prefix+"last==true, returning full output");
            return finalOutputOrNullStream();
        }

        if (request.hasContentLength() && totalBytesWritten >= request.getContentLength()) {
            if (log.isDebugEnabled()) log.debug(prefix+"all bytes written, returning full output");
            return finalOutputOrNullStream();
        }
        final int bytesToRead = (int) (totalBytesWritten - totalBytesRead - (2 * MAX_BYTE_BUFFER_SIZE));
        if (bytesToRead < 0) {
            // we shouldn't try to read yet, less than 1024 bytes have been written
            if (log.isDebugEnabled()) log.debug(prefix + "not enough data written (bytesToRead=" + bytesToRead + "), can't read anything yet");
            return NULL_STREAM;
        }
        if (output == null) {
            if (log.isDebugEnabled()) log.debug(prefix + "not enough data written (output is null and bytesToRead=" + bytesToRead + "), can't read anything yet");
            return NULL_STREAM;
        }

        if (log.isDebugEnabled()) log.debug(prefix+"trying to read "+bytesToRead+" bytes from output="+output.getClass().getSimpleName());
        final byte[] buffer = new byte[bytesToRead];
        final int bytesRead = output.read(buffer);
        if (log.isDebugEnabled()) log.debug(prefix+"actually read "+bytesRead+" bytes");
        if (bytesRead == -1) {
            // nothing to return
            if (log.isDebugEnabled()) log.debug(prefix+"end of stream, returning NullInputStream");
            return NULL_STREAM;
        }

        if (log.isDebugEnabled()) log.debug(prefix+"read "+bytesRead+", returning buffer");
        totalBytesRead += bytesRead;

        return new FixedByteArrayInputStream(buffer, 0, bytesRead);
    }

    public InputStream finalOutputOrNullStream() {
        if (output == null) {
            if (log.isErrorEnabled()) log.error("finalOutputOrNullStream: output was null!");
            return NULL_STREAM;
        }
        return output;
    }

    private final Map<String, String> doNotWrap = new ExpirationMap<>(DAYS.toMillis(1), ExpirationEvictionPolicy.atime);

    private InputStream inputStream(MultiStream baseStream) throws IOException {
        final String prefix = prefix("inputStream");
        final String url = request.getUrl();
        if (encoding == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"no encoding, returning baseStream unmodified");
            return baseStream;
        } else if (encoding == HttpContentEncodingType.identity) {
            if (log.isDebugEnabled()) log.debug(prefix+"identity encoding, returning baseStream unmodified");
            return baseStream;

        } else if (url == null || doNotWrap.containsKey(url)) {
            if (log.isDebugEnabled()) log.debug(prefix+"previous error wrapping encoding, returning baseStream unmodified");
            encoding = null;
            return baseStream;
        }
        try {
            final InputStream wrapped = encoding.wrapInput(baseStream, DEFAULT_BYTE_BUFFER_SIZE);
            if (log.isDebugEnabled()) log.debug(prefix+"returning baseStream wrapped in " + wrapped.getClass().getSimpleName());
            return wrapped;
        } catch (IOException e) {
            if (log.isWarnEnabled()) log.warn(prefix+"error wrapping with "+encoding+", sending as-is (perhaps missing a byte or two) url: "+url+" error:"+shortError(e), e);
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
