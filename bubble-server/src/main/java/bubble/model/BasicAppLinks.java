/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;

import static org.cobbzilla.wizard.model.entityconfig.EntityFieldType.http_url;

public class BasicAppLinks {

    @ECField(type=http_url) @Getter @Setter private String ios;
    @ECField(type=http_url) @Getter @Setter private String android;
    @ECField(type=http_url) @Getter @Setter private String windows;
    @ECField(type=http_url) @Getter @Setter private String macosx;
    @ECField(type=http_url) @Getter @Setter private String linux;

}
