package bubble.test.filter;

import bubble.resources.stream.FilterHttpRequest;
import bubble.rule.AbstractAppRuleDriver;
import lombok.Getter;

import java.io.InputStream;
import java.nio.charset.Charset;

public class PassthruDriver extends AbstractAppRuleDriver {

    @Getter private Charset lastSeenCharset;

    @Override public InputStream doFilterResponse(FilterHttpRequest filterRequest, InputStream in, Charset charset) {
        this.lastSeenCharset = charset;
        return super.doFilterResponse(filterRequest, in, charset);
    }

    @Override public boolean couldModify(FilterHttpRequest request) { return true; }

}
