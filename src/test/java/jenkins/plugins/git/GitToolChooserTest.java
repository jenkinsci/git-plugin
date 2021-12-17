package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.FilePath;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.plugins.git.GitTool;
import hudson.slaves.DumbSlave;
import hudson.tools.*;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.trait.SCMSourceTrait;

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
import java.util.Random;
import java.util.Set;

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

    private static Random random = new Random();

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

    @Before
    public void resetRepositorySizeCache() {
        GitToolChooser.clearRepositorySizeCache();
    }

    /*
    This test checks the GitToolChooser in a scenario where repo size>5M, user's choice is `jgit`.
    In the event of having no node-specific installations, GitToolChooser will choose to return the default installation.
     */
    @Issue("JENKINS-63519")
    @Test
    public void testResolveGitTool() throws IOException, InterruptedException {
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        GitTool JTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool, JTool);

        GitToolChooser r = new GitToolChooser(remote, context, credentialsId, JTool, null, TaskListener.NULL,true);

        assertThat(r.getGitTool(), containsString(isWindows() ? "git.exe" : "git"));
    }

    /*
    This test checks the GitToolChooser in a scenario where repo size>5M, user's choice is `jgit`.
    There is no specific agent(node=null). In this case agent = Jenkins.get().
    In the event of running the GitToolChooser on the agent, it should correctly predict the git installation for
    that specific agent.
     */
    @Issue("JENKINS-63519")
    @Test
    public void testResolveGitToolWithJenkins() throws IOException, InterruptedException {
        if (isWindows()) { // Runs on Unix only
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        TestToolInstaller inst = new TestToolInstaller(jenkins.jenkins.getSelfLabel().getName(), "echo Hello", "updated/git");
        GitTool t = new GitTool("myGit", "default/git", Collections.singletonList(
                new InstallSourceProperty(Collections.singletonList(inst))));

        GitTool tool = new GitTool("my-git", "git", Collections.<ToolProperty<?>>emptyList());
        GitTool JTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool, JTool, t);

        GitToolChooser r = new GitToolChooser(remote, context, credentialsId, JTool, null, TaskListener.NULL,true);

        assertThat(r.getGitTool(), containsString("updated/git"));
    }

    /*
    This test checks the GitToolChooser in a scenario where repo size>5M, user's choice is `jgit`.
    There is an agent labeled -> `agent-windows`
    In the event of running the GitToolChooser on the agent, it should correctly predict the git installation for
    that specific agent.
     */
    @Issue("JENKINS-63519")
    @Test
    public void testResolutionGitToolOnAgent() throws Exception {
        if (isWindows()) { // Runs on Unix only
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        LabelAtom label = new LabelAtom("agent-windows");
        DumbSlave agent = jenkins.createOnlineSlave(label);
        agent.setMode(Node.Mode.NORMAL);
        agent.setLabelString("agent-windows");

        TestToolInstaller inst = new TestToolInstaller(jenkins.jenkins.getSelfLabel().getName(), "echo Hello", "myGit/git");
        GitTool toolOnMaster = new GitTool("myGit", "default/git", Collections.singletonList(
                new InstallSourceProperty(Collections.singletonList(inst))));

        TestToolInstaller instonAgent = new TestToolInstaller("agent-windows", "echo Hello", "my-git/git");
        GitTool toolOnAgent = new GitTool("my-git", "git", Collections.singletonList(new InstallSourceProperty(Collections.singletonList(instonAgent))));

        GitTool JTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());

        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(toolOnMaster, toolOnAgent, JTool);
        agent.getNodeProperties().add(new ToolLocationNodeProperty(new ToolLocationNodeProperty.ToolLocation(
                jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class), toolOnMaster.getName(), toolOnMaster.getHome())));

        agent.getNodeProperties().add(new ToolLocationNodeProperty(new ToolLocationNodeProperty.ToolLocation(
                jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class), toolOnAgent.getName(), toolOnAgent.getHome())));

        agent.getNodeProperties().add(new ToolLocationNodeProperty(new ToolLocationNodeProperty.ToolLocation(
                jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class), JTool.getName(), null)));

        GitToolChooser r = new GitToolChooser(remote, context, credentialsId, JTool, agent, TaskListener.NULL,true);

        assertThat(r.getGitTool(), containsString("my-git/git"));
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
        GitTool rTool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());

        GitToolChooser repoSizeEstimator = new GitToolChooser(instance.getRemote(), list.get(0), "github", rTool, null, TaskListener.NULL, true);
        String tool = repoSizeEstimator.getGitTool();

        // The class get a size < 5M from APIs and wants to recommend `jgit` but will return NONE instead
        // as `jgit` is not enabled by the user
        assertThat(tool, is("NONE"));

        // If size were reported as 0, should return NONE
        assertThat(repoSizeEstimator.determineSwitchOnSize(0L, rTool), is("NONE"));
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

        GitToolChooser repoSizeEstimator = new GitToolChooser(source.getRemote(), list.get(0), "github", tool, null,TaskListener.NULL,true);
        /*
        Since the size of repository is 21.785 KiBs, the estimator should suggest "jgit" as an implementation
         */
        assertThat(repoSizeEstimator.getGitTool(), containsString("jgit"));

        /* Confirm that a remote with a different name still finds the git cache */
        String permutedRemote = source.getRemote();
        String suffix = ".git";
        if (permutedRemote.endsWith(suffix)) {
            permutedRemote = permutedRemote.substring(0, permutedRemote.length() - suffix.length()); // Remove trailing ".git" suffix
        } else {
            permutedRemote = permutedRemote + suffix; // Add trailing ".git" suffix
        }
        GitToolChooser permutedRepoSizeEstimator = new GitToolChooser(permutedRemote, list.get(0), "github", tool, null, TaskListener.NULL, true);
        assertThat("Alternative repository name should find the cache",
                permutedRepoSizeEstimator.getGitTool(), containsString("jgit"));
    }

    /* Test the remoteAlternatives permutation of git repo URLs */
    @Test
    @Issue("JENKINS-63539")
    public void testRemoteAlternatives() throws Exception {
        GitTool tool = new JGitTool(Collections.<ToolProperty<?>>emptyList());

        GitToolChooser nullRemoteSizeEstimator = new GitToolChooser("git://example.com/git/git.git", null, null, tool, null, TaskListener.NULL, true);
        assertThat(nullRemoteSizeEstimator.remoteAlternatives(null), is(empty()));
        assertThat(nullRemoteSizeEstimator.remoteAlternatives(""), is(empty()));

        /* Borrow the nullRemoteSizer to also test determineSwitchOnSize a little more */
        long sizeOfRepo = 1 + random.nextInt(4000);
        assertThat(nullRemoteSizeEstimator.determineSwitchOnSize(sizeOfRepo, tool), is("NONE"));

        /* Each of these alternatives is expected to be interpreted as
         * a valid alias for every other alternative in the list.
         */
        String[] remoteAlternatives = {
                "git://example.com/jenkinsci/git-plugin",
                "git://example.com/jenkinsci/git-plugin.git",
                "git@example.com:jenkinsci/git-plugin",
                "git@example.com:jenkinsci/git-plugin.git",
                "https://example.com/jenkinsci/git-plugin",
                "https://example.com/jenkinsci/git-plugin.git",
                "ssh://git@example.com/jenkinsci/git-plugin",
                "ssh://git@example.com/jenkinsci/git-plugin.git",
        };

        for (String remote : remoteAlternatives) {
            GitToolChooser sizeEstimator = new GitToolChooser(remote, null, null, tool, null, TaskListener.NULL, random.nextBoolean());
            Set<String> alternatives = sizeEstimator.remoteAlternatives(remote);
            assertThat("Remote: " + remote, alternatives, containsInAnyOrder(remoteAlternatives));
        }

        /* Test remote that ends with '/' */
        for (String remote : remoteAlternatives) {
            remote = remote + "/";
            GitToolChooser sizeEstimator = new GitToolChooser(remote, null, null, tool, null, TaskListener.NULL, random.nextBoolean());
            Set<String> alternatives = sizeEstimator.remoteAlternatives(remote);
            assertThat("Remote+'/': " + remote, alternatives, containsInAnyOrder(remoteAlternatives));
        }
    }

    /* Test conversion of any remote alternative of git repo URLs to a standard URL */
    @Test
    public void testConvertToCanonicalURL() throws Exception {
        GitTool tool = new JGitTool(Collections.<ToolProperty<?>>emptyList());

        String[] remoteAlternatives = {
                "git://example.com/jenkinsci/git-plugin",
                "git://example.com/jenkinsci/git-plugin.git",
                "git@example.com:jenkinsci/git-plugin",
                "git@example.com:jenkinsci/git-plugin.git",
                "https://example.com/jenkinsci/git-plugin",
                "https://example.com/jenkinsci/git-plugin.git",
                "ssh://git@example.com/jenkinsci/git-plugin",
                "ssh://git@example.com/jenkinsci/git-plugin.git",
        };

        String actualNormalizedURL = "https://example.com/jenkinsci/git-plugin.git";

        for (String remote : remoteAlternatives) {
            GitToolChooser sizeEstimator = new GitToolChooser(remote, null, null, tool, null, TaskListener.NULL, random.nextBoolean());
            String expectedNormalizedURL = sizeEstimator.convertToCanonicalURL(remote);
            assertThat("Remote: " + remote, expectedNormalizedURL, is(actualNormalizedURL));
        }

        /* Check behavior in case of any other format of git repo URL*/
        String otherRemote = "file://srv/git/repo";
        GitToolChooser sizeEstimator = new GitToolChooser(otherRemote, null, null, tool, null, TaskListener.NULL, random.nextBoolean());
        assertThat("Remote: " + otherRemote, sizeEstimator.convertToCanonicalURL(otherRemote), is(otherRemote + ".git"));
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
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());


        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", tool, null, TaskListener.NULL,true);
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

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", tool, null, TaskListener.NULL,true);
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
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", tool,null, TaskListener.NULL,true);
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
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", tool,null, TaskListener.NULL,true);

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
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());

        GitToolChooser sizeEstimator = new GitToolChooser(sampleRepo.toString(), list.get(0), null, tool, null, TaskListener.NULL,true);

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
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, tool, null, TaskListener.NULL,true);

        //According to size of repo, "jgit" should be recommended but it is not installed by the user
        //Hence, in this case GitToolChooser should return a NONE
        assertThat(gitToolChooser.getGitTool(), is("NONE"));

    }

    @Test
    public void testGitToolChooserWithBothGitAndJGit() throws Exception {
        String remote = "https://github.com/rishabhBudhouliya/git-plugin.git";
        Item context = Mockito.mock(Item.class);
        String credentialsId = null;

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        GitTool jgitTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool, jgitTool);

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, tool, null, TaskListener.NULL,true);
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

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, tool, null, TaskListener.NULL,true);
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

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, jGitApacheTool, null, TaskListener.NULL,true);
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

        GitToolChooser gitToolChooser = new GitToolChooser(remote, context, credentialsId, jGitApacheTool, null, TaskListener.NULL,true);
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
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", tool, null, TaskListener.NULL,true);
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

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", jGitTool, null, TaskListener.NULL,true);
        assertThat(sizeEstimator.getGitTool(), is("jgit")); // Since git is not available, we suggest `jgit` which doesn't make any difference
    }

    @Test
    public void testGitToolChooserWithCustomGitTool_2() throws Exception {
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);

        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed and git is present in the machine
        String gitExe = tool.getGitExe();

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", tool, null, TaskListener.NULL,true);
        assertThat(sizeEstimator.getGitTool(), is(isWindows() ? "git.exe" : "git"));
    }

    @Test
    public void testGitToolChooserWithAllTools_2() throws Exception {
        String remote = "https://gitlab.com/rishabhBudhouliya/git-plugin.git";
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        // With JGit, we don't ask the name and home of the tool
        GitTool tool = new GitTool("my-git", isWindows() ? "git.exe" : "git", Collections.<ToolProperty<?>>emptyList());
        GitTool jgitTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());
        GitTool jGitApacheTool = new JGitApacheTool(Collections.<ToolProperty<?>>emptyList());
        jenkins.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool, jgitTool, jGitApacheTool);

        buildAProject(sampleRepo, false);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Assuming no tool is installed and git is present in the machine
        String gitExe = tool.getGitExe();

        GitToolChooser sizeEstimator = new GitToolChooser(remote, list.get(0), "github", tool, null, TaskListener.NULL,true);
        assertThat(sizeEstimator.getGitTool(), is(isWindows() ? "git.exe" : "git"));
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

    /* Do not throw null pointer excception if remote configuration is empty. */
    @Test
    @Issue("JENKINS-63572")
    public void testSizeEstimationWithNoRemoteConfig() throws Exception {
        sampleRepo.init();

        failAProject(sampleRepo);

        List<TopLevelItem> list = jenkins.jenkins.getItems();

        // Use JGit as the git tool for this NPE check
        GitTool jgitTool = new JGitTool(Collections.<ToolProperty<?>>emptyList());

        GitToolChooser sizeEstimator = new GitToolChooser(sampleRepo.toString(), list.get(0), null, jgitTool, null, TaskListener.NULL, true);

        assertThat(sizeEstimator.getGitTool(), is("NONE"));
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

    private static class TestToolInstaller extends CommandInstaller {

        private boolean invoked;

        public TestToolInstaller(String label, String command, String toolHome) {
            super(label, command, toolHome);
        }

        public boolean isInvoked() {
            return invoked;
        }

        @Override
        public FilePath performInstallation(ToolInstallation toolInstallation, Node node, TaskListener taskListener) throws IOException, InterruptedException {
            taskListener.error("Hello, world!");
            invoked = true;
            return super.performInstallation(toolInstallation, node, taskListener);
        }
    }


    private void buildAProject(GitSampleRepoRule sampleRepo, boolean noCredentials) throws Exception {
        WorkflowJob p = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', \n"
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]]\n"
                        + "  )\n"
                        + "  def tokenBranch = tm '${GIT_BRANCH,fullName=false}'\n"
                        + "  echo \"token macro expanded branch is ${tokenBranch}\"\n"
                        + "}", true));
        WorkflowRun b = jenkins.buildAndAssertSuccess(p);
        if (!noCredentials) {
            jenkins.waitForMessage("using credential github", b);
        }
        jenkins.waitForMessage("token macro expanded branch is remotes/origin/master", b); // Unexpected but current behavior
    }

    /* Attempt to perform a checkout without defining a remote repository. Expected to fail, but should not report NPE */
    private void failAProject(GitSampleRepoRule sampleRepo) throws Exception {
        WorkflowJob p = jenkins.jenkins.createProject(WorkflowJob.class, "intentionally-failing-job-without-remote-config");
        p.setDefinition(new CpsFlowDefinition("node {\n"
                                              + "  checkout(\n"
                                              + "    [$class: 'GitSCM']\n"
                                              + "  )\n"
                                              + "}", true));
        WorkflowRun b = jenkins.buildAndAssertStatus(hudson.model.Result.FAILURE, p);
        jenkins.waitForMessage("Couldn't find any revision to build", b);
    }

    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password");
    }

    /** inline ${@link hudson.Functions#isWindows()} to prevent a transient remote classloader issue */
    private boolean isWindows() {
        return File.pathSeparatorChar==';';
    }
}
