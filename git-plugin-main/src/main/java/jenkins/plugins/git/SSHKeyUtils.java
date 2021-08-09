package jenkins.plugins.git;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import hudson.FilePath;
import hudson.util.Secret;
import jenkins.bouncycastle.api.PEMEncodable;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.GitClient;

import javax.naming.SizeLimitExceededException;
import java.io.IOException;
import java.security.GeneralSecurityException;

public interface SSHKeyUtils {

    static String getPrivateKey(SSHUserPrivateKey credentials) {
        return credentials.getPrivateKeys().get(0);
    }

    static String getPassphrase(SSHUserPrivateKey credentials) {
        return Secret.toString(credentials.getPassphrase());
    }

    static boolean isPrivateKeyEncrypted(String passphrase) {
        return passphrase.isEmpty() ? false : true;
    }

    default String getSSHExePathInWin(GitClient git) throws IOException, InterruptedException {
        return ((CliGitAPIImpl) git).getSSHExecutable().getAbsolutePath();
    }

    default FilePath getPrivateKeyFile(SSHUserPrivateKey credentials, FilePath workspace) throws InterruptedException, IOException {
        FilePath tempKeyFile = workspace.createTempFile("private", ".key");
        final String privateKeyValue = SSHKeyUtils.getPrivateKey(credentials);
        final String passphraseValue = SSHKeyUtils.getPassphrase(credentials);
        try {
            if (isPrivateKeyEncrypted(passphraseValue)) {
                if (OpenSSHKeyFormatImpl.isOpenSSHFormat(privateKeyValue)) {
                    OpenSSHKeyFormatImpl openSSHKeyFormat = new OpenSSHKeyFormatImpl(privateKeyValue, passphraseValue);
                    openSSHKeyFormat.getOpenSSHKeyFile(tempKeyFile);
                } else {
                    tempKeyFile.write(PEMEncodable.decode(privateKeyValue, passphraseValue.toCharArray()).encode(), null);
                }
            } else {
                tempKeyFile.write(privateKeyValue, null);
            }
            tempKeyFile.chmod(0400);
            return tempKeyFile;
        } catch (IOException | InterruptedException | GeneralSecurityException | SizeLimitExceededException e) {
            e.printStackTrace();
        }
        return null;
    }
}
