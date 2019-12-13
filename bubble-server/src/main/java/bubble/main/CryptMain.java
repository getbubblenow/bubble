package bubble.main;

import org.cobbzilla.util.main.BaseMain;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;

import static org.cobbzilla.wizard.model.ModelCryptUtil.getCryptor;

public class CryptMain extends BaseMain<CryptOptions> {

    public static void main (String[] args) { main(CryptMain.class, args); }

    @Override protected void run() throws Exception {
        final CryptOptions options = getOptions();
        final String value = options.getValue();
        final HibernatePBEStringEncryptor cryptor = getCryptor(options.getDatabaseConfiguration());
        switch (options.getOperation()) {
            case encrypt: out(cryptor.encrypt(value)); return;
            case decrypt: out(cryptor.decrypt(value)); return;
            default: die("invalid operation: "+options.getOperation());
        }
    }

}
