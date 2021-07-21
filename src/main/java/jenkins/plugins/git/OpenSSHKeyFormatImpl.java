package jenkins.plugins.git;

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.IOException;
import java.io.StringWriter;

public class OpenSSHKeyFormatImpl {

    private final String privateKey;
    private final String passphrase;

    public OpenSSHKeyFormatImpl(final String privateKey, final String passphrase) {
        this.privateKey = privateKey;
        this.passphrase = passphrase;
    }

    public static boolean isOpenSSHFormat(String privateKey) {
        final String HEADER = "-----BEGIN OPENSSH PRIVATE KEY-----";
        return privateKey.regionMatches(false, 0, HEADER, 0, HEADER.length());
    }

    public String getDecodedPrivateKey() throws IOException {
        OpenSSHKeyV1KeyFile openSSHProtectedKey = new OpenSSHKeyV1KeyFile();
        openSSHProtectedKey.init(privateKey, "", new AcquirePassphrase(passphrase.toCharArray()));
        byte[] content = openSSHProtectedKey.getPrivate().getEncoded();
        StringWriter sw = new StringWriter();
        PemWriter pemWriter = new PemWriter(sw);
        PemObject pemObject = new PemObject("PRIVATE KEY", content);
        pemWriter.writeObject(pemObject);
        return sw.toString();
    }

    private final static class AcquirePassphrase implements PasswordFinder {

        char[] p;

        AcquirePassphrase(char[] passphrase) {
            this.p = passphrase;
        }

        @Override
        public char[] reqPassword(Resource<?> resource) {
            return p;
        }

        @Override
        public boolean shouldRetry(Resource<?> resource) {
            return false;
        }
    }
}
