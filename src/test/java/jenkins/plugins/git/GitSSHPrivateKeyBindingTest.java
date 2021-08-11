package jenkins.plugins.git;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.plugins.git.GitTool;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.SecretBuildWrapper;
import org.jenkinsci.plugins.gitclient.JGitApacheTool;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.zip.ZipFile.OPEN_READ;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;

@RunWith(Parameterized.class)
public class GitSSHPrivateKeyBindingTest {
    @Parameterized.Parameters(name = "PrivateKey-Name {0}: Passphrase {1}: GitToolInstance {2}")
    public static Collection<Object[]> data() throws IOException {
        return Arrays.asList(new Object[][]{
                //algorithm_format_enc/uenc
                {privateKeyFileRead("rsa_openssh_enc"), "Dummy", new GitTool("git", "git", null)},
                {privateKeyFileRead("rsa_openssh_uenc"), "", new GitTool("Default", "git", null)},
                {privateKeyFileRead("rsa_openssh_enc"), "Dummy", new JGitTool()},
                {privateKeyFileRead("rsa_openssh_uenc"), "Dummy", new JGitApacheTool()},
        });
    }

    private final String privateKey;
    private final String privatekeyPassphrase;
    private final GitTool gitToolInstance;
    private final String credentialID = DigestUtils.sha256Hex(("Git SSH Private Key Binding").getBytes(StandardCharsets.UTF_8));

    private SSHUserPrivateKey credentials = null;
    private BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKeySource = null;

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule g = new GitSampleRepoRule();

    public GitSSHPrivateKeyBindingTest(String privateKey, String passphrase, GitTool gitToolInstance){
        this.privateKey = privateKey;
        this.privatekeyPassphrase = passphrase;
        this.gitToolInstance = gitToolInstance;
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

        //Setting Git Tool
        Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).getDefaultInstallers().clear();
        Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(gitToolInstance);
    }

    private String batchCheck(boolean includeCliCheck) {
        return includeCliCheck
                ? "set | findstr PRIVATE_KEY > sshAuth.txt & set | findstr PASSPHRASE >> sshAuth.txt & set | findstr GCM_INTERACTIVE >> sshAuth.txt"
                : "set | findstr PRIVATE_KEY > sshAuth.txt & set | findstr PASSPHRASE >> sshAuth.txt";
    }

    private String shellCheck() {
        return "env | grep -zE \"PRIVATE_KEY|PASSPHRASE|GIT_TERMINAL_PROMPT\" > sshAuth.txt";
    }

    @Test
    public void test_SSHBinding_FreeStyleProject() throws Exception {
        FreeStyleProject prj = r.createFreeStyleProject();
        prj.getBuildWrappersList().add(new SecretBuildWrapper(Collections.<MultiBinding<?>>
                singletonList(new GitSSHPrivateKeyBinding(gitToolInstance.getName(),credentialID))));
        prj.getBuildersList().add(isWindows() ? new BatchFile(batchCheck(isCliGitTool())) : new Shell(shellCheck()));
        r.configRoundtrip((Item) prj);

        SecretBuildWrapper wrapper = prj.getBuildWrappersList().get(SecretBuildWrapper.class);
        assertThat(wrapper, is(notNullValue()));
        List<? extends MultiBinding<?>> bindings = wrapper.getBindings();
        assertThat(bindings.size(), is(1));
        MultiBinding<?> binding = bindings.get(0);
        if(isCliGitTool()) {
            assertThat(((GitSSHPrivateKeyBinding) binding).getGitToolName(), equalTo(gitToolInstance.getName()));
        }else {
            assertThat(((GitSSHPrivateKeyBinding) binding).getGitToolName(), equalTo(""));
        }

        FreeStyleBuild b = r.buildAndAssertSuccess(prj);

        String fileContents = b.getWorkspace().child("sshAuth.txt").readToString().trim();
        //Assert Git specific env variables
        if (isCliGitTool()) {
            if (isWindows()) {
                assertThat(fileContents, containsString("GCM_INTERACTIVE=false"));
            } else if (g.gitVersionAtLeast(2, 3, 0)) {
                assertThat(fileContents, containsString("GIT_TERMINAL_PROMPT=false"));
            }
        }
    }

    @Test
    public void test_SSHBinding_PipelineJob() throws Exception {
        WorkflowJob project = r.createProject(WorkflowJob.class);

        // Use default tool if JGit or JGitApache
        String gitToolNameArg = !isCliGitTool() ? "" : ", gitToolName: '" + gitToolInstance.getName() + "'";

        String pipeline = ""
                + "node {\n"
                + "  withCredentials([gitSSHPrivateKey(credentialsId: '" + credentialID + "'" + gitToolNameArg + ")]) {\n"
                + "    if (isUnix()) {\n"
                + "      sh '" + shellCheck() + "'\n"
                + "    } else {\n"
                + "      bat '" + batchCheck(isCliGitTool()) + "'\n"
                + "    }\n"
                + "  }\n"
                + "}";
        project.setDefinition(new CpsFlowDefinition(pipeline, true));
        WorkflowRun b = project.scheduleBuild2(0).waitForStart();
        r.waitForCompletion(b);
        r.assertBuildStatusSuccess(b);

        String fileContents = r.jenkins.getWorkspaceFor(project).child("sshAuth.txt").readToString().trim();
        // Assert Git version specific env variables
        if (isCliGitTool()) {
            if (isWindows()) {
                assertThat(fileContents, containsString("GCM_INTERACTIVE=false"));
            } else if (g.gitVersionAtLeast(2, 3, 0)) {
                assertThat(fileContents, containsString("GIT_TERMINAL_PROMPT=false"));
            }
        }
    }

    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote classloader issue
     */
    private static boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }

    private boolean isCliGitTool() {
        return gitToolInstance.getClass().equals(GitTool.class);
    }
}
