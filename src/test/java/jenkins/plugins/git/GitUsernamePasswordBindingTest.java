package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.EnvVars;
import hudson.FilePath;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.SecretBuildWrapper;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.JGitApacheTool;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
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
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;

@RunWith(Parameterized.class)
public class GitUsernamePasswordBindingTest {
    @Parameterized.Parameters(name = "User {0}: Password {1}: GitToolName {2}: GitToolInstance {3}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"randomName", "special%%_342@**", new GitTool("git", "git", null)},
                {"r-Name", "default=@#(*^!", new GitTool("Default", "git", null)},
                {"adwesw-unique", "here's-a-quote", new JGitTool()},
                {"bceas-unique", "He said \"Hello\", then left.", new JGitApacheTool()},
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
        gitCredBind = new GitUsernamePasswordBinding(gitToolInstance.getName(), credentials.getId());
        assertThat(gitCredBind.type(), is(StandardUsernamePasswordCredentials.class));

        //Setting Git Tool
        Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).getDefaultInstallers().clear();
        Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(gitToolInstance);
    }

    @Test
    public void test_EnvironmentVariables_FreeStyleProject() throws Exception {
        FreeStyleProject prj = r.createFreeStyleProject();
        prj.getBuildWrappersList().add(new SecretBuildWrapper(Collections.<MultiBinding<?>>
                singletonList(new GitUsernamePasswordBinding(gitToolInstance.getName(), credentialID))));
        if (isCliGitTool()) {
            if (isWindows()) {
                prj.getBuildersList().add(new BatchFile("set | findstr GIT_USERNAME > auth.txt & set | findstr GIT_PASSWORD >> auth.txt & set | findstr GCM_INTERACTIVE >> auth.txt"));
            } else {
                prj.getBuildersList().add(new Shell("env | grep GIT_USERNAME > auth.txt; env | grep GIT_PASSWORD >> auth.txt; env | grep GIT_TERMINAL_PROMPT >> auth.txt;"));
            }
        } else {
            if (isWindows()) {
                prj.getBuildersList().add(new BatchFile("set | findstr GIT_USERNAME > auth.txt & set | findstr GIT_PASSWORD >> auth.txt"));
            } else {
                prj.getBuildersList().add(new Shell("env | grep GIT_USERNAME > auth.txt; env | grep GIT_PASSWORD >> auth.txt;"));
            }
        }
        r.configRoundtrip((Item) prj);

        SecretBuildWrapper wrapper = prj.getBuildWrappersList().get(SecretBuildWrapper.class);
        assertThat(wrapper, is(notNullValue()));
        List<? extends MultiBinding<?>> bindings = wrapper.getBindings();
        assertThat(bindings.size(), is(1));
        MultiBinding<?> binding = bindings.get(0);
        if(isCliGitTool()) {
            assertThat(((GitUsernamePasswordBinding) binding).getGitToolName(), equalTo(gitToolInstance.getName()));
        }else {
            assertThat(((GitUsernamePasswordBinding) binding).getGitToolName(), equalTo(""));
        }

        FreeStyleBuild b = r.buildAndAssertSuccess(prj);
        r.assertLogNotContains(this.username, b);
        r.assertLogNotContains(this.password, b);

        //Assert Keys
        assertThat(binding.variables(b), hasItem("GIT_USERNAME"));
        assertThat(binding.variables(b), hasItem("GIT_PASSWORD"));
        //Assert setKeyBindings method
        String fileContents = b.getWorkspace().child("auth.txt").readToString().trim();
        assertThat(fileContents, containsString("GIT_USERNAME=" + this.username));
        assertThat(fileContents, containsString("GIT_PASSWORD=" + this.password));
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
    public void test_EnvironmentVariables_PipelineJob() throws Exception {
        WorkflowJob project = r.createProject(WorkflowJob.class);
        if (isCliGitTool()) {
            project.setDefinition(new CpsFlowDefinition(""
                    + "node {\n"
                    + "withCredentials([GitUsernamePassword(credentialsId: '" + credentialID + "', gitToolName: '" + gitToolInstance.getName() + "')]) {"
                    + "    if (isUnix()) {\n"
                    + "      sh 'env | grep GIT_USERNAME > auth.txt; env | grep GIT_PASSWORD >> auth.txt; env | grep GIT_TERMINAL_PROMPT >> auth.txt;'\n"
                    + "    } else {\n"
                    + "      bat 'set | findstr GIT_USERNAME > auth.txt & set | findstr GIT_PASSWORD >> auth.txt & set | findstr GCM_INTERACTIVE >> auth.txt'\n"
                    + "    }\n"
                    + "  }\n"
                    + "}", true));
        } else {
            project.setDefinition(new CpsFlowDefinition(""
                    + "node {\n"
                    + "withCredentials([GitUsernamePassword(credentialsId: '" + credentialID + "', gitToolName: '" + gitToolInstance.getName() + "')]) {"
                    + "    if (isUnix()) {\n"
                    + "      sh 'env | grep GIT_USERNAME > auth.txt; env | grep GIT_PASSWORD >> auth.txt;'\n"
                    + "    } else {\n"
                    + "      bat 'set | findstr GIT_USERNAME > auth.txt & set | findstr GIT_PASSWORD >> auth.txt'\n"
                    + "    }\n"
                    + "  }\n"
                    + "}", true));
        }
        WorkflowRun b = project.scheduleBuild2(0).waitForStart();
        r.waitForCompletion(b);
        r.assertBuildStatusSuccess(b);
        r.assertLogNotContains(this.username, b);
        r.assertLogNotContains(this.password, b);
        //Assert setKeyBindings method
        String fileContents = r.jenkins.getWorkspaceFor(project).child("auth.txt").readToString().trim();
        assertThat(fileContents, containsString("GIT_USERNAME=" + this.username));
        assertThat(fileContents, containsString("GIT_PASSWORD=" + this.password));
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
    public void test_getCliGitTool_using_FreeStyleProject() throws Exception {
        FreeStyleProject prj = r.createFreeStyleProject();
        prj.getBuildWrappersList().add(new SecretBuildWrapper(Collections.<MultiBinding<?>>
                singletonList(new GitUsernamePasswordBinding(gitToolInstance.getName(), credentialID))));
        if (isWindows()) {
            prj.getBuildersList().add(new BatchFile("set | findstr GIT_USERNAME > auth.txt & set | findstr GIT_PASSWORD >> auth.txt"));
        } else {
            prj.getBuildersList().add(new Shell("env | grep GIT_USERNAME > auth.txt; env | grep GIT_PASSWORD >> auth.txt;"));
        }
        r.configRoundtrip((Item) prj);
        SecretBuildWrapper wrapper = prj.getBuildWrappersList().get(SecretBuildWrapper.class);
        assertThat(wrapper, is(notNullValue()));
        List<? extends MultiBinding<?>> bindings = wrapper.getBindings();
        assertThat(bindings.size(), is(1));
        MultiBinding<?> binding = bindings.get(0);
        FreeStyleBuild run = prj.scheduleBuild2(0).waitForStart();
        if (isCliGitTool()) {
            assertThat(((GitUsernamePasswordBinding) binding).getCliGitTool(run, ((GitUsernamePasswordBinding) binding).getGitToolName(), TaskListener.NULL),
                    is(notNullValue()));
        } else {
            assertThat(((GitUsernamePasswordBinding) binding).getCliGitTool(run, ((GitUsernamePasswordBinding) binding).getGitToolName(), TaskListener.NULL),
                    is(nullValue()));
        }
        r.waitForCompletion(run);
        r.assertBuildStatusSuccess(run);
    }

    @Test
    public void test_getGitClientInstance() throws IOException, InterruptedException {
        if (isCliGitTool()) {
            assertThat(gitCredBind.getGitClientInstance(gitToolInstance.getGitExe(), rootFilePath,
                    new EnvVars(), TaskListener.NULL), instanceOf(CliGitAPIImpl.class));
        } else {
            assertThat(gitCredBind.getGitClientInstance(gitToolInstance.getGitExe(), rootFilePath,
                    new EnvVars(), TaskListener.NULL), not(instanceOf(CliGitAPIImpl.class)));
        }
    }

    @Test
    public void test_GenerateGitScript_write() throws IOException, InterruptedException {
        GitUsernamePasswordBinding.GenerateGitScript tempGenScript = new GitUsernamePasswordBinding.GenerateGitScript(this.username, this.password, credentials.getId(), !isWindows());
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
