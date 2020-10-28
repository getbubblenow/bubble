package bubble.cloud.email;

import bubble.cloud.CloudServiceDriverBase;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpUtil.getResponse;
import static org.cobbzilla.util.system.Sleep.sleep;

public abstract class EmailApiDriverBase<T extends EmailApiDriverConfig> extends CloudServiceDriverBase<T> implements EmailServiceDriver {

    @Autowired @Getter protected BubbleConfiguration configuration;

    @Override public String getTemplatePath() { return config.getTemplatePath(); }

    protected boolean send(HttpRequestBean request) {
        final int maxTries = 3;
        Exception lastEx = null;
        for (int i=0; i<maxTries; i++) {
            sleep(SECONDS.toMillis(i * i), "send: waiting ti retry");
            final HttpResponseBean response;
            try {
                response = getResponse(request);
                if (response.isOk()) return true;
                log.warn("send: unexpected HTTP status: " + response.getStatus() + ": " + response.getEntityString());
            } catch (Exception e) {
                lastEx = e;
                log.warn("send: "+shortError(e));
            }
        }
        if (lastEx != null) log.error("send: error sending, last exception: "+shortError(lastEx));
        return false;
    }

}
