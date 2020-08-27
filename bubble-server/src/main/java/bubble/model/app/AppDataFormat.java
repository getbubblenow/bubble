/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bubble.ApiConstants.enumFromString;

@AllArgsConstructor
public enum AppDataFormat {

    key        (s -> s.map(AppData::getKey).collect(Collectors.toList())),

    value      (s -> s.map(AppData::getData).collect(Collectors.toList())),

    key_value  (s -> s.collect(Collectors.toMap(AppData::getKey, AppData::getData))),

    full       (s -> s.collect(Collectors.toList()));

    @JsonCreator public static AppDataFormat fromString (String v) { return enumFromString(AppDataFormat.class, v); }

    private final Function<Stream<AppData>, Object> filter;

    public Object filter (Stream<AppData> stream) { return filter.apply(stream); }

}
