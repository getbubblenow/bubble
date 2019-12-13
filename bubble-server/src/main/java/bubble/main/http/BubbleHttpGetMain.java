package bubble.main.http;

import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.wizard.util.RestResponse;

public class BubbleHttpGetMain extends BubbleHttpMain<BubbleHttpOptions> {

    public static void main (String[] args) { main(BubbleHttpGetMain.class, args); }

    @Override protected String getMethod() { return HttpMethods.GET; }

    @Override protected RestResponse request(String url) throws Exception {
        return getApiClient().get(url);
    }

}
