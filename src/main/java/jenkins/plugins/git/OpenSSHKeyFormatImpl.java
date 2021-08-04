package jenkins.plugins.git;

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import javax.naming.SizeLimitExceededException;
import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class OpenSSHKeyFormatImpl {

    private final String privateKey;
    private final String passphrase;
    private static final String BEGIN_MARKER = OpenSSHKeyPairResourceParser.BEGIN_MARKER;
    private static final String END_MARKER = OpenSSHKeyPairResourceParser.END_MARKER;
    private static final String DASH_MARKER = "-----";

    public OpenSSHKeyFormatImpl(final String privateKey, final String passphrase) {
        this.privateKey = privateKey;
        this.passphrase = passphrase;
    }

    private byte[] getEncData(String privateKey){
        String data = privateKey
                .replace("\n","")
                .replace(" ","")
                .replace("-----BEGINOPENSSHPRIVATEKEY-----","")
                .replace("-----BEGINOPENSSHPRIVATEKEY-----","");
        Base64.Decoder decoder = Base64.getDecoder();
        return decoder.decode(data);
    }

    private KeyPair getOpenSSHKeyPair(SessionContext session, NamedResource resourceKey,
                                      String beginMarker, String endMarker,
                                      FilePasswordProvider passwordProvider,
                                      byte[] bytes, Map<String, String> headers )
            throws IOException, GeneralSecurityException, SizeLimitExceededException {
        OpenSSHKeyPairResourceParser openSSHParser = new OpenSSHKeyPairResourceParser();
        Collection<KeyPair> keyPairs = openSSHParser.extractKeyPairs(session,resourceKey,beginMarker,
                                                                     endMarker, passwordProvider,
                                                                     bytes, headers);
        if(keyPairs.size() > 1){
            throw new SizeLimitExceededException("Expected KeyPair size to be 1");
        }else {
            return Collections.unmodifiableCollection(keyPairs).iterator().next();
        }
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
        JcaPEMWriter pemWriter = new JcaPEMWriter(sw);
        PemObject pemObject = new PemObject("PRIVATE KEY", content);
        pemWriter.writeObject(pemObject);
        pemWriter.flush();
        pemWriter.close();
        return sw.toString();
    }

    private final static class AcquirePassphrase implements FilePasswordProvider {

        String passphrase;

        AcquirePassphrase(String passphrase) {
            this.passphrase = passphrase;
        }

        @Override
        public String getPassword(SessionContext session, NamedResource resourceKey, int retryIndex) throws IOException {
            return this.passphrase;
        }
    }
}
