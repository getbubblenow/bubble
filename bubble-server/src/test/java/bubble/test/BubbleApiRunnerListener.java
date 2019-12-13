package bubble.test;

import bubble.BubbleHandlebars;
import bubble.cloud.CloudServiceType;
import bubble.cloud.payment.stripe.StripePaymentDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import com.github.jknack.handlebars.Handlebars;
import com.stripe.model.Token;
import org.cobbzilla.wizard.client.script.SimpleApiRunnerListener;

import java.util.HashMap;
import java.util.Map;

import static bubble.ApiConstants.getBubbleDefaultDomain;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class BubbleApiRunnerListener extends SimpleApiRunnerListener {

    public static final String STRIPE_TOKENIZE_CARD = "stripe_tokenize_card";
    public static final String CTX_STRIPE_TOKEN = "stripeToken";

    private BubbleConfiguration configuration;

    @Override protected Handlebars initHandlebars() { return BubbleHandlebars.instance.getHandlebars(); }

    public BubbleApiRunnerListener(BubbleConfiguration configuration) {
        super("bubble-api-runner-listener");
        this.configuration = configuration;
    }

    @Override public void afterScript(String after, Map<String, Object> ctx) throws Exception {
        if (after != null && after.equals(STRIPE_TOKENIZE_CARD)) {
            // ensure stripe API token is initialized
            final Account admin = configuration.getBean(AccountDAO.class).findFirstAdmin();
            final CloudService stripe = configuration.getBean(CloudServiceDAO.class)
                    .findByAccountAndType(admin.getUuid(), CloudServiceType.payment)
                    .stream().filter(c -> c.getDriverClass().equals(StripePaymentDriver.class.getName()))
                    .findFirst().orElse(null);
            if (stripe == null) {
                die("afterScript: no cloud found with driverClass="+StripePaymentDriver.class.getName());
                return;
            }
            stripe.getPaymentDriver(configuration);

            final Map<String, Object> tokenParams = new HashMap<>();
            final Map<String, Object> cardParams = new HashMap<>();
            cardParams.put("number", "4242424242424242");
            cardParams.put("exp_month", 10);
            cardParams.put("exp_year", 2026);
            cardParams.put("cvc", "222");
            tokenParams.put("card", cardParams);
            try {
                final Token token = Token.create(tokenParams);
                ctx.put(CTX_STRIPE_TOKEN, token.getId());
            } catch (Exception e) {
                die("afterScript: error creating Stripe token: "+e);
            }

        } else {
            super.afterScript(after, ctx);
        }
    }

    @Override public void setCtxVars(Map<String, Object> ctx) {
        ctx.put("serverConfig", configuration);
        ctx.put("defaultDomain", getBubbleDefaultDomain());
    }

}
