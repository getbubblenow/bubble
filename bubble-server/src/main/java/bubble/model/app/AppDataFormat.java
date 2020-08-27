/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;

import java.util.function.Function;
import java.util.stream.Stream;

import static bubble.ApiConstants.enumFromString;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor
public enum AppDataFormat {

    key        (s -> s.map(AppData::getKey).collect(toList())),

    value      (s -> s.map(AppData::getData).collect(toList())),

    key_value  (s -> s.collect(toMap(AppData::getKey, AppData::getData))),

    full       (s -> s.collect(toList()));

    @JsonCreator public static AppDataFormat fromString (String v) { return enumFromString(AppDataFormat.class, v); }

    private final Function<Stream<AppData>, Object> filter;

    public Object filter (Stream<AppData> stream) { return filter.apply(stream); }

}
