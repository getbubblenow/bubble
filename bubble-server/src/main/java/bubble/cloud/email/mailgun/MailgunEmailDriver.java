package bubble.cloud.email.mailgun;

import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.email.EmailApiDriverBase;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.body.MultipartBody;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.mail.SimpleEmailMessage;
import org.cobbzilla.util.string.Base64;

import java.io.InputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;

@Slf4j
public class MailgunEmailDriver extends EmailApiDriverBase<MailgunEmailDriverConfig> {

    public static final String PARAM_DOMAIN = "domain";
    public static final String PARAM_API_KEY = "apiKey";

    public static final String MG_API_BASE = "https://api.mailgun.net/v3/";
    public static final String MG_API_SEND = "/messages";
    public static final String FALSE = Boolean.FALSE.toString();

    @Getter(lazy=true) private final String mailSendUrl = MG_API_BASE + getCredentials().getParam(PARAM_DOMAIN) + MG_API_SEND;
    @Getter(lazy=true) private final String authHeader = "Basic " + Base64.encodeBytes(("api:" + getCredentials().getParam(PARAM_API_KEY)).getBytes());

    @Override public boolean send(RenderedMessage renderedMessage) {

        // todo: add retries?
        final SimpleEmailMessage m = (SimpleEmailMessage) renderedMessage;
        try {
            MultipartBody request = Unirest.post(getMailSendUrl())
                    .basicAuth("api", getCredentials().getParam(PARAM_API_KEY))
                    .field("from", formatEmail(m.getFromName(), m.getFromEmail()))
                    .field("to", formatEmail(m.getToName(), m.getToEmail()))
                    .field("subject", m.getSubject())
                    .field("text", m.getMessage())
                    .field("o:tracking", FALSE)
                    .field("o:tracking-clicks", FALSE)
                    .field("o:tracking-opens", FALSE);
            if (m.hasHtmlMessage()) request = request.field("html", m.getHtmlMessage());

            final HttpResponse<JsonNode> response = request.asJson();
            if (response.getStatus() / 100 == 2) return true;

            final InputStream rawBody = response.getRawBody();
            final String body = rawBody != null ? IOUtils.toString(rawBody, UTF8cs) : null;
            log.warn("send: error: HTTP status "+response.getStatus()+": "+response.getStatusText()+(body != null ? ": "+body : ""));

        } catch (Exception e) {
            log.error("send: "+shortError(e));
        }
        return false;
    }

    private String formatEmail(String name, String email) {
        return !empty(name) ? safeEmailName(name) + " <" + email + ">" : email; }

    private String safeEmailName(String name) {
        // Ensure "name" part of email does not contain any characters that could be used to inject another
        // email address, and quote it to ensure it
        return "\"" + name
                .replace("<", "")
                .replace(">", "")
                .replace(",", " ")
                .replace("\"", "")
                + "\"";
    }

}
