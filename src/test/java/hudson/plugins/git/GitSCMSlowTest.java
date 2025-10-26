package hudson.plugins.git;

import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.User;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.ChangelogToBranch;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.plugins.git.extensions.impl.SparseCheckoutPath;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.plugins.git.CliGitCommand;
import jenkins.security.MasterToSlaveCallable;

import static hudson.Functions.isWindows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.storage.file.UserConfigFile;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.jvnet.hudson.test.Issue;

/**
 * Split the slow tests out of GitSCMTest.
 *
 * @author Mark Waite
 */
@TestMethodOrder(MethodOrderer.Random.class)
class GitSCMSlowTest extends AbstractGitTestCase {

    private final Random random = new Random();
    private boolean useChangelogToBranch = random.nextBoolean();
    private static boolean gpgsignEnabled = false; // set by gpgsignCheck()

    @BeforeAll
    static void beforeAll() throws Exception {
        SystemReader.getInstance().getUserConfig().clear();
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();

        File userGitConfig = new File(System.getProperty("user.home"), ".gitconfig");
        File xdgGitConfig = userGitConfig;
        String xdgDirName = System.getenv("XDG_CONFIG_HOME");
        if (xdgDirName != null) {
            xdgGitConfig = new File(xdgDirName, ".gitconfig");
        }
        UserConfigFile userConfig = new UserConfigFile(null, userGitConfig, xdgGitConfig, FS.DETECTED);
        userConfig.load();
        gpgsignEnabled = userConfig.getBoolean(ConfigConstants.CONFIG_COMMIT_SECTION, ConfigConstants.CONFIG_KEY_GPGSIGN, false) ||
                         userConfig.getBoolean(ConfigConstants.CONFIG_TAG_SECTION, ConfigConstants.CONFIG_KEY_GPGSIGN, false);
    }

    private static final Instant START_TIME = Instant.now();

    private static final int MAX_SECONDS_FOR_THESE_TESTS = 180;

    private boolean isTimeAvailable() {
        String env = System.getenv("CI");
        if (!Boolean.parseBoolean(env)) {
            // Run all tests when not in CI environment
            return true;
        }
        return Duration.between(START_TIME, Instant.now()).toSeconds() <= MAX_SECONDS_FOR_THESE_TESTS;
    }

    private void addChangelogToBranchExtension(GitSCM scm) {
        if (useChangelogToBranch) {
            /* Changelog should be no different with this enabled or disabled */
            ChangelogToBranchOptions changelogOptions = new ChangelogToBranchOptions("origin", "master");
            scm.getExtensions().add(new ChangelogToBranch(changelogOptions));
        }
        useChangelogToBranch = !useChangelogToBranch;
    }

    /*
     * Makes sure that git browser URL is preserved across config round trip.
     */
    @Issue("JENKINS-22604")
    @Test
    void testConfigRoundtripURLPreserved() throws Exception {
        /* Long running test of low value on Windows
         * Only run on non-Windows and approximately 50% of test runs
         * On Windows, it requires 24 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject p = createFreeStyleProject();
        final String url = "https://github.com/jenkinsci/jenkins";
        GitRepositoryBrowser browser = new GithubWeb(url);
        GitSCM scm = new GitSCM(createRepoList(url),
                Collections.singletonList(new BranchSpec("")),
                browser, null, null);
        p.setScm(scm);
        r.configRoundtrip(p);
        r.assertEqualDataBoundBeans(scm, p.getScm());
        assertEquals("git " + url, scm.getKey(), "Wrong key");
    }

    private List<UserRemoteConfig> createRepoList(String url) {
        List<UserRemoteConfig> repoList = new ArrayList<>();
        repoList.add(new UserRemoteConfig(url, null, null, null));
        return repoList;
    }

    /*
     * Makes sure that git extensions are preserved across config round trip.
     */
    @Issue("JENKINS-33695")
    @Test
    void testConfigRoundtripExtensionsPreserved() throws Exception {
        /* Long running test of low value on Windows
         * Only run on non-Windows and approximately 50% of test runs
         * On Windows, it requires 26 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject p = createFreeStyleProject();
        final String url = "https://github.com/jenkinsci/git-plugin.git";
        GitRepositoryBrowser browser = new GithubWeb(url);
        GitSCM scm = new GitSCM(createRepoList(url),
                Collections.singletonList(new BranchSpec("*/master")),
                browser, null, null);
        p.setScm(scm);

        /* Assert that no extensions are loaded initially */
        assertEquals(Collections.emptyList(), scm.getExtensions().toList());

        /* Add LocalBranch extension */
        LocalBranch localBranchExtension = new LocalBranch("**");
        scm.getExtensions().add(localBranchExtension);
        assertTrue(scm.getExtensions().toList().contains(localBranchExtension));

        /* Save the configuration */
        p = r.configRoundtrip(p);
        List<GitSCMExtension> extensions = scm.getExtensions().toList();
        assertTrue(extensions.contains(localBranchExtension));
        assertEquals(1, extensions.size(), "Wrong extension count before reload");
        r.assertEqualDataBoundBeans(browser, p.getScm().getBrowser());

        /* Reload configuration from disc */
        p.doReload();
        GitSCM reloadedGit = (GitSCM) p.getScm();
        List<GitSCMExtension> reloadedExtensions = reloadedGit.getExtensions().toList();
        assertEquals(1, reloadedExtensions.size(), "Wrong extension count after reload");
        LocalBranch reloadedLocalBranch = (LocalBranch) reloadedExtensions.get(0);
        assertEquals(localBranchExtension.getLocalBranch(), reloadedLocalBranch.getLocalBranch());
        r.assertEqualDataBoundBeans(browser, reloadedGit.getBrowser());
    }

    /*
     * Makes sure that the configuration form works.
     */
    @Test
    void testConfigRoundtrip() throws Exception {
        /* Long running test of low value on Windows.
         * Only run on non-Windows and approximately 50% of test runs
         * On Windows, it requires 20 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject p = createFreeStyleProject();
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/jenkins");
        p.setScm(scm);
        r.configRoundtrip(p);
        r.assertEqualDataBoundBeans(scm, p.getScm());
    }

    @Test
    void testBuildChooserContext() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        final FreeStyleProject p = createFreeStyleProject();
        final FreeStyleBuild b = r.buildAndAssertSuccess(p);

        GitSCM.BuildChooserContextImpl c = new GitSCM.BuildChooserContextImpl(p, b, null);
        c.actOnBuild((BuildChooserContext.ContextCallable<Run<?, ?>, Object>) (param, channel) -> {
            assertSame(param, b);
            return null;
        });
        c.actOnProject((BuildChooserContext.ContextCallable<Job<?, ?>, Object>) (param, channel) -> {
            assertSame(param, p);
            return null;
        });
        DumbSlave agent = r.createOnlineSlave();
        assertEquals(p.toString(), agent.getChannel().call(new GitSCMSlowTest.BuildChooserContextTestCallable(c)));
    }

    private static class BuildChooserContextTestCallable extends MasterToSlaveCallable<String, IOException> {

        private final BuildChooserContext c;

        public BuildChooserContextTestCallable(BuildChooserContext c) {
            this.c = c;
        }

        @Override
        public String call() throws IOException {
            try {
                // Can not be a lamda -> SecurityException: Rejected: java.lang.invoke.SerializedLambda; see https://jenkins.io/redirect/class-filter/
                return c.actOnProject(new BuildChooserContext.ContextCallable<Job<?, ?>, String>() {
                    @Override
                    public String invoke(Job<?, ?> param, VirtualChannel channel) throws IOException, InterruptedException {
                        assertInstanceOf(Channel.class, channel);
                        assertNotNull(Jenkins.getInstanceOrNull());
                        return param.toString();
                    }
                });
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
    }

    @Test
    void testMergeFailedWithAgent() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(r.createSlave().getSelfLabel());

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        commit(commitFile1, "other content", johnDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");
        r.buildAndAssertStatus(Result.FAILURE, project);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
    }

    @Test
    void testMergeWithAgent() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(r.createSlave().getSelfLabel());

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new TestPreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
    }

    /**
     * because of auto gpgsign we must disable it at repo level
     */
    public static class TestPreBuildMerge extends PreBuildMerge {
        public TestPreBuildMerge(UserMergeOptions options) {
            super(options);
        }

        @Override
        public GitClient decorate(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
            GitClient gitClient = super.decorate(scm, git);
            gitClient.config(GitClient.ConfigLevel.LOCAL, "commit.gpgsign", "false");
            gitClient.config(GitClient.ConfigLevel.LOCAL, "tag.gpgSign", "false");
            return gitClient;
        }
    }

    @Test
    void testMergeWithMatrixBuild() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        /* The testMergeWithMatrixBuild test fails randomly on several
         * machines when commit.gpgsign and tag.gpgsign are not
         * enabled if the TestPreBuildMerge implementation is used. It
         * passes consistently when PreBuildMerge is used. Rather than
         * spend the time trying to diagnose the intermittent
         * failures, this configuration allows the test to be skipped
         * if either of those configuration settings are enabled.
         *
         * Other tests in this class are able to use TestPreBuildMerge
         * without issue.
         */
        assumeFalse(gpgsignEnabled, "gpgsign enabled");
        //Create a matrix project and a couple of axes
        MatrixProject project = r.jenkins.createProject(MatrixProject.class, "xyz");
        project.setAxes(new AxisList(new Axis("VAR", "a", "b")));

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final MatrixBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");
        final MatrixBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
    }

    @Test
    void testInitSparseCheckout() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupProject("master", Collections.singletonList(new SparseCheckoutPath("toto")));

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("toto").exists());
        assertTrue(build1.getWorkspace().child(commitFile1).exists());
        assertFalse(build1.getWorkspace().child("titi").exists());
        assertFalse(build1.getWorkspace().child(commitFile2).exists());
    }

    @Test
    void testInitSparseCheckoutBis() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupProject("master", Collections.singletonList(new SparseCheckoutPath("titi")));

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("titi").exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        assertFalse(build1.getWorkspace().child("toto").exists());
        assertFalse(build1.getWorkspace().child(commitFile1).exists());
    }

    @Test
    void testInitSparseCheckoutOverAgent() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupProject("master", Collections.singletonList(new SparseCheckoutPath("titi")));
        project.setAssignedLabel(r.createSlave().getSelfLabel());

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("titi").exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        assertFalse(build1.getWorkspace().child("toto").exists());
        assertFalse(build1.getWorkspace().child(commitFile1).exists());
    }

    @Issue("HUDSON-7411")
    @Test
    void testNodeEnvVarsAvailable() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");
        DumbSlave agent = r.createSlave();
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("TESTKEY", "agent value"));
        project.setAssignedLabel(agent.getSelfLabel());
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertEquals("agent value", getEnvVars(project).get("TESTKEY"));
    }

    @Test
    void testBasicWithAgent() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(r.createSlave().getSelfLabel());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals(1, culprits.size(), "The build should have only one culprit");
        assertEquals(janeDoe.getName(), culprits.iterator().next().getFullName(), "");
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
    }
}
