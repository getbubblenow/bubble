/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;

public class BasicAppLinks {

    @ECField @Getter @Setter private String ios;
    @ECField @Getter @Setter private String android;
    @ECField @Getter @Setter private String windows;
    @ECField @Getter @Setter private String macosx;
    @ECField @Getter @Setter private String linux;

}
