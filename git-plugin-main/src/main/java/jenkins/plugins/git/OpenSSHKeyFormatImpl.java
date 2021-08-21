package jenkins.plugins.git;

import hudson.FilePath;
import hudson.util.Secret;
import io.jenkins.plugins.git.shaded.org.apache.sshd.common.config.keys.loader.openssh.OpenSSHKeyPairResourceParser;
import io.jenkins.plugins.git.shaded.org.apache.sshd.common.NamedResource;
import io.jenkins.plugins.git.shaded.org.apache.sshd.common.config.keys.FilePasswordProvider;
import io.jenkins.plugins.git.shaded.org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import io.jenkins.plugins.git.shaded.org.apache.sshd.common.session.SessionContext;
import io.jenkins.plugins.git.shaded.org.apache.sshd.common.util.io.SecureByteArrayOutputStream;

import javax.naming.SizeLimitExceededException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class OpenSSHKeyFormatImpl {

    private final String privateKey;
    private final Secret passphrase;
    private static final String BEGIN_MARKER = OpenSSHKeyPairResourceParser.BEGIN_MARKER;
    private static final String END_MARKER = OpenSSHKeyPairResourceParser.END_MARKER;
    private static final String DASH_MARKER = "-----";

    public OpenSSHKeyFormatImpl(final String privateKey, final Secret passphrase) {
        this.privateKey = privateKey;
        this.passphrase = passphrase;
    }

    private byte[] getEncData(String privateKey){
        String data = privateKey
                .replace(DASH_MARKER+BEGIN_MARKER+DASH_MARKER,"")
                .replace(DASH_MARKER+END_MARKER+DASH_MARKER,"")
                .replaceAll("\\s","");
        Base64.Decoder decoder = Base64.getDecoder();
        return decoder.decode(data);
    }

    private KeyPair getOpenSSHKeyPair(SessionContext session, NamedResource resourceKey,
                                      String beginMarker, String endMarker,
                                      FilePasswordProvider passwordProvider,
                                      InputStream stream, Map<String, String> headers )
            throws IOException, GeneralSecurityException, SizeLimitExceededException {
        OpenSSHKeyPairResourceParser openSSHParser = new OpenSSHKeyPairResourceParser();
        Collection<KeyPair> keyPairs = openSSHParser.extractKeyPairs(session,resourceKey,beginMarker,
                                                                     endMarker, passwordProvider,
                                                                     stream, headers);
        if(keyPairs.size() > 1){
            throw new SizeLimitExceededException("Expected KeyPair size to be 1");
        }else {
            return Collections.unmodifiableCollection(keyPairs).iterator().next();
        }
    }

    private FilePath writePrivateKeyOpenSSHFormatted(FilePath tempFile) throws SizeLimitExceededException, GeneralSecurityException, IOException, InterruptedException {
        OpenSSHKeyPairResourceWriter privateKeyWriter = new OpenSSHKeyPairResourceWriter();
        SecureByteArrayOutputStream privateKeyBuffer = new SecureByteArrayOutputStream();
        ByteArrayInputStream stream = new ByteArrayInputStream(getEncData(this.privateKey));
        KeyPair sshKeyPair = null;
        sshKeyPair = getOpenSSHKeyPair(null,null,"","",
                    new AcquirePassphrase(this.passphrase),
                    stream,null);
        privateKeyWriter.writePrivateKey(sshKeyPair, "", null, privateKeyBuffer);
        tempFile.write(privateKeyBuffer.toString(),null);
        return tempFile;
    }

    /**
     * Check the format of private key using HEADERS
     * @param privateKey The private key{@link java.lang.String}
     * @return true is the privater key is in OpenSSH format
     **/
    public static boolean isOpenSSHFormatted(String privateKey) {
        final String HEADER = DASH_MARKER+BEGIN_MARKER+DASH_MARKER;
        return privateKey.regionMatches(false, 0, HEADER, 0, HEADER.length());
    }

    /**
     * Decrypts the passphrase protected OpenSSH formatted private key
     * @param tempKeyFile Decrypted private key file{@link hudson.FilePath} on agents/controller file-system
     * @return Decrypted private key file{@link hudson.FilePath} OpenSSH Formatted
     **/
    public FilePath writeDecryptedOpenSSHKey(FilePath tempKeyFile) throws IOException, InterruptedException, GeneralSecurityException, SizeLimitExceededException {
        return writePrivateKeyOpenSSHFormatted(tempKeyFile);
    }

    private final static class AcquirePassphrase implements FilePasswordProvider {

        Secret passphrase;

        AcquirePassphrase(Secret passphrase) {
            this.passphrase = passphrase;
        }

        @Override
        public String getPassword(SessionContext session, NamedResource resourceKey, int retryIndex) throws IOException {
            return this.passphrase.getPlainText();
        }
    }
}
