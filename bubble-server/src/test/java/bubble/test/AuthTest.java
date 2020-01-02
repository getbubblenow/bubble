package bubble.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class AuthTest extends ActivatedBubbleModelTestBase {

    private static final String MANIFEST_ALL = "manifest-all";

    @Override protected String getManifest() { return MANIFEST_ALL; }

    @Test public void testBasicAuth () throws Exception { modelTest("auth/basic_auth"); }
    @Test public void testAccountCrud () throws Exception { modelTest("auth/account_crud"); }
    @Test public void testDeviceCrud () throws Exception { modelTest("auth/device_crud"); }
    @Test public void testRegistration () throws Exception { modelTest("auth/account_registration"); }
    @Test public void testForgotPassword () throws Exception { modelTest("auth/forgot_password"); }
    @Test public void testMultifactorAuth () throws Exception { modelTest("auth/multifactor_auth"); }
    @Test public void testDownloadAccount () throws Exception { modelTest("auth/download_account"); }

}
