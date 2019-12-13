package bubble.main.http;

import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.util.RestResponse;

import java.io.File;

import static org.cobbzilla.util.http.HttpContentTypes.MULTIPART_FORM_DATA;
import static org.cobbzilla.util.io.FileUtil.toFileOrDie;

public class BubbleHttpPutMain extends BubbleHttpMain<BubbleHttpEntityOptions> {

    public static void main (String[] args) { main(BubbleHttpPutMain.class, args); }

    @Override protected String getMethod() { return HttpMethods.PUT; }

    @Override protected RestResponse request(String url) throws Exception {
        final BubbleHttpEntityOptions options = getOptions();
        if (options.getContentType().equalsIgnoreCase(MULTIPART_FORM_DATA)) {
            final File temp = FileUtil.temp(".tmp");
            toFileOrDie(temp, options.getRequestJson());
            return getApiClient().doPut(url, temp);
        } else {
            return getApiClient().put(url, options.getRequestJson(), options.contentType());
        }

    }

}
