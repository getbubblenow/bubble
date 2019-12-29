package bubble.cloud.sms.twilio;

import bubble.cloud.sms.RenderedSms;
import bubble.cloud.sms.SmsServiceDriverBase;
import bubble.server.BubbleConfiguration;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class TwilioSmsDriver extends SmsServiceDriverBase<TwilioSmsConfig> {

    private static final String PARAM_ACCOUNT_SID = "accountSID";
    private static final String PARAM_AUTH_TOKEN = "authToken";
    private static final String PARAM_FROM_PHONE_NUMBER = "fromPhoneNumber";

    private static final AtomicBoolean twilioInitDone = new AtomicBoolean(false);
    private static String sid;

    @Autowired @Getter protected BubbleConfiguration configuration;

    @Override public void startDriver() {
        synchronized (twilioInitDone) {
            if (!twilioInitDone.get()) {
                sid = getCredentials().getParam(PARAM_ACCOUNT_SID);
                final String token = getCredentials().getParam(PARAM_AUTH_TOKEN);
                Twilio.init(sid, token);
                twilioInitDone.set(true);
            } else if (!sid.equals(getCredentials().getParam(PARAM_ACCOUNT_SID))) {
                die("startDriver: Due to a limitation in the Twilio driver, only one Twilio cloud service is allowed per node.");
            }
        }
    }

    @Override protected String getFromPhone() { return getCredentials().getParam(PARAM_FROM_PHONE_NUMBER); }

    protected boolean deliver(RenderedSms sms) {
        final Message message = Message.creator(
                new PhoneNumber(sms.getToNumber()), // to
                new PhoneNumber(sms.getFromNumber()), // from
                sms.getText()
            ).create();
        return !empty(message.getSid());
    }

}
