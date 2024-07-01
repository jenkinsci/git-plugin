package jenkins.plugins.git;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.util.Secret;
import jenkins.bouncycastle.api.PEMEncodable;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.GitClient;

import javax.naming.SizeLimitExceededException;
import java.io.IOException;
import java.security.GeneralSecurityException;

public interface SSHKeyUtils {

    /**
     * Get a single private key
     * @param credentials Credentials{@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey}. Can't be null
     * @return Private key at index 0
     * @exception IndexOutOfBoundsException thrown when no private key if found/available
     **/
    static String getSinglePrivateKey(@NonNull SSHUserPrivateKey credentials) {
        return credentials.getPrivateKeys().get(0);
    }

    /**
     * Get passphrase as a Secret{@link hudson.util.Secret}
     * @param credentials Credentials{@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey}. Can't be null
     * @return Passphrase of type secret{@link hudson.util.Secret}
     **/
    static Secret getPassphraseAsSecret(@NonNull SSHUserPrivateKey credentials) {
        return credentials.getPassphrase();
    }

    /**
     * Get passphrase as a String{@link java.lang.String}
     * @param credentials Credentials{@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey}. Can't be null
     * @return Passphrase of type string{@link java.lang.String}
     **/
    static String getPassphraseAsString(@NonNull SSHUserPrivateKey credentials) {
        return Secret.toString(credentials.getPassphrase());
    }

    /**
     * Check if private key is encrypted by using the passphrase
     * @param passphrase Passphrase{@link java.lang.String}. Can't be null
     * @return True if passphrase is not an empty string value
     **/
    static boolean isPrivateKeyEncrypted(@NonNull String passphrase) {
        return passphrase.isEmpty() ? false : true;
    }

    /**
     * Get SSH executable absolute path{@link java.lang.String} on a Windows system
     * @param git Git Client{@link org.jenkinsci.plugins.gitclient.GitClient}. Can't be null
     * @return SSH executable absolute path{@link java.lang.String}
     * @exception InterruptedException If no ssh executable path is found
     **/
    default String getSSHExePathInWin(@NonNull GitClient git) throws IOException, InterruptedException {
        return ((CliGitAPIImpl) git).getSSHExecutable().getAbsolutePath();
    }

    /**
     * Get a file{@link hudson.FilePath} stored on the file-system used during the build, with private key written in it.
     * The private key if encrypted will be decrypted first and then written in the file{@link hudson.FilePath}.
     * @param credentials Credentials{@link com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey}. Can't be null
     * @param workspace Work directory{@link hudson.FilePath} for storing private key file
     * @return Private Key file
     **/
    default FilePath getPrivateKeyFile(@NonNull SSHUserPrivateKey credentials, @NonNull FilePath workspace) {
        final String privateKeyValue = SSHKeyUtils.getSinglePrivateKey(credentials);
        final String passphraseValue = SSHKeyUtils.getPassphraseAsString(credentials);
        try {
            FilePath tempKeyFile = workspace.createTempFile("private", ".key");
            if (isPrivateKeyEncrypted(passphraseValue)) {
                if (OpenSSHKeyFormatImpl.isOpenSSHFormatted(privateKeyValue)) {
                    OpenSSHKeyFormatImpl openSSHKeyFormat = new OpenSSHKeyFormatImpl(privateKeyValue, SSHKeyUtils.getPassphraseAsSecret(credentials));
                    openSSHKeyFormat.writeDecryptedOpenSSHKey(tempKeyFile);
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
