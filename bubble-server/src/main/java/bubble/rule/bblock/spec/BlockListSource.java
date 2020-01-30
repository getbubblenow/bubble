package bubble.rule.bblock.spec;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class BlockListSource {

    @Getter @Setter private String url;
    @Getter @Setter private String format;

    @Getter @Setter private Long lastDownloaded;
    public long age () { return lastDownloaded == null ? Long.MAX_VALUE : now() - lastDownloaded; }

    @Getter @Setter private BlockList blockList = new BlockList();

    public BlockListSource download() throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(HttpUtil.get(url)))) {
            String line;
            boolean firstLine = true;
            while ( (line = r.readLine()) != null ) {
                if (empty(line)) continue;
                line = line.trim();
                if (firstLine && line.startsWith("[") && line.endsWith("]")) {
                    format = line.substring(1, line.length()-1);
                }
                firstLine = false;
                if (line.startsWith("!")) continue;
                try {
                    if (line.startsWith("@@")) {
                        blockList.addToWhitelist(BlockSpec.parse(line));
                    } else {
                        blockList.addToBlacklist(BlockSpec.parse(line));
                    }
                } catch (Exception e) {
                    log.warn("download("+url+"): error parsing line (skipping due to "+shortError(e)+"): " + line);
                }
            }
        }
        lastDownloaded = now();
        return this;
    }

}
