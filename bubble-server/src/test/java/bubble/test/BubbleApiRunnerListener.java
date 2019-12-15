package bubble.test;

import bubble.BubbleHandlebars;
import bubble.cloud.CloudServiceType;
import bubble.cloud.payment.stripe.StripePaymentDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.mock.MockStripePaymentDriver;
import bubble.model.account.Account;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.service.bill.BillingService;
import com.github.jknack.handlebars.Handlebars;
import com.stripe.model.Token;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.client.script.SimpleApiRunnerListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.getBubbleDefaultDomain;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.incrementSystemTimeOffset;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.parseDuration;

public class BubbleApiRunnerListener extends SimpleApiRunnerListener {

    public static final String FAST_FORWARD_AND_BILL = "fast_forward_and_bill";
    public static final String SET_STRIPE_ERROR = "set_stripe_error";
    public static final String UNSET_STRIPE_ERROR = "unset_stripe_error";
    public static final String STRIPE_TOKENIZE_CARD = "stripe_tokenize_card";
    public static final String CTX_STRIPE_TOKEN = "stripeToken";

    private BubbleConfiguration configuration;

    @Override protected Handlebars initHandlebars() { return BubbleHandlebars.instance.getHandlebars(); }

    public BubbleApiRunnerListener(BubbleConfiguration configuration) {
        super("bubble-api-runner-listener");
        this.configuration = configuration;
    }

    @Override public void beforeScript(String before, Map<String, Object> ctx) throws Exception {
        if (before == null) return;
        if (before.startsWith(FAST_FORWARD_AND_BILL)) {
            final List<String> parts = StringUtil.splitAndTrim(before.substring(FAST_FORWARD_AND_BILL.length()), " ");
            final long delta = parseDuration(parts.get(0));
            final long sleepTime = parts.size() > 1 ? parseDuration(parts.get(1)) : SECONDS.toMillis(20);
            incrementSystemTimeOffset(delta);
            configuration.getBean(BillingService.class).processBilling();
            sleep(sleepTime, "waiting for BillingService to complete");

        } else if (before.equals(SET_STRIPE_ERROR)) {
            MockStripePaymentDriver.setError(before.substring(SET_STRIPE_ERROR.length()).trim());

        } else if (before.equals(UNSET_STRIPE_ERROR)) {
            MockStripePaymentDriver.setError(null);

        } else {
            super.beforeScript(before, ctx);
        }
    }

    @Override public void afterScript(String after, Map<String, Object> ctx) throws Exception {
        if (after == null) return;
        if (after.equals(STRIPE_TOKENIZE_CARD)) {
            // ensure stripe API token is initialized
            final Account admin = configuration.getBean(AccountDAO.class).findFirstAdmin();
            final CloudService stripe = configuration.getBean(CloudServiceDAO.class)
                    .findByAccountAndType(admin.getUuid(), CloudServiceType.payment)
                    .stream().filter(c -> StripePaymentDriver.class.isAssignableFrom(forName(c.getDriverClass())))
                    .findFirst().orElse(null);
            if (stripe == null) {
                die("afterScript: no cloud found with driverClass=" + StripePaymentDriver.class.getName());
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
                die("afterScript: error creating Stripe token: " + e);
            }

        } else if (after.equals(SET_STRIPE_ERROR)) {
            MockStripePaymentDriver.setError(after.substring(SET_STRIPE_ERROR.length()).trim());

        } else if (after.equals(UNSET_STRIPE_ERROR)) {
            MockStripePaymentDriver.setError(null);

        } else {
            super.afterScript(after, ctx);
        }
    }

    @Override public void setCtxVars(Map<String, Object> ctx) {
        ctx.put("serverConfig", configuration);
        ctx.put("defaultDomain", getBubbleDefaultDomain());
    }

}
