package bubble.rule;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class FilterResponse {

    public FilterResponse(String data) { this.data = data.getBytes(); }

    @Getter @Setter byte[] data;
    private int pos;

    public boolean hasData() { return data != null && data.length > 0; }

    public int read() {
        if (pos >= data.length) return -1;
        return data[pos++];
    }

    public int read(byte[] b, int off, int len) {
        if (pos >= data.length) return -1;

        int readLen = len;
        if (pos + len > data.length) {
            readLen = data.length - pos;
        }
        System.arraycopy(data, pos, b, off, readLen);
        pos += readLen;
        return readLen;
    }
}
