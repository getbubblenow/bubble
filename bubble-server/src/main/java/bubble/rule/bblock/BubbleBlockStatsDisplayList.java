/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.bblock;

import bubble.model.device.BlockStatsDisplayMode;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpUtil.url2string;

public class BubbleBlockStatsDisplayList {

    @Getter @Setter private String name;
    @Getter @Setter private String id;
    @Getter @Setter private String url;
    @Getter @Setter private String description;
    @Getter @Setter private BlockStatsDisplayMode mode;

    public List<String> loadLines() {
        final List<String> lines = new ArrayList<>();
        try {
            for (String line : url2string(url).split("\n")) {
                line = line.trim();
                if (empty(line) || line.startsWith("#")) continue;
                lines.add(line);
            }
        } catch (IOException e) {
            return die("loadLines: "+shortError(e));
        }
        return lines;
    }

}
