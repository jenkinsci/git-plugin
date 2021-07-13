package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.EnvVars;
import hudson.FilePath;

import hudson.model.FreeStyleProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import hudson.model.Item;
import hudson.model.FreeStyleBuild;
import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.SecretBuildWrapper;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.JGitApacheTool;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(Parameterized.class)
public class GitUsernamePasswordBindingTest {
    @Parameterized.Parameters(name = "User {0}: Password {1}: GitToolName {2}: GitToolInstance {3}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"randomName", "special%%_342@**", new GitTool("git", "git", null)},
                {"r-Name", "default=@#(*^!", new GitTool("Default", "git", null)},
                {"a", "here's-a-quote", new JGitTool()},
                {"b", "He said \"Hello\", then left.", new JGitApacheTool()},
                {"many-words-in-a-user-name-because-we-can", "&Ampersand&", new JGitApacheTool()},
        });
    }

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule g = new GitSampleRepoRule();

    private final String username;

    private final String password;

    private final GitTool gitToolInstance;

    private final String credentialID = DigestUtils.sha256Hex(("Git Usernanme and Password Binding").getBytes(StandardCharsets.UTF_8));

    private File rootDir = null;
    private FilePath rootFilePath = null;
    private UsernamePasswordCredentialsImpl credentials = null;
    private GitUsernamePasswordBinding gitCredBind = null;

    public GitUsernamePasswordBindingTest(String username, String password, GitTool gitToolInstance) {
        this.username = username;
        this.password = password;
        this.gitToolInstance = gitToolInstance;
    }

    @Before
    public void basicSetup() throws IOException {
        Jenkins.get();
        //File init
        rootDir = tempFolder.getRoot();
        rootFilePath = new FilePath(rootDir.getAbsoluteFile());

        //Credential init
        credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialID, "Git Username and Password Binding Test", this.username, this.password);
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), credentials);

        //GitUsernamePasswordBinding instance
        gitCredBind = new GitUsernamePasswordBinding(gitToolInstance.getName(),credentials.getId());
        assertThat(gitCredBind.type(), is(StandardUsernamePasswordCredentials.class));

        //Setting Git Tool
        Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).getDefaultInstallers().clear();
        Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(gitToolInstance);
    }

    @Test
    public void test_GenerateGitScript_write() throws IOException, InterruptedException {
        GitUsernamePasswordBinding.GenerateGitScript tempGenScript = new GitUsernamePasswordBinding.GenerateGitScript(this.username, this.password, credentials.getId(),!isWindows());
        assertThat(tempGenScript.type(), is(StandardUsernamePasswordCredentials.class));
        FilePath tempScriptFile = tempGenScript.write(credentials, rootFilePath);
        if (!isWindows()) {
            assertThat(tempScriptFile.mode(), is(0500));
            assertThat("File extension not sh", FilenameUtils.getExtension(tempScriptFile.getName()), is("sh"));
        } else {
            assertThat("File extension not bat", FilenameUtils.getExtension(tempScriptFile.getName()), is("bat"));
        }
        assertThat(tempScriptFile.readToString(), containsString(this.username));
        assertThat(tempScriptFile.readToString(), containsString(this.password));
    }

    //This test will pass as long as setKeyBindings(@NonNull StandardCredentials credentials) method
    //is executed before git tool type check, for all git tool implementations
    @Test
    public void test_FreeStyleProject() throws Exception {
        FreeStyleProject prj = r.createFreeStyleProject();
        prj.getBuildWrappersList().add(new SecretBuildWrapper(Collections.<MultiBinding<?>>
                singletonList(new GitUsernamePasswordBinding(gitToolInstance.getName(),credentialID))));
        if (isWindows()) {
            prj.getBuildersList().add(new BatchFile("set | findstr GIT_USERNAME > auth.txt & set | findstr GIT_PASSWORD >> auth.txt"));
        } else {
            prj.getBuildersList().add(new Shell("env | grep GIT_USERNAME > auth.txt; env | grep GIT_PASSWORD >> auth.txt"));
        }
        Map<JobPropertyDescriptor, JobProperty<? super FreeStyleProject>> p = prj.getProperties();
        r.configRoundtrip((Item) prj);
        SecretBuildWrapper wrapper = prj.getBuildWrappersList().get(SecretBuildWrapper.class);
        assertThat(wrapper, is(notNullValue()));
        List<? extends MultiBinding<?>> bindings = wrapper.getBindings();
        assertThat(bindings.size(), is(1));
        MultiBinding<?> binding = bindings.get(0);
        assertThat(((GitUsernamePasswordBinding) binding).getGitToolName(), equalTo(gitToolInstance.getName()));
        FreeStyleBuild b = r.buildAndAssertSuccess(prj);
        assertThat(binding.variables(b), hasItem("GIT_USERNAME"));
        assertThat(binding.variables(b), hasItem("GIT_PASSWORD"));
        r.assertLogNotContains(this.password, b);
        String fileContents = b.getWorkspace().child("auth.txt").readToString().trim();
        assertThat(fileContents, containsString("GIT_USERNAME=" + this.username));
        assertThat(fileContents, containsString("GIT_PASSWORD=" + this.password));
    }

    @Test
    public void test_getGitClientInstance() throws IOException, InterruptedException {
        if(StringUtils.equalsAnyIgnoreCase(gitToolInstance.getName(),"git","Default")) {
            assertThat(gitCredBind.getGitClientInstance(gitToolInstance.getGitExe(), rootFilePath,
                    new EnvVars(), TaskListener.NULL), instanceOf(CliGitAPIImpl.class));
        }else {
            assertThat(gitCredBind.getGitClientInstance(gitToolInstance.getGitExe(), rootFilePath,
                    new EnvVars(), TaskListener.NULL),not(instanceOf(CliGitAPIImpl.class)));
        }
    }
    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote classloader issue
     */
    private static boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }

    private boolean isCliGitTool(){
        return gitToolInstance.getClass().equals(GitTool.class);
    }
}
