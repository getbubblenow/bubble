package bubble.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class AuthTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-test"; }

    @Test public void testAccountDeletion () throws Exception { modelTest("auth/delete_account"); }
    @Test public void testBasicAuth () throws Exception { modelTest("auth/basic_auth"); }
    @Test public void testAccountCrud () throws Exception { modelTest("auth/account_crud"); }
    @Test public void testDeviceCrud () throws Exception { modelTest("auth/device_crud"); }
    @Test public void testRegistration () throws Exception { modelTest("auth/account_registration"); }
    @Test public void testForgotPassword () throws Exception { modelTest("auth/forgot_password"); }
    @Test public void testMultifactorAuth () throws Exception { modelTest("auth/multifactor_auth"); }
    @Test public void testDownloadAccount () throws Exception { modelTest("auth/download_account"); }
    @Test public void testNetworkAuth () throws Exception { modelTest("auth/network_auth"); }

}
