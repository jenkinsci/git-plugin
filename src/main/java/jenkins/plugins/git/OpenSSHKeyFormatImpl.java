package jenkins.plugins.git;

import hudson.FilePath;
import org.apache.sshd.common.config.keys.loader.openssh.OpenSSHKeyPairResourceParser;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.io.SecureByteArrayOutputStream;

import javax.naming.SizeLimitExceededException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
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

    private File writePrivateKeyOpenSSHFormatted(File tempFile) {
        OpenSSHKeyPairResourceWriter privateKeyWriter = new OpenSSHKeyPairResourceWriter();
        SecureByteArrayOutputStream privateKeyBuffer = new SecureByteArrayOutputStream();
        ByteArrayInputStream stream = new ByteArrayInputStream(getEncData(this.privateKey));
        KeyPair sshKeyPair = null;
        try {
            sshKeyPair = getOpenSSHKeyPair(null,null,"","",
                    new AcquirePassphrase(this.passphrase),
                    stream,null);
            privateKeyWriter.writePrivateKey(sshKeyPair, "", null, privateKeyBuffer);
            FileOutputStream privateKeyFileStream = new FileOutputStream(tempFile);
            privateKeyBuffer.writeTo(privateKeyFileStream);
            privateKeyFileStream.close();
        } catch (IOException | SizeLimitExceededException | GeneralSecurityException e) {
            e.printStackTrace();
        }
        return tempFile;
    }

    public static boolean isOpenSSHFormatted(String privateKey) {
        final String HEADER = DASH_MARKER+BEGIN_MARKER+DASH_MARKER;
        return privateKey.regionMatches(false, 0, HEADER, 0, HEADER.length());
    }

    public FilePath writeDecryptedOpenSSHKey(FilePath tempKeyFile) throws IOException, InterruptedException, GeneralSecurityException, SizeLimitExceededException {
        File tempFile = new File(tempKeyFile.toURI());
        writePrivateKeyOpenSSHFormatted(tempFile);
        return new FilePath(tempFile);
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
