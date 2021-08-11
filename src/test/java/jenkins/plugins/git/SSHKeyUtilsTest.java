package jenkins.plugins.git;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.plugins.git.GitTool;
import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.zip.ZipFile.OPEN_READ;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@RunWith(Parameterized.class)
public class SSHKeyUtilsTest {
    @Parameterized.Parameters(name = "PrivateKey-Name {0}: Passphrase {1}")
    public static Collection<Object[]> data() throws IOException {
        return Arrays.asList(new Object[][]{
                //algorithm_format_enc/uenc
                {privateKeyFileRead("rsa_openssh_enc"), "Dummy"},
                {privateKeyFileRead("rsa_openssh_uenc"), ""},
        });
    }

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule g = new GitSampleRepoRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final String privateKey;
    private final String privatekeyPassphrase;
    private final String credentialID = DigestUtils.sha256Hex(("Git SSH Private Key Binding").getBytes(StandardCharsets.UTF_8));

    private SSHUserPrivateKey credentials = null;
    private BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKeySource = null;
    private File workspace = temporaryFolder.newFolder();


    public SSHKeyUtilsTest(String privateKey, String passphrase) throws IOException {
        this.privateKey = privateKey;
        this.privatekeyPassphrase = passphrase;
    }

    private static String privateKeyFileRead(String privatekeyFilename) throws IOException {
        File zipFilePath = new File("src/test/resources/jenkins/plugins/git/GitSSHPrivateKeyBindingTest/sshKeys.zip");
        ZipFile zipFile = new ZipFile(zipFilePath,OPEN_READ);
        ZipEntry zipEntry = zipFile.getEntry(privatekeyFilename);
        InputStream stream = zipFile.getInputStream(zipEntry);
        int arraySize = (int) zipEntry.getSize();
        byte[] keyBytes = new byte[arraySize];
        stream.read(keyBytes);
        stream.close();
        zipFile.close();
        return new String(keyBytes, StandardCharsets.UTF_8);
    }

    @Before
    public void basicSetup() throws IOException {
        //Private Key
        privateKeySource = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey);

        //Credential init
        credentials = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, credentialID, "GitSSHPrivateKey"
                ,privateKeySource,privatekeyPassphrase,"Git SSH Private Key Binding");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), credentials);
    }

    @Test
    public void test_SSHKeyUtils_StaticMethods(){
        String tempKey = SSHKeyUtils.getSinglePrivateKey(credentials);
        assertThat(tempKey, equalTo(this.privateKey));
        String tempPassphrase = SSHKeyUtils.getPassphrase(credentials);
        assertThat(tempPassphrase, equalTo(this.privatekeyPassphrase));
        boolean flag = SSHKeyUtils.isPrivateKeyEncrypted(this.privatekeyPassphrase);
        assertThat(flag, not(this.privatekeyPassphrase.isEmpty()));
    }

    private GitSSHPrivateKeyBinding getGitSSHBindingInstance() {
        //Setting Git Tool
        Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).getDefaultInstallers().clear();
        Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(new GitTool("Default", "git", null));
        return new GitSSHPrivateKeyBinding("Default",credentialID);
    }
}
