package bubble.test;

public class NetworkTestBase extends ActivatedBubbleModelTestBase {

    public static final String MANIFEST_NETWORK = "manifest-network";

    @Override protected String getModelPrefix() { return "models/"; }
    @Override protected String getManifest() { return MANIFEST_NETWORK; }

}
