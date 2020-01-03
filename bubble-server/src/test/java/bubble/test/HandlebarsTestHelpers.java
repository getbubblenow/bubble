package bubble.test;

import com.github.jknack.handlebars.Handlebars;
import lombok.extern.slf4j.Slf4j;

import static bubble.ApiConstants.G_AUTH;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class HandlebarsTestHelpers {

    public static Handlebars registerTestHelpers(Handlebars hb) {
        hb.registerHelper("authenticator_token", (src, options) -> {
            if (src == null) return die("authenticator_token: no secret provided");
            if (!(src instanceof String)) return die("authenticator_token: secret was not a String");
            final String secret = ""+src;
            final int code = G_AUTH.getTotpPassword(secret);
            log.info("initHandlebars: generated code="+code+" from secret: '"+secret+"'");
            return new Handlebars.SafeString(""+code);
        });
        return hb;
    }

}
