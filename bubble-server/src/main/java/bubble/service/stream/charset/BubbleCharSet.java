/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream.charset;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.string.StringUtil.UTF8cs;

@AllArgsConstructor @ToString(of="charset")
public class BubbleCharSet {

    private static final Map<Charset, BubbleCharSet> cache = new ConcurrentHashMap<>(10);

    public static BubbleCharSet forCharSet(Charset cs) {
        return cache.computeIfAbsent(cs, BubbleCharSet::new);
    }

    public static final BubbleCharSet RAW = new BubbleCharSet(null);
    public static final BubbleCharSet UTF8 = forCharSet(UTF8cs);

    @Getter private final Charset charset;

}
