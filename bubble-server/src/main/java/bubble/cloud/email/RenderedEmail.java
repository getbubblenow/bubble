/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.email;

import bubble.cloud.auth.RenderedMessage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.mail.SimpleEmailMessage;
import org.cobbzilla.util.reflect.OpenApiSchema;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;

import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor @Accessors(chain=true) @OpenApiSchema
public class RenderedEmail extends SimpleEmailMessage implements RenderedMessage {

    @ECField @Getter private final long ctime = now();
    @ECField @Getter private final String uuid = randomUUID().toString();
    @Getter @Setter private Map<String, Object> ctx;

    public RenderedEmail (Map<String, Object> ctx) { this.ctx = ctx; }

    public RenderedEmail (SimpleEmailMessage email) { copy(this, email); }

}
