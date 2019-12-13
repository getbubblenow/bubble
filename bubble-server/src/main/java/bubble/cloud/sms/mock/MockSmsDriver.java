package bubble.cloud.sms.mock;

import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.sms.RenderedSms;
import bubble.cloud.sms.twilio.TwilioSmsDriver;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockSmsDriver extends TwilioSmsDriver {

    @Getter private static final Map<String, ArrayList<RenderedMessage>> spool = new ConcurrentHashMap<>();

    private static final int MAX_INBOX_SIZE = 20;

    @Override protected String getFromPhone() { return "+18005551212"; }

    @Override protected boolean deliver(RenderedSms sms) {
        synchronized (spool) {
            final ArrayList<RenderedMessage> inbox = spool.computeIfAbsent(sms.getToNumber(), m -> new ArrayList<>());
            inbox.add(0, sms);

            // remove old messages, keep inbox size from growing infinitely
            while (inbox.size() > MAX_INBOX_SIZE) inbox.remove(inbox.size()-1);
        }
        return true;
    }

}
