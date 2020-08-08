/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;

import java.util.function.Function;

import static bubble.ApiConstants.enumFromString;
import static java.util.function.Function.identity;

@AllArgsConstructor
public enum MessageResourceFormat {

    raw (identity()),
    underscore (v -> v.replace(".", "_"));

    private final Function<String, String> formatFunction;

    @JsonCreator public static MessageResourceFormat fromString(String v) { return enumFromString(MessageResourceFormat.class, v); }

    public String format(String val) { return formatFunction.apply(val); }

}
