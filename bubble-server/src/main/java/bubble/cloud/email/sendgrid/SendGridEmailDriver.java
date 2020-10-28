/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.email.sendgrid;

import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.email.EmailApiDriverBase;
import bubble.cloud.email.EmailApiDriverConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.HttpRequestBean;

import java.util.HashMap;
import java.util.Map;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpMethods.POST;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

@Slf4j
public class SendGridEmailDriver extends EmailApiDriverBase<EmailApiDriverConfig> {

    public static final String SG_API_BASE = "https://api.sendgrid.com/v3/";
    public static final String SG_API_SEND = SG_API_BASE + "mail/send";
    public static final String PARAM_API_KEY = "apiKey";

    public static final String SEND_JSON_TEMPLATE = "sendgrid_email.json.hbs";
    @Getter(lazy=true) private static final String sendJsonTemplate = stream2string(getPackagePath(SendGridEmailDriver.class)+"/"+SEND_JSON_TEMPLATE);

    @Override public boolean send(RenderedMessage renderedMessage) {
        final HttpRequestBean request = new HttpRequestBean();
        request.setMethod(POST)
               .setUri(SG_API_SEND)
               .setHeader(CONTENT_TYPE, APPLICATION_JSON)
               .setHeader(AUTHORIZATION, "Bearer " + getCredentials().getParam(PARAM_API_KEY));

        final Map<String, Object> ctx = new HashMap<>();
        ctx.put("m", renderedMessage);
        request.setEntity(HandlebarsUtil.apply(configuration.getHandlebars(), getSendJsonTemplate(), ctx));

        return send(request);
    }

}
