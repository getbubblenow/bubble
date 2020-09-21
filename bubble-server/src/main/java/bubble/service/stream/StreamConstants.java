package bubble.service.stream;

import org.cobbzilla.util.system.Bytes;

public class StreamConstants {

    // do not wrap input with encoding stream until we have received at least this many bytes
    // this avoids errors when creating a GZIPInputStream when only one or a few bytes are available
    public static final long MIN_BYTES_BEFORE_WRAP = Bytes.KB;

}
