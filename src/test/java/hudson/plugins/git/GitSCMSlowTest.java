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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.plugins.git.CliGitCommand;
import jenkins.security.MasterToSlaveCallable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.util.SystemReader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Split the slow tests out of GitSCMTest.
 *
 * @author Mark Waite
 */
public class GitSCMSlowTest extends AbstractGitTestCase {

    private final Random random = new Random();
    private boolean useChangelogToBranch = random.nextBoolean();

    @BeforeClass
    public static void setGitDefaults() throws Exception {
        SystemReader.getInstance().getUserConfig().clear();
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
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
    public void testConfigRoundtripURLPreserved() throws Exception {
        /* Long running test of low value on Windows
         * Only run on non-Windows and approximately 50% of test runs
         * On Windows, it requires 24 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        FreeStyleProject p = createFreeStyleProject();
        final String url = "https://github.com/jenkinsci/jenkins";
        GitRepositoryBrowser browser = new GithubWeb(url);
        GitSCM scm = new GitSCM(createRepoList(url),
                Collections.singletonList(new BranchSpec("")),
                browser, null, null);
        p.setScm(scm);
        rule.configRoundtrip(p);
        rule.assertEqualDataBoundBeans(scm, p.getScm());
        assertEquals("Wrong key", "git " + url, scm.getKey());
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
    public void testConfigRoundtripExtensionsPreserved() throws Exception {
        /* Long running test of low value on Windows
         * Only run on non-Windows and approximately 50% of test runs
         * On Windows, it requires 26 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
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
        rule.configRoundtrip(p);
        List<GitSCMExtension> extensions = scm.getExtensions().toList();
        assertTrue(extensions.contains(localBranchExtension));
        assertEquals("Wrong extension count before reload", 1, extensions.size());

        /* Reload configuration from disc */
        p.doReload();
        GitSCM reloadedGit = (GitSCM) p.getScm();
        List<GitSCMExtension> reloadedExtensions = reloadedGit.getExtensions().toList();
        assertEquals("Wrong extension count after reload", 1, reloadedExtensions.size());
        LocalBranch reloadedLocalBranch = (LocalBranch) reloadedExtensions.get(0);
        assertEquals(localBranchExtension.getLocalBranch(), reloadedLocalBranch.getLocalBranch());
    }

    /*
     * Makes sure that the configuration form works.
     */
    @Test
    public void testConfigRoundtrip() throws Exception {
        /* Long running test of low value on Windows.
         * Only run on non-Windows and approximately 50% of test runs
         * On Windows, it requires 20 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        FreeStyleProject p = createFreeStyleProject();
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/jenkins");
        p.setScm(scm);
        rule.configRoundtrip(p);
        rule.assertEqualDataBoundBeans(scm, p.getScm());
    }

    @Test
    public void testBuildChooserContext() throws Exception {
        final FreeStyleProject p = createFreeStyleProject();
        final FreeStyleBuild b = rule.buildAndAssertSuccess(p);

        GitSCM.BuildChooserContextImpl c = new GitSCM.BuildChooserContextImpl(p, b, null);
        c.actOnBuild(new BuildChooserContext.ContextCallable<Run<?, ?>, Object>() {
            @Override
            public Object invoke(Run param, VirtualChannel channel) throws IOException, InterruptedException {
                assertSame(param, b);
                return null;
            }
        });
        c.actOnProject(new BuildChooserContext.ContextCallable<Job<?, ?>, Object>() {
            @Override
            public Object invoke(Job param, VirtualChannel channel) throws IOException, InterruptedException {
                assertSame(param, p);
                return null;
            }
        });
        DumbSlave agent = rule.createOnlineSlave();
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
                return c.actOnProject(new BuildChooserContext.ContextCallable<Job<?, ?>, String>() {
                    @Override
                    public String invoke(Job<?, ?> param, VirtualChannel channel) throws IOException, InterruptedException {
                        assertTrue(channel instanceof Channel);
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
    public void testMergeFailedWithAgent() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

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

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        commit(commitFile1, "other content", johnDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        rule.buildAndAssertStatus(Result.FAILURE, project);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testMergeWithAgent() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

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

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testMergeWithMatrixBuild() throws Exception {

        //Create a matrix project and a couple of axes
        MatrixProject project = rule.jenkins.createProject(MatrixProject.class, "xyz");
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

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        final MatrixBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testInitSparseCheckout() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
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
    public void testInitSparseCheckoutBis() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
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
    public void testInitSparseCheckoutOverAgent() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
        FreeStyleProject project = setupProject("master", Collections.singletonList(new SparseCheckoutPath("titi")));
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

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
    public void testNodeEnvVarsAvailable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        DumbSlave agent = rule.createSlave();
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("TESTKEY", "agent value"));
        project.setAssignedLabel(agent.getSelfLabel());
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertEquals("agent value", getEnvVars(project).get("TESTKEY"));
    }

    @Test
    public void testBasicWithAgent() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote classloader issue
     */
    private boolean isWindows() {
        return java.io.File.pathSeparatorChar == ';';
    }
}
