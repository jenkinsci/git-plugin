package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.*;
import hudson.plugins.git.GitTool;
import hudson.tools.ToolProperty;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.trait.SCMSourceTrait;

import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.plugins.gitclient.JGitApacheTool;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.io.FileMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

/**
 * The test aims to functionally validate "estimation of size" and "git implementation recommendation" from a
 * cached directory and from plugin extensions.
 */
public class GitToolChooserTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    static final String GitBranchSCMHead_DEV_MASTER = "[GitBranchSCMHead{name='dev', ref='refs/heads/dev'}, GitBranchSCMHead{name='master', ref='refs/heads/master'}]";

    private CredentialsStore store = null;

    @Before
    public void enableSystemCredentialsProvider() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.<Credentials>emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.get())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;
            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
    }

    /*
    In the event of having no cache but extension APIs in the ExtensionList, the estimator should recommend a tool
    instead of recommending no git implementation.
     */
    @Test
    public void testSizeEstimationWithNoGitCache() throws Exception {
        sampleRepo.init();
        GitSCMSource instance = new GitSCMSource("https://github.com/rishabhBudhouliya/git-plugin.git");

        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        //Since no installation is provided, the gitExe will be git
        String gitExe = "git";

        GitToolChooser repoSizeEstimator = new GitToolChooser(instance.getRemote(), list.get(0), "github", gitExe, true);
        String tool = repoSizeEstimator.getGitTool();

        // The class should make recommendation because of APIs implementation even though
        // it can't find a .git cached directory
        assertThat(tool, is(not("NONE")));

        // If size were reported as 0, should return NONE
        assertThat(repoSizeEstimator.determineSwitchOnSize(0L, gitExe), is("NONE"));
    }

    /*
    In the case of having a cached .git repository, the estimator class should estimate the size of the local checked
    out repository and ultimately provide a suggestion on the base of decided heuristic.
     */
    @Test
    public void testSizeEstimationWithGitCache() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "lightweight");
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=dev2");
        sampleRepo.git("tag", "-a", "annotated", "-m", "annotated");
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=dev3");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[]", source.fetch(listener).toString());
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new JGitTool(Collections.<ToolProperty<?>>emptyList());

        // Add a JGit tool to the Jenkins instance to let the estimator find and recommend "jgit"
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);

        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        GitToolChooser repoSizeEstimator = new GitToolChooser(source.getRemote(), list.get(0), "github", tool.getGitExe(), true);
        /*
        Since the size of repository is 21.785 KiBs, the estimator should suggest "jgit" as an implementation
         */
        assertThat(repoSizeEstimator.getGitTool(), containsString("jgit"));
    }

    /*
    In the event of having an extension which returns the size of repository as 10000 KiB, the estimator should
    recommend "git" as the optimal implementation from the heuristics
     */
    @Test
    public void testSizeEstimationWithAPIForGit() throws Exception {
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();
        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed and git is present in the machine
        String gitExe = "git";

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", gitExe, true);
        assertThat(sizeEstimator.getGitTool(), containsString("git"));
    }

    /*
    In the event of having an extension which returns the size of repository as 500 KiB, the estimator should
    recommend "jgit" as the optimal implementation from the heuristics
     */
    @Test
    public void testSizeEstimationWithAPIForJGit() throws Exception {
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);


        buildAProject(sampleRepo, false);
        List<TopLevelItem> list = jenkins.jenkins.getItems();

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", tool.getGitExe(), true);
        assertThat(sizeEstimator.getGitTool(), containsString("jgit"));
    }

    /*
    In the event of having an extension which is not applicable to the remote URL provided by the git plugin,
    the estimator recommends no git implementation
     */
    @Test
    public void testSizeEstimationWithBitbucketAPIs() throws Exception {
        String remote = "https://bitbucket.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();
        buildAProject(sampleRepo, false);
        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed by user and git is present in the machine
        String gitExe = "git";

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", gitExe,true);
        assertThat(sizeEstimator.getGitTool(), is("NONE"));
    }

    /*
    In the event of having an extension which is applicable to the remote URL but throws an exception due to some
    reason from the implemented git provider plugin, the estimator handles the exception by silently logging an
    "INFO" message and returns no recommendation.
     */
    @Test
    public void testSizeEstimationWithException() throws Exception {
        String remote = "https://bitbucket.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();
        buildAProject(sampleRepo, false);
        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed by user and git is present in the machine
        String gitExe = "git";

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", gitExe, true);

        assertThat(sizeEstimator.getGitTool(), is("NONE"));
    }

    /*
    In case of having no user credentials, the git plugin expects the `implementers` of the extension point to handle
    and try querying for size of repo, if it throws an exception we catch it and recommend "NONE", i.e, no recommendation.
     */
    @Test
    public void testSizeEstimationWithNoCredentials() throws Exception {
        sampleRepo.init();

        buildAProject(sampleRepo, true);
        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed by user and git is present in the machine
        String gitExe = "git";

        GitToolChooser sizeEstimator = new GitToolChooser(sampleRepo.toString(), list.get(0), null, gitExe, true);

        assertThat(sizeEstimator.getGitTool(), is("NONE"));
    }

    /*
    Tests related to git tool resolution
    Scenario 1: Size of repo is < 5 MiB, "jgit" should be recommended
     */
    @Test
    public void testGitToolChooserWithCustomGitTool() throws Exception {
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", SystemUtils.IS_OS_WINDOWS ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, tool.getGitExe(), true);

        //According to size of repo, "jgit" should be recommended but it is not installed by the user
        //Hence, in this case GitToolChooser resolve gitExe as the user configured `home` value
        assertThat(gitToolChooser.getGitTool(), is(SystemUtils.IS_OS_WINDOWS ? "git.exe" : "git"));

    }

    @Test
    public void testGitToolChooserWithBothGitAndJGit() throws Exception {
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", SystemUtils.IS_OS_WINDOWS ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        GitTool jgitTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool, jgitTool);

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, tool.getGitExe(), true);
        assertThat(gitToolChooser.getGitTool(), is("jgit"));
    }

    /*
    According to the size of repo, GitToolChooser will recommend "jgit" even if "jgitapache" is present
     */
    @Test
    public void testGitToolChooserWithAllTools() throws Exception {
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", "/usr/bin/git", Collections.<ToolProperty<?>>emptyList());
        GitTool jgitTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        GitTool jGitApacheTool = new JGitApacheTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool, jgitTool, jGitApacheTool);

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, tool.getGitExe(), true);
        assertThat(gitToolChooser.getGitTool(), is("jgit"));
    }

    /*
    If the user has chosen `jgitapache` and the system contains "cli git" and "jgitapache", GitToolChooser should
    recommend `jgitapache`
     */
    @Test
    public void testGitToolChooserWithJGitApache() throws Exception {
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", "/usr/bin/git", Collections.<ToolProperty<?>>emptyList());
        GitTool jGitApacheTool = new JGitApacheTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool, jGitApacheTool);

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, jGitApacheTool.getGitExe(), true);
        assertThat(gitToolChooser.getGitTool(), is("jgitapache"));
    }

    /*
    According to the size of repo, GitToolChooser will recommend "jgitapache" since that is user's configured choice
     */
    @Test
    public void testGitToolChooserWithJGitApacheAndGit() throws Exception {
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        // With JGit, we don't ask the name and home of the tool
        GitTool jGitApacheTool = new JGitApacheTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(jGitApacheTool);

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, jGitApacheTool.getGitExe(), true);
        assertThat(gitToolChooser.getGitTool(), is("jgitapache"));
    }

    /*
    Tests related to git tool resolution
    Scenario 2: Size of repo is > 5 MiB, "git" should be recommended
     */
    @Test
    public void testGitToolChooserWithDefaultTool() throws Exception {
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();
        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed and git is present in the machine
        String gitExe = "git";

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", gitExe, true);
        assertThat(sizeEstimator.getGitTool(), containsString("git"));
    }

    @Test
    public void testGitToolChooserWithOnlyJGit() throws Exception {
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        // With JGit, we don't ask the name and home of the tool
        GitTool jGitTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(jGitTool);

        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed and git is present in the machine
        String gitExe = jGitTool.getGitExe();

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", gitExe, true);
        assertThat(sizeEstimator.getGitTool(), is("jgit"));
    }

    @Test
    public void testGitToolChooserWithCustomGitTool_2() throws Exception {
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", SystemUtils.IS_OS_WINDOWS ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);

        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed and git is present in the machine
        String gitExe = tool.getGitExe();

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", gitExe, true);
        assertThat(sizeEstimator.getGitTool(), is(SystemUtils.IS_OS_WINDOWS ? "git.exe" : "git"));
    }

    @Test
    public void testGitToolChooserWithAllTools_2() throws Exception {
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", SystemUtils.IS_OS_WINDOWS ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        GitTool jgitTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        GitTool jGitApacheTool = new JGitApacheTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool, jgitTool, jGitApacheTool);

        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed and git is present in the machine
        String gitExe = tool.getGitExe();

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", gitExe, true);
        assertThat(sizeEstimator.getGitTool(), is(SystemUtils.IS_OS_WINDOWS ? "git.exe" : "git"));
    }

    @Test
    @Issue("JENKINS-63541")
    public void getCacheDirCreatesNoDirectory() throws Exception {
        // Generate a unique repository name and compute expected cache directory
        String remoteName = "https://github.com/jenkinsci/git-plugin-" + java.util.UUID.randomUUID().toString() + ".git";
        String cacheEntry = AbstractGitSCMSource.getCacheEntry(remoteName);
        File expectedCacheDir = new File(new File(jenkins.jenkins.getRootDir(), "caches"), cacheEntry);

        // Directory should not exist
        assertThat(expectedCacheDir, is(not(anExistingFileOrDirectory())));

        // Getting the cache directory will not create an empty directory
        File nullCacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry, false);
        assertThat(nullCacheDir, is(nullValue()));
        assertThat(expectedCacheDir, is(not(anExistingFileOrDirectory())));

        // Getting the cache directory will create an empty directory
        File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry, true);
        assertThat(cacheDir, is(anExistingDirectory()));
        assertThat(expectedCacheDir, is(anExistingDirectory()));
    }

    /*
    A test extension implemented to clone the behavior of a plugin extending the capability of providing the size of
    repo from a remote URL of "Github".
     */
    @TestExtension
    public static class TestExtensionGithub extends GitToolChooser.RepositorySizeAPI {

        @Override
        public boolean isApplicableTo(String remote, Item context, String credentialsId) {
            return remote.contains("github");
        }

        @Override
        public Long getSizeOfRepository(String remote, Item context, String credentialsId) {
            // from remote, remove .git and https://github.com
            return (long) 500;
        }
    }

    /*
    A test extension implemented to clone the behavior of a plugin extending the capability of providing the size of
    repo from a remote URL of "GitLab".
     */
    @TestExtension
    public static class TestExtensionGitlab extends GitToolChooser.RepositorySizeAPI {

        @Override
        public boolean isApplicableTo(String remote, Item context, String credentialsId) {
            return remote.contains("gitlab");
        }

        @Override
        public Long getSizeOfRepository(String remote, Item context, String credentialsId) {
            // from remote, remove .git and https://github.com
            return (long) 10000;
        }
    }

    /*
    A test extension implemented to clone the behavior of a plugin extending the capability of providing the size of
    repo from a remote URL of "BitBucket".
     */
    @TestExtension
    public static class TestExtensionBit extends GitToolChooser.RepositorySizeAPI {

        @Override
        public boolean isApplicableTo(String remote, Item context, String credentialsId) {
            return remote.contains("bit");
        }

        @Override
        public Long getSizeOfRepository(String remote, Item context, String credentialsId) throws IOException {
            throw new IOException();
        }
    }


    private void buildAProject(GitSampleRepoRule sampleRepo, boolean noCredentials) throws Exception {
        WorkflowJob p = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', \n"
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = jenkins.assertBuildStatusSuccess(p.scheduleBuild2(0));
        if (!noCredentials) {
            jenkins.waitForMessage("using credential github", b);
        }
    }

    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password");
    }

}
