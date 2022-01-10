package hudson.plugins.git;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.plugins.git.GitSCM.BuildChooserContextImpl;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.*;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.util.BuildChooserContext.ContextCallable;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.GitUtils;
import hudson.plugins.parameterizedtrigger.BuildTrigger;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.tools.ToolLocationNodeProperty;
import hudson.tools.ToolProperty;
import hudson.triggers.SCMTrigger;
import hudson.util.LogTaskListener;
import hudson.util.RingBufferLogHandler;
import hudson.util.StreamTaskListener;

import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jenkinsci.plugins.gitclient.*;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;

import static org.jvnet.hudson.test.LoggerRule.recorded;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.transport.RemoteConfig;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jenkins.model.Jenkins;
import jenkins.plugins.git.CliGitCommand;
import jenkins.plugins.git.GitSampleRepoRule;

/**
 * Tests for {@link GitSCM}.
 * @author ishaaq
 */
public class GitSCMTest extends AbstractGitTestCase {
    @Rule
    public GitSampleRepoRule secondRepo = new GitSampleRepoRule();

    @Rule
    public LoggerRule logRule = new LoggerRule();

    private CredentialsStore store = null;

    @BeforeClass
    public static void setGitDefaults() throws Exception {
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
    }

    @Before
    public void enableSystemCredentialsProvider() throws Exception {
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

    @After
    public void waitForJenkinsIdle() throws Exception {
        if (cleanupIsUnreliable()) {
            rule.waitUntilNoActivityUpTo(5001);
        }
    }

    private StandardCredentials getInvalidCredential() {
        String username = "bad-user";
        String password = "bad-password";
        CredentialsScope scope = CredentialsScope.GLOBAL;
        String id = "username-" + username + "-password-" + password;
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, username, password);
    }

    @Test
    public void testAddGitTagAction() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        List<UserRemoteConfig> remoteConfigs = GitSCM.createRepoList("https://github.com/jenkinsci/git-plugin", "github");
        project.setScm(new GitSCM(remoteConfigs,
                Collections.singletonList(new BranchSpec("master")), false, null, null, null, null));

        GitSCM scm = (GitSCM) project.getScm();
        final DescriptorImpl descriptor = (DescriptorImpl) scm.getDescriptor();
        boolean originalValue = scm.isAddGitTagAction();
        assertFalse("Wrong initial value for hide tag action", originalValue);
        descriptor.setAddGitTagAction(true);
        assertTrue("Hide tag action not set", scm.isAddGitTagAction());
        descriptor.setAddGitTagAction(false);
        assertFalse("Wrong final value for hide tag action", scm.isAddGitTagAction());
        descriptor.setAddGitTagAction(originalValue); // restore original value of addGitTagAction

        /* Exit test early if running on Windows and path will be too long */
        /* Known limitation of git for Windows 2.28.0 and earlier */
        /* Needs a longpath fix in git for Windows */
        String currentDirectoryPath = new File(".").getCanonicalPath();
        if (isWindows() && currentDirectoryPath.length() > 95) {
            return;
        }

        logRule.record(GitSCM.class, Level.FINE).capture(20);

        // Build 1 will not add a tag action
        commit("commitFileWithoutGitTagAction", johnDoe, "Commit 1 without git tag action");
        build(project, Result.SUCCESS);
        assertThat(logRule, recorded(containsString("Not adding GitTagAction to build 1")));

        // Build 2 will add a tag action
        descriptor.setAddGitTagAction(true);
        build(project, Result.SUCCESS);
        assertThat(logRule, recorded(containsString("Adding GitTagAction to build 2")));

        // Build 3 will not add a tag action
        descriptor.setAddGitTagAction(false);
        build(project, Result.SUCCESS);
        assertThat(logRule, recorded(containsString("Not adding GitTagAction to build 3")));
    }

    @Test
    public void manageShouldAccessGlobalConfig() throws Exception {
        final String USER = "user";
        final String MANAGER = "manager";
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        rule.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   // Read access
                                                   .grant(Jenkins.READ).everywhere().to(USER)

                                                   // Read and Manage
                                                   .grant(Jenkins.READ).everywhere().to(MANAGER)
                                                   .grant(Jenkins.MANAGE).everywhere().to(MANAGER)
        );

        try (ACLContext c = ACL.as(User.getById(USER, true))) {
            Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
            assertThat("Global configuration should not be accessible to READ users", descriptors, is(empty()));
        }
        try (ACLContext c = ACL.as(User.getById(MANAGER, true))) {
            Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
            Optional<Descriptor> found =
                    descriptors.stream().filter(descriptor -> descriptor instanceof GitSCM.DescriptorImpl).findFirst();
            assertTrue("Global configuration should be accessible to MANAGE users", found.isPresent());
        }
    }

    @Test
    public void trackCredentials() throws Exception {
        StandardCredentials credential = getInvalidCredential();
        store.addCredentials(Domain.global(), credential);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should not be set before job definition", fingerprint, nullValue());

        JenkinsRule.WebClient wc = rule.createWebClient();
        HtmlPage page = wc.goTo("credentials/store/system/domain/_/credentials/" + credential.getId());
        assertThat("Have usage tracking reported", page.getElementById("usage"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-missing"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-present"), nullValue());

        FreeStyleProject project = setupProject("master", credential);

        fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should not be set before first build", fingerprint, nullValue());

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should be set after first build", fingerprint, notNullValue());
        assertThat(fingerprint.getJobs(), hasItem(is(project.getFullName())));
        Fingerprint.RangeSet rangeSet = fingerprint.getRangeSet(project);
        assertThat(rangeSet, notNullValue());
        assertThat(rangeSet.includes(project.getLastBuild().getNumber()), is(true));

        page = wc.goTo("credentials/store/system/domain/_/credentials/" + credential.getId());
        assertThat(page.getElementById("usage-missing"), nullValue());
        assertThat(page.getElementById("usage-present"), notNullValue());
        assertThat(page.getAnchorByText(project.getFullDisplayName()), notNullValue());
    }

    /**
     * Basic test - create a GitSCM based project, check it out and build for the first time.
     * Next test that polling works correctly, make another commit, check that polling finds it,
     * then build it and finally test the build culprits as well as the contents of the workspace.
     * @throws Exception on error
     */
    @Test
    public void testBasic() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

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

    @Test
    @Issue("JENKINS-56176")
    public void testBasicRemotePoll() throws Exception {
//        FreeStyleProject project = setupProject("master", true, false);
        FreeStyleProject project = setupProject("master", false, null, null, null, true, null);
        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        String sha1String = commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        // ... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // JENKINS-56176 token macro expansion broke when BuildData was no longer updated
        assertThat(TokenMacro.expandAll(build2, listener, "${GIT_REVISION,length=7}"), is(sha1String.substring(0, 7)));
        assertThat(TokenMacro.expandAll(build2, listener, "${GIT_REVISION}"), is(sha1String));
        assertThat(TokenMacro.expandAll(build2, listener, "$GIT_REVISION"), is(sha1String));
    }

    @Test
    public void testBranchSpecWithRemotesMaster() throws Exception {
        FreeStyleProject projectMasterBranch = setupProject("remotes/origin/master", false, null, null, null, true, null);
        // create initial commit and build
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(projectMasterBranch, Result.SUCCESS, commitFile1);
    }

    /**
     * This test and testSpecificRefspecsWithoutCloneOption confirm behaviors of
     * refspecs on initial clone. Without the CloneOption to honor refspec, all
     * references are cloned, even if they will be later ignored due to the
     * refspec.  With the CloneOption to ignore refspec, the initial clone also
     * honors the refspec and only retrieves references per the refspec.
     * @throws Exception on error
     */
    @Test
    @Issue("JENKINS-31393")
    public void testSpecificRefspecs() throws Exception {
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "+refs/heads/foo:refs/remotes/foo", null));

        /* Set CloneOption to honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        CloneOption cloneOptionMaster = new CloneOption(false, null, null);
        cloneOptionMaster.setHonorRefspec(true);
        ((GitSCM)projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);

        /* Set CloneOption to honor refspec on initial clone */
        FreeStyleProject projectWithFoo = setupProject(repos, Collections.singletonList(new BranchSpec("foo")), null, false, null);
        CloneOption cloneOptionFoo = new CloneOption(false, null, null);
        cloneOptionFoo.setHonorRefspec(true);
        ((GitSCM)projectWithMaster.getScm()).getExtensions().add(cloneOptionFoo);

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");
        // create branch and make initial commit
        git.checkout().ref("master").branch("foo").execute();
        commit(commitFile1, johnDoe, "Commit in foo");

        build(projectWithMaster, Result.FAILURE);
        build(projectWithFoo, Result.SUCCESS, commitFile1);
    }

    /**
     * This test confirms the behaviour of avoiding the second fetch in GitSCM checkout()
     **/
    @Test
    @Issue("JENKINS-56404")
    public void testAvoidRedundantFetch() throws Exception {
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "+refs/heads/*:refs/remotes/*", null));

        /* Without honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        if (random.nextBoolean()) {
            /* Randomly enable shallow clone, should not alter test assertions */
            CloneOption cloneOptionMaster = new CloneOption(false, null, null);
            cloneOptionMaster.setDepth(1);
            ((GitSCM) projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);
        }

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");

        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);

        assertRedundantFetchIsSkipped(build, "+refs/heads/*:refs/remotes/origin/*");
    }

    /**
     * After avoiding the second fetch call in retrieveChanges(), this test verifies there is no data loss by fetching a repository
     * (git init + git fetch) with a narrow refspec but without CloneOption of honorRefspec = true on initial clone
     * First fetch -> wide refspec
     * Second fetch -> narrow refspec (avoided)
     **/
    @Test
    @Issue("JENKINS-56404")
    public void testAvoidRedundantFetchWithoutHonorRefSpec() throws Exception {
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "+refs/heads/foo:refs/remotes/foo", null));

        /* Without honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        if (random.nextBoolean()) {
            /* Randomly enable shallow clone, should not alter test assertions */
            CloneOption cloneOptionMaster = new CloneOption(false, null, null);
            cloneOptionMaster.setDepth(1);
            ((GitSCM) projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);
        }

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");
        // Add another branch 'foo'
        git.checkout().ref("master").branch("foo").execute();
        commit(commitFile1, johnDoe, "Commit in foo");

        // Build will be success because the initial clone disregards refspec and fetches all branches
        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);
        FilePath childFile = returnFile(build);

        if (childFile != null) {
            // assert that no data is lost by avoidance of second fetch
            assertThat("master branch was not fetched", childFile.readToString(), containsString("master"));
            assertThat("foo branch was not fetched", childFile.readToString(), containsString("foo"));
        }

        String wideRefSpec = "+refs/heads/*:refs/remotes/origin/*";
        assertRedundantFetchIsSkipped(build, wideRefSpec);

        assertThat(build.getResult(), is(Result.SUCCESS));
    }

    /**
     * After avoiding the second fetch call in retrieveChanges(), this test verifies there is no data loss by fetching a
     * repository(git init + git fetch) with a narrow refspec with CloneOption of honorRefspec = true on initial clone
     * First fetch -> narrow refspec (since refspec is honored on initial clone)
     * Second fetch -> narrow refspec (avoided)
     **/
    @Test
    @Issue("JENKINS-56404")
    public void testAvoidRedundantFetchWithHonorRefSpec() throws Exception {
        List<UserRemoteConfig> repos = new ArrayList<>();
        String refSpec = "+refs/heads/foo:refs/remotes/foo";
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", refSpec, null));

        /* With honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        CloneOption cloneOptionMaster = new CloneOption(false, null, null);
        cloneOptionMaster.setHonorRefspec(true);
        ((GitSCM)projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);

        // create initial commit
        final String commitFile1 = "commitFile1";
        final String commitFile1SHA1a = commit(commitFile1, johnDoe, "Commit in master");
        // Add another branch 'foo'
        git.checkout().ref("master").branch("foo").execute();
        final String commitFile1SHA1b = commit(commitFile1, johnDoe, "Commit in foo");

        // Build will be failure because the initial clone regards refspec and fetches branch 'foo' only.
        FreeStyleBuild build = build(projectWithMaster, Result.FAILURE);

        FilePath childFile = returnFile(build);
        assertNotNull(childFile);
        // assert that no data is lost by avoidance of second fetch
        final String fetchHeadContents = childFile.readToString();
        final List<String> buildLog = build.getLog(50);
        assertThat("master branch was fetched: " + buildLog, fetchHeadContents, not(containsString("branch 'master'")));
        assertThat("foo branch was not fetched: " + buildLog, fetchHeadContents, containsString("branch 'foo'"));
        assertThat("master branch SHA1 '" + commitFile1SHA1a + "' fetched " + buildLog, fetchHeadContents, not(containsString(commitFile1SHA1a)));
        assertThat("foo branch SHA1 '" + commitFile1SHA1b + "' was not fetched " + buildLog, fetchHeadContents, containsString(commitFile1SHA1b));
        assertRedundantFetchIsSkipped(build, refSpec);

        assertThat(build.getResult(), is(Result.FAILURE));
    }

    @Test
    @Issue("JENKINS-49757")
    public void testAvoidRedundantFetchWithNullRefspec() throws Exception {
        String nullRefspec = null;
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", nullRefspec, null));

        /* Without honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        if (random.nextBoolean()) {
            /* Randomly enable shallow clone, should not alter test assertions */
            CloneOption cloneOptionMaster = new CloneOption(false, null, null);
            cloneOptionMaster.setDepth(1);
            ((GitSCM) projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);
        }

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");

        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);

        assertRedundantFetchIsSkipped(build, "+refs/heads/*:refs/remotes/origin/*");
    }

    /*
     * When initial clone does not honor the refspec and a custom refspec is used
     * that is not part of the default refspec, then the second fetch is not
     * redundant and must not be fetched.
     *
     * This example uses the format to reference GitHub pull request 553. Other
     * formats would apply as well, but the case is illustrated well enough by
     * using the GitHub pull request as an example of this type of problem.
     */
    @Test
    @Issue("JENKINS-49757")
    public void testRetainRedundantFetch() throws Exception {
        String refspec = "+refs/heads/*:refs/remotes/origin/* +refs/pull/553/head:refs/remotes/origin/pull/553";
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", refspec, null));

        /* Without honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        if (random.nextBoolean()) {
            /* Randomly enable shallow clone, should not alter test assertions */
            CloneOption cloneOptionMaster = new CloneOption(false, null, null);
            cloneOptionMaster.setDepth(1);
            ((GitSCM) projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);
        }

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");

        /* Create a ref for the fake pull in the source repository */
        String[] expectedResult = {""};
        CliGitCommand gitCmd = new CliGitCommand(testRepo.git, "update-ref", "refs/pull/553/head", "HEAD");
        assertThat(gitCmd.run(), is(expectedResult));

        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);

        assertRedundantFetchIsUsed(build, refspec);
    }

    /*
    * When "Preserve second fetch during checkout" is checked in during configuring Jenkins,
    * the second fetch should be retained
    */
    @Test
    @Issue("JENKINS-49757")
    public void testRetainRedundantFetchIfSecondFetchIsAllowed() throws Exception {
        String refspec = "+refs/heads/*:refs/remotes/*";
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", refspec, null));

        /* Without honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);

        GitSCM scm = (GitSCM) projectWithMaster.getScm();
        final DescriptorImpl descriptor = (DescriptorImpl) scm.getDescriptor();
        assertThat("Redundant fetch is skipped by default", scm.isAllowSecondFetch(), is(false));
        descriptor.setAllowSecondFetch(true);
        assertThat("Redundant fetch should be allowed", scm.isAllowSecondFetch(), is(true));

        if (random.nextBoolean()) {
            /* Randomly enable shallow clone, should not alter test assertions */
            CloneOption cloneOptionMaster = new CloneOption(false, null, null);
            cloneOptionMaster.setDepth(1);
            ((GitSCM) projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);
        }

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");

        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);

        assertRedundantFetchIsUsed(build, refspec);
    }

    // Checks if the second fetch is being avoided
    private void assertRedundantFetchIsSkipped(FreeStyleBuild build, String refSpec) throws IOException {
        assertRedundantFetchCount(build, refSpec, 1);
    }

    // Checks if the second fetch is being called
    private void assertRedundantFetchIsUsed(FreeStyleBuild build, String refSpec) throws IOException {
        assertRedundantFetchCount(build, refSpec, 2);
    }

    // Checks if the second fetch is being avoided
    private void assertRedundantFetchCount(FreeStyleBuild build, String refSpec, int expectedFetchCount) throws IOException {
        List<String> values = build.getLog(Integer.MAX_VALUE);

        //String fetchArg = " > git fetch --tags --force --progress -- " + testRepo.gitDir.getAbsolutePath() + argRefSpec + " # timeout=10";
        Pattern fetchPattern = Pattern.compile(".* git.* fetch .*");
        List<String> fetchCommands = values.stream().filter(fetchPattern.asPredicate()).collect(Collectors.toList());

        // After the fix, git fetch is called exactly once
        assertThat("Fetch commands were: " + fetchCommands, fetchCommands, hasSize(expectedFetchCount));
    }

    // Returns the file FETCH_HEAD found in .git
    private FilePath returnFile(FreeStyleBuild build) throws IOException, InterruptedException {
        List<FilePath> files = build.getProject().getWorkspace().list();
        FilePath resultFile = null;
        for (FilePath s : files) {
            if(s.getName().equals(".git")) {
                resultFile = s.child("FETCH_HEAD");
            }
        }
        return resultFile;
    }

    /**
     * This test and testSpecificRefspecs confirm behaviors of
     * refspecs on initial clone. Without the CloneOption to honor refspec, all
     * references are cloned, even if they will be later ignored due to the
     * refspec.  With the CloneOption to ignore refspec, the initial clone also
     * honors the refspec and only retrieves references per the refspec.
     * @throws Exception on error
     */
    @Test
    @Issue("JENKINS-36507")
    public void testSpecificRefspecsWithoutCloneOption() throws Exception {
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "+refs/heads/foo:refs/remotes/foo", null));
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        FreeStyleProject projectWithFoo = setupProject(repos, Collections.singletonList(new BranchSpec("foo")), null, false, null);

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");
        // create branch and make initial commit
        git.checkout().ref("master").branch("foo").execute();
        commit(commitFile1, johnDoe, "Commit in foo");

        build(projectWithMaster, Result.SUCCESS); /* If clone refspec had been honored, this would fail */
        build(projectWithFoo, Result.SUCCESS, commitFile1);
    }

    /**
     * An empty remote repo URL failed the job as expected but provided
     * a poor diagnostic message. The fix for JENKINS-38608 improves
     * the error message to be clear and helpful. This test checks for
     * that error message.
     * @throws Exception on error
     */
    @Test
    @Issue("JENKINS-38608")
    public void testAddFirstRepositoryWithNullRepoURL() throws Exception{
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(null, null, null, null));
        FreeStyleProject project = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        FreeStyleBuild build = build(project, Result.FAILURE);
        // Before JENKINS-38608 fix
        assertThat("Build log reports 'Null value not allowed'",
                   build.getLog(175), not(hasItem("Null value not allowed as an environment variable: GIT_URL")));
        // After JENKINS-38608 fix
        assertThat("Build log did not report empty string in job definition",
                   build.getLog(175), hasItem("FATAL: Git repository URL 1 is an empty string in job definition. Checkout requires a valid repository URL"));
    }

    /**
     * An empty remote repo URL failed the job as expected but provided
     * a poor diagnostic message. The fix for JENKINS-38608 improves
     * the error message to be clear and helpful. This test checks for
     * that error message when the second URL is empty.
     * @throws Exception on error
     */
    @Test
    @Issue("JENKINS-38608")
    public void testAddSecondRepositoryWithNullRepoURL() throws Exception{
        String repoURL = "https://example.com/non-empty/repo/url";
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(repoURL, null, null, null));
        repos.add(new UserRemoteConfig(null, null, null, null));
        FreeStyleProject project = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        FreeStyleBuild build = build(project, Result.FAILURE);
        // Before JENKINS-38608 fix
        assertThat("Build log reports 'Null value not allowed'",
                   build.getLog(175), not(hasItem("Null value not allowed as an environment variable: GIT_URL_2")));
        // After JENKINS-38608 fix
        assertThat("Build log did not report empty string in job definition for URL 2",
                   build.getLog(175), hasItem("FATAL: Git repository URL 2 is an empty string in job definition. Checkout requires a valid repository URL"));
    }

    @Test
    public void testBranchSpecWithRemotesHierarchical() throws Exception {
      FreeStyleProject projectMasterBranch = setupProject("master", false, null, null, null, true, null);
      FreeStyleProject projectHierarchicalBranch = setupProject("remotes/origin/rel-1/xy", false, null, null, null, true, null);
      // create initial commit
      final String commitFile1 = "commitFile1";
      commit(commitFile1, johnDoe, "Commit number 1");
      // create hierarchical branch, delete master branch, and build
      git.branch("rel-1/xy");
      git.checkout("rel-1/xy");
      git.deleteBranch("master");
      build(projectMasterBranch, Result.FAILURE);
      build(projectHierarchicalBranch, Result.SUCCESS, commitFile1);
    }

    @Test
    public void testBranchSpecUsingTagWithSlash() throws Exception {
        FreeStyleProject projectMasterBranch = setupProject("path/tag", false, null, null, null, true, null);
        // create initial commit and build
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1 will be tagged with path/tag");
        testRepo.git.tag("path/tag", "tag with a slash in the tag name");
        build(projectMasterBranch, Result.SUCCESS, commitFile1);
      }

    @Test
    public void testBasicIncludedRegion() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, null, ".*3");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should not have been included", project.poll(listener).hasChanges());

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.poll(listener).hasChanges());

        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());
        
        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    /**
     * testMergeCommitInExcludedRegionIsIgnored() confirms behavior of excluded regions with merge commits.
     * This test has excluded and included regions, for files ending with .excluded and .included,
     * respectively. The git repository is set up so that a non-fast-forward merge commit comes
     * to master. The newly merged commit is a file ending with .excluded, so it should be ignored.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389","JENKINS-23606"})
    @Test
    public void testMergeCommitInExcludedRegionIsIgnored() throws Exception {
        final String branchToMerge = "new-branch-we-merge-to-master";

        FreeStyleProject project = setupProject("master", false, null, ".*\\.excluded", null, ".*\\.included");

        final String initialCommit = "initialCommit";
        commit(initialCommit, johnDoe, "Commit " + initialCommit + " to master");
        build(project, Result.SUCCESS, initialCommit);
        final String secondCommit = "secondCommit";
        commit(secondCommit, johnDoe, "Commit " + secondCommit + " to master");

        testRepo.git.checkoutBranch(branchToMerge, "HEAD~");
        final String fileToMerge = "fileToMerge.excluded";
        commit(fileToMerge, johnDoe, "Commit should be ignored: " + fileToMerge + " to " + branchToMerge);

        ObjectId branchSHA = git.revParse("HEAD");
        testRepo.git.checkoutBranch("master", "refs/heads/master");
        MergeCommand mergeCommand = testRepo.git.merge();
        mergeCommand.setRevisionToMerge(branchSHA);
        mergeCommand.execute();

        // Should return false, because our merge commit falls within the excluded region.
        assertFalse("Polling should report no changes, because they are in the excluded region.",
                project.poll(listener).hasChanges());
    }

    /**
     * testMergeCommitInExcludedDirectoryIsIgnored() confirms behavior of excluded directories with merge commits.
     * This test has excluded and included directories, named /excluded/ and /included/,respectively. The repository
     * is set up so that a non-fast-forward merge commit comes to master, and is in the directory /excluded/,
     * so it should be ignored.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389","JENKINS-23606"})
    @Test
    public void testMergeCommitInExcludedDirectoryIsIgnored() throws Exception {
        final String branchToMerge = "new-branch-we-merge-to-master";

        FreeStyleProject project = setupProject("master", false, null, "excluded/.*", null, "included/.*");

        final String initialCommit = "initialCommit";
        commit(initialCommit, johnDoe, "Commit " + initialCommit + " to master");
        build(project, Result.SUCCESS, initialCommit);
        final String secondCommit = "secondCommit";
        commit(secondCommit, johnDoe, "Commit " + secondCommit + " to master");

        testRepo.git.checkoutBranch(branchToMerge, "HEAD~");
        final String fileToMerge = "excluded/should-be-ignored";
        commit(fileToMerge, johnDoe, "Commit should be ignored: " + fileToMerge + " to " + branchToMerge);

        ObjectId branchSHA = git.revParse("HEAD");
        testRepo.git.checkoutBranch("master", "refs/heads/master");
        MergeCommand mergeCommand = testRepo.git.merge();
        mergeCommand.setRevisionToMerge(branchSHA);
        mergeCommand.execute();

        // Should return false, because our merge commit falls within the excluded directory.
        assertFalse("Polling should see no changes, because they are in the excluded directory.",
                project.poll(listener).hasChanges());
    }

    /**
     * testMergeCommitInIncludedRegionIsProcessed() confirms behavior of included regions with merge commits.
     * This test has excluded and included regions, for files ending with .excluded and .included, respectively.
     * The git repository is set up so that a non-fast-forward merge commit comes to master. The newly merged
     * commit is a file ending with .included, so it should be processed as a new change.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389","JENKINS-23606"})
    @Test
    public void testMergeCommitInIncludedRegionIsProcessed() throws Exception {
        final String branchToMerge = "new-branch-we-merge-to-master";

        FreeStyleProject project = setupProject("master", false, null, ".*\\.excluded", null, ".*\\.included");

        final String initialCommit = "initialCommit";
        commit(initialCommit, johnDoe, "Commit " + initialCommit + " to master");
        build(project, Result.SUCCESS, initialCommit);

        final String secondCommit = "secondCommit";
        commit(secondCommit, johnDoe, "Commit " + secondCommit + " to master");

        testRepo.git.checkoutBranch(branchToMerge, "HEAD~");
        final String fileToMerge = "fileToMerge.included";
        commit(fileToMerge, johnDoe, "Commit should be noticed and processed as a change: " + fileToMerge + " to " + branchToMerge);

        ObjectId branchSHA = git.revParse("HEAD");
        testRepo.git.checkoutBranch("master", "refs/heads/master");
        MergeCommand mergeCommand = testRepo.git.merge();
        mergeCommand.setRevisionToMerge(branchSHA);
        mergeCommand.execute();

        // Should return true, because our commit falls within the included region.
        assertTrue("Polling should report changes, because they fall within the included region.",
                project.poll(listener).hasChanges());
    }

    /**
     * testMergeCommitInIncludedRegionIsProcessed() confirms behavior of included directories with merge commits.
     * This test has excluded and included directories, named /excluded/ and /included/, respectively. The repository
     * is set up so that a non-fast-forward merge commit comes to master, and is in the directory /included/,
     * so it should be processed as a new change.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389","JENKINS-23606"})
    @Test
    public void testMergeCommitInIncludedDirectoryIsProcessed() throws Exception {
        final String branchToMerge = "new-branch-we-merge-to-master";

        FreeStyleProject project = setupProject("master", false, null, "excluded/.*", null, "included/.*");

        final String initialCommit = "initialCommit";
        commit(initialCommit, johnDoe, "Commit " + initialCommit + " to master");
        build(project, Result.SUCCESS, initialCommit);

        final String secondCommit = "secondCommit";
        commit(secondCommit, johnDoe, "Commit " + secondCommit + " to master");

        testRepo.git.checkoutBranch(branchToMerge, "HEAD~");
        final String fileToMerge = "included/should-be-processed";
        commit(fileToMerge, johnDoe, "Commit should be noticed and processed as a change: " + fileToMerge + " to " + branchToMerge);

        ObjectId branchSHA = git.revParse("HEAD");
        testRepo.git.checkoutBranch("master", "refs/heads/master");
        MergeCommand mergeCommand = testRepo.git.merge();
        mergeCommand.setRevisionToMerge(branchSHA);
        mergeCommand.execute();

        // When this test passes, project.poll(listener).hasChanges()) should return
        // true, because our commit falls within the included region.
        assertTrue("Polling should report changes, because they are in the included directory.",
                project.poll(listener).hasChanges());
    }

    /**
     * testMergeCommitOutsideIncludedRegionIsIgnored() confirms behavior of included regions with merge commits.
     * This test has an included region defined, for files ending with .included. There is no excluded region
     * defined. The repository is set up and a non-fast-forward merge commit comes to master. The newly merged commit
     * is a file ending with .should-be-ignored, thus falling outside of the included region, so it should ignored.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389","JENKINS-23606"})
    @Test
    public void testMergeCommitOutsideIncludedRegionIsIgnored() throws Exception {
        final String branchToMerge = "new-branch-we-merge-to-master";

        FreeStyleProject project = setupProject("master", false, null, null, null, ".*\\.included");

        final String initialCommit = "initialCommit";
        commit(initialCommit, johnDoe, "Commit " + initialCommit + " to master");
        build(project, Result.SUCCESS, initialCommit);

        final String secondCommit = "secondCommit";
        commit(secondCommit, johnDoe, "Commit " + secondCommit + " to master");

        testRepo.git.checkoutBranch(branchToMerge, "HEAD~");
        final String fileToMerge = "fileToMerge.should-be-ignored";
        commit(fileToMerge, johnDoe, "Commit should be ignored: " + fileToMerge + " to " + branchToMerge);

        ObjectId branchSHA = git.revParse("HEAD");
        testRepo.git.checkoutBranch("master", "refs/heads/master");
        MergeCommand mergeCommand = testRepo.git.merge();
        mergeCommand.setRevisionToMerge(branchSHA);
        mergeCommand.execute();

        // Should return false, because our commit falls outside the included region.
        assertFalse("Polling should ignore the change, because it falls outside the included region.",
                project.poll(listener).hasChanges());
    }

    /**
     * testMergeCommitOutsideIncludedDirectoryIsIgnored() confirms behavior of included directories with merge commits.
     * This test has only an included directory `/included`  defined. The git repository is set up so that
     * a non-fast-forward, but mergeable, commit comes to master. The newly merged commit is outside of the
     * /included/ directory, so polling should report no changes.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389","JENKINS-23606"})
    @Test
    public void testMergeCommitOutsideIncludedDirectoryIsIgnored() throws Exception {
        final String branchToMerge = "new-branch-we-merge-to-master";

        FreeStyleProject project = setupProject("master", false, null, null, null, "included/.*");

        final String initialCommit = "initialCommit";
        commit(initialCommit, johnDoe, "Commit " + initialCommit + " to master");
        build(project, Result.SUCCESS, initialCommit);

        final String secondCommit = "secondCommit";
        commit(secondCommit, johnDoe, "Commit " + secondCommit + " to master");

        testRepo.git.checkoutBranch(branchToMerge, "HEAD~");
        final String fileToMerge = "directory-to-ignore/file-should-be-ignored";
        commit(fileToMerge, johnDoe, "Commit should be ignored: " + fileToMerge + " to " + branchToMerge);

        ObjectId branchSHA = git.revParse("HEAD");
        testRepo.git.checkoutBranch("master", "refs/heads/master");
        MergeCommand mergeCommand = testRepo.git.merge();
        mergeCommand.setRevisionToMerge(branchSHA);
        mergeCommand.execute();

        // Should return false, because our commit falls outside of the included directory
        assertFalse("Polling should ignore the change, because it falls outside the included directory.",
                project.poll(listener).hasChanges());
    }

    /**
     * testMergeCommitOutsideExcludedRegionIsProcessed() confirms behavior of excluded regions with merge commits.
     * This test has an excluded region defined, for files ending with .excluded. There is no included region defined.
     * The repository is set up so a non-fast-forward merge commit comes to master. The newly merged commit is a file
     * ending with .should-be-processed, thus falling outside of the excluded region, so it should processed
     * as a new change.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389","JENKINS-23606"})
    @Test
    public void testMergeCommitOutsideExcludedRegionIsProcessed() throws Exception {
        final String branchToMerge = "new-branch-we-merge-to-master";

        FreeStyleProject project = setupProject("master", false, null, ".*\\.excluded", null, null);

        final String initialCommit = "initialCommit";
        commit(initialCommit, johnDoe, "Commit " + initialCommit + " to master");
        build(project, Result.SUCCESS, initialCommit);

        final String secondCommit = "secondCommit";
        commit(secondCommit, johnDoe, "Commit " + secondCommit + " to master");

        testRepo.git.checkoutBranch(branchToMerge, "HEAD~");
        final String fileToMerge = "fileToMerge.should-be-processed";
        commit(fileToMerge, johnDoe, "Commit should be noticed and processed as a change: " + fileToMerge + " to " + branchToMerge);

        ObjectId branchSHA = git.revParse("HEAD");
        testRepo.git.checkoutBranch("master", "refs/heads/master");
        MergeCommand mergeCommand = testRepo.git.merge();
        mergeCommand.setRevisionToMerge(branchSHA);
        mergeCommand.execute();

        // Should return true, because our commit falls outside of the excluded region
        assertTrue("Polling should process the change, because it falls outside the excluded region.",
                project.poll(listener).hasChanges());
    }

    /**
     * testMergeCommitOutsideExcludedDirectoryIsProcessed() confirms behavior of excluded directories with merge commits.
     * This test has an excluded directory `excluded` defined. There is no `included` directory defined. The repository
     * is set up so that a non-fast-forward merge commit comes to master. The newly merged commit resides in a
     * directory of its own, thus falling outside of the excluded directory, so it should processed
     * as a new change.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389","JENKINS-23606"})
    @Test
    public void testMergeCommitOutsideExcludedDirectoryIsProcessed() throws Exception {
        final String branchToMerge = "new-branch-we-merge-to-master";

        FreeStyleProject project = setupProject("master", false, null, "excluded/.*", null, null);

        final String initialCommit = "initialCommit";
        commit(initialCommit, johnDoe, "Commit " + initialCommit + " to master");
        build(project, Result.SUCCESS, initialCommit);

        final String secondCommit = "secondCommit";
        commit(secondCommit, johnDoe, "Commit " + secondCommit + " to master");

        testRepo.git.checkoutBranch(branchToMerge, "HEAD~");
        // Create this new file outside of our excluded directory
        final String fileToMerge = "directory-to-include/file-should-be-processed";
        commit(fileToMerge, johnDoe, "Commit should be noticed and processed as a change: " + fileToMerge + " to " + branchToMerge);

        ObjectId branchSHA = git.revParse("HEAD");
        testRepo.git.checkoutBranch("master", "refs/heads/master");
        MergeCommand mergeCommand = testRepo.git.merge();
        mergeCommand.setRevisionToMerge(branchSHA);
        mergeCommand.execute();

        // Should return true, because our commit falls outside of the excluded directory
        assertTrue("SCM polling should process the change, because it falls outside the excluded directory.",
                project.poll(listener).hasChanges());
    }

    @Test
    public void testIncludedRegionWithDeeperCommits() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, null, ".*3");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should not have been included", project.poll(listener).hasChanges());
        

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        
        final String commitFile4 = "commitFile4";
        commit(commitFile4, janeDoe, "Commit number 4");
        assertTrue("scm polling did not detect commit3 change", project.poll(listener).hasChanges());

        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());
        
        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testBasicExcludedRegion() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, ".*2", null, null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should have been excluded", project.poll(listener).hasChanges());

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());

        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    private int findLogLineStartsWith(List<String> buildLog, String initialString) {
        int logLine = 0;
        for (String logString : buildLog) {
            if (logString.startsWith(initialString)) {
                return logLine;
            }
            logLine++;
        }
        return -1;
    }

    @Test
    public void testCleanBeforeCheckout() throws Exception {
    	FreeStyleProject p = setupProject("master", false, null, null, "Jane Doe", null);
        ((GitSCM)p.getScm()).getExtensions().add(new CleanBeforeCheckout());

        /* First build should not clean, since initial clone is always clean */
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(p, Result.SUCCESS, commitFile1);
        assertThat(firstBuild.getLog(50), not(hasItem("Cleaning workspace")));
        /* Second build should clean, since first build might have modified the workspace */
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        final FreeStyleBuild secondBuild = build(p, Result.SUCCESS, commitFile2);
        List<String> secondLog = secondBuild.getLog(50);
        assertThat(secondLog, hasItem("Cleaning workspace"));
        int cleaningLogLine = findLogLineStartsWith(secondLog, "Cleaning workspace");
        int fetchingLogLine = findLogLineStartsWith(secondLog, "Fetching upstream changes from ");
        assertThat("Cleaning should happen before fetch", cleaningLogLine, is(lessThan(fetchingLogLine)));
    }

    @Issue("JENKINS-8342")
    @Test
    public void testExcludedRegionMultiCommit() throws Exception {
        // Got 2 projects, each one should only build if changes in its own file
        FreeStyleProject clientProject = setupProject("master", false, null, ".*serverFile", null, null);
        FreeStyleProject serverProject = setupProject("master", false, null, ".*clientFile", null, null);
        String initialCommitFile = "initialFile";
        commit(initialCommitFile, johnDoe, "initial commit");
        build(clientProject, Result.SUCCESS, initialCommitFile);
        build(serverProject, Result.SUCCESS, initialCommitFile);

        assertFalse("scm polling should not detect any more changes after initial build", clientProject.poll(listener).hasChanges());
        assertFalse("scm polling should not detect any more changes after initial build", serverProject.poll(listener).hasChanges());

        // Got commits on serverFile, so only server project should build.
        commit("myserverFile", johnDoe, "commit first server file");

        assertFalse("scm polling should not detect any changes in client project", clientProject.poll(listener).hasChanges());
        assertTrue("scm polling did not detect changes in server project", serverProject.poll(listener).hasChanges());

        // Got commits on both client and serverFile, so both projects should build.
        commit("myNewserverFile", johnDoe, "commit new server file");
        commit("myclientFile", johnDoe, "commit first clientfile");

        assertTrue("scm polling did not detect changes in client project", clientProject.poll(listener).hasChanges());
        assertTrue("scm polling did not detect changes in server project", serverProject.poll(listener).hasChanges());
    }

    /*
     * With multiple branches specified in the project and having commits from a user
     * excluded should not build the excluded revisions when another branch changes.
     */
    /*
    @Issue("JENKINS-8342")
    @Test
    public void testMultipleBranchWithExcludedUser() throws Exception {
        final String branch1 = "Branch1";
        final String branch2 = "Branch2";

        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("master"));
        branches.add(new BranchSpec(branch1));
        branches.add(new BranchSpec(branch2));
        final FreeStyleProject project = setupProject(branches, false, null, null, janeDoe.getName(), null, false, null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        // create branches here so we can get back to them later...
        git.branch(branch1);
        git.branch(branch2);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling should detect changes in 'master' branch", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile1, commitFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());

        // Add excluded commit
        final String commitFile4 = "commitFile4";
        commit(commitFile4, janeDoe, "Commit number 4");
        assertFalse("scm polling detected change in 'master', which should have been excluded", project.poll(listener).hasChanges());

        // now jump back...
        git.checkout(branch1);
        final String branch1File1 = "branch1File1";
        commit(branch1File1, janeDoe, "Branch1 commit number 1");
        assertFalse("scm polling detected change in 'Branch1', which should have been excluded", project.poll(listener).hasChanges());

        // and the other branch...
        git.checkout(branch2);

        final String branch2File1 = "branch2File1";
        commit(branch2File1, janeDoe, "Branch2 commit number 1");
        assertFalse("scm polling detected change in 'Branch2', which should have been excluded", project.poll(listener).hasChanges());

        final String branch2File2 = "branch2File2";
        commit(branch2File2, johnDoe, "Branch2 commit number 2");
        assertTrue("scm polling should detect changes in 'Branch2' branch", project.poll(listener).hasChanges());

        //... and build it...
        build(project, Result.SUCCESS, branch2File1, branch2File2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        // now jump back again...
        git.checkout(branch1);

        // Commit excluded after non-excluded commit, should trigger build.
        final String branch1File2 = "branch1File2";
        commit(branch1File2, johnDoe, "Branch1 commit number 2");
        final String branch1File3 = "branch1File3";
        commit(branch1File3, janeDoe, "Branch1 commit number 3");
        assertTrue("scm polling should detect changes in 'Branch1' branch", project.poll(listener).hasChanges());

        build(project, Result.SUCCESS, branch1File1, branch1File2, branch1File3);
    } */

    @Test
    public void testBasicExcludedUser() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, "Jane Doe", null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should have been excluded", project.poll(listener).hasChanges());
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());

        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

    }

    @Test
    public void testBasicInSubdir() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        ((GitSCM)project.getScm()).getExtensions().add(new RelativeTargetDirectory("subdir"));

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, "subdir", Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, "subdir", Result.SUCCESS,
                                            commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertEquals("The workspace should have a 'subdir' subdirectory, but does not.", true,
                     build2.getWorkspace().child("subdir").exists());
        assertEquals("The 'subdir' subdirectory should contain commitFile2, but does not.", true,
                build2.getWorkspace().child("subdir").child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
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

    @Issue("HUDSON-7547")
    @Test
    public void testBasicWithAgentNoExecutorsOnMaster() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        rule.jenkins.setNumExecutors(0);

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

    @Test
    public void testAuthorOrCommitterFalse() throws Exception {
        // Test with authorOrCommitter set to false and make sure we get the committer.
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());

        final FreeStyleBuild secondBuild = build(project, Result.SUCCESS, commitFile2);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final Set<User> secondCulprits = secondBuild.getCulprits();

        assertEquals("The build should have only one culprit", 1, secondCulprits.size());
        assertEquals("Did not get the committer as the change author with authorOrCommitter==false",
                     janeDoe.getName(), secondCulprits.iterator().next().getFullName());
    }

    @Test
    public void testAuthorOrCommitterTrue() throws Exception {
        // Next, test with authorOrCommitter set to true and make sure we get the author.
        FreeStyleProject project = setupSimpleProject("master");
        ((GitSCM)project.getScm()).getExtensions().add(new AuthorInChangelog());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());

        final FreeStyleBuild secondBuild = build(project, Result.SUCCESS, commitFile2);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final Set<User> secondCulprits = secondBuild.getCulprits();

        assertEquals("The build should have only one culprit", 1, secondCulprits.size());
        assertEquals("Did not get the author as the change author with authorOrCommitter==true",
                johnDoe.getName(), secondCulprits.iterator().next().getFullName());
    }

    @Test
    public void testNewCommitToUntrackedBranchDoesNotTriggerBuild() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        //now create and checkout a new branch:
        git.checkout(Constants.HEAD, "untracked");
        //.. and commit to it:
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertFalse("scm polling should not detect commit2 change because it is not in the branch we are tracking.", project.poll(listener).hasChanges());
    }

    private String checkoutString(FreeStyleProject project, String envVar) {
        return "checkout -f " + getEnvVars(project).get(envVar);
    }

    @Test
    public void testEnvVarsAvailable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        assertEquals("origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
        rule.waitForMessage(getEnvVars(project).get(GitSCM.GIT_BRANCH), build1);

        rule.waitForMessage(checkoutString(project, GitSCM.GIT_COMMIT), build1);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);

        rule.assertLogNotContains(checkoutString(project, GitSCM.GIT_PREVIOUS_COMMIT), build2);
        rule.waitForMessage(checkoutString(project, GitSCM.GIT_PREVIOUS_COMMIT), build1);

        rule.assertLogNotContains(checkoutString(project, GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT), build2);
        rule.waitForMessage(checkoutString(project, GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT), build1);
    }

    @Issue("HUDSON-7411")
    @Test
    public void testNodeEnvVarsAvailable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        DumbSlave agent = rule.createSlave();
        setVariables(agent, new Entry("TESTKEY", "agent value"));
        project.setAssignedLabel(agent.getSelfLabel());
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertEquals("agent value", getEnvVars(project).get("TESTKEY"));
    }

    @Test
    public void testNodeOverrideGit() throws Exception {
        GitSCM scm = new GitSCM(null);

        DumbSlave agent = rule.createSlave();
        GitTool.DescriptorImpl gitToolDescriptor = rule.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class);
        GitTool installation = new GitTool("Default", "/usr/bin/git", null);
        gitToolDescriptor.setInstallations(installation);

        String gitExe = scm.getGitExe(agent, TaskListener.NULL);
        assertEquals("/usr/bin/git", gitExe);

        ToolLocationNodeProperty nodeGitLocation = new ToolLocationNodeProperty(new ToolLocationNodeProperty.ToolLocation(gitToolDescriptor, "Default", "C:\\Program Files\\Git\\bin\\git.exe"));
        agent.setNodeProperties(Collections.singletonList(nodeGitLocation));

        gitExe = scm.getGitExe(agent, TaskListener.NULL);
        assertEquals("C:\\Program Files\\Git\\bin\\git.exe", gitExe);
    }

    /*
     * A previous version of GitSCM would only build against branches, not tags. This test checks that that
     * regression has been fixed.
     */
    @Test
    public void testGitSCMCanBuildAgainstTags() throws Exception {
        final String mytag = "mytag";
        FreeStyleProject project = setupSimpleProject(mytag);
        build(project, Result.FAILURE); // fail, because there's nothing to be checked out here

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");

        // Try again. The first build will leave the repository in a bad state because we
        // cloned something without even a HEAD - which will mean it will want to re-clone once there is some
        // actual data.
        build(project, Result.FAILURE); // fail, because there's nothing to be checked out here

        //now create and checkout a new branch:
        final String tmpBranch = "tmp";
        git.branch(tmpBranch);
        git.checkout(tmpBranch);
        // commit to it
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertFalse("scm polling should not detect any more changes since mytag is untouched right now", project.poll(listener).hasChanges());
        build(project, Result.FAILURE);  // fail, because there's nothing to be checked out here

        // tag it, then delete the tmp branch
        git.tag(mytag, "mytag initial");
        git.checkout("master");
        git.deleteBranch(tmpBranch);

        // at this point we're back on master, there are no other branches, tag "mytag" exists but is
        // not part of "master"
        assertTrue("scm polling should detect commit2 change in 'mytag'", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());

        // now, create tmp branch again against mytag:
        git.checkout(mytag);
        git.branch(tmpBranch);
        // another commit:
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertFalse("scm polling should not detect any more changes since mytag is untouched right now", project.poll(listener).hasChanges());

        // now we're going to force mytag to point to the new commit, if everything goes well, gitSCM should pick the change up:
        git.tag(mytag, "mytag moved");
        git.checkout("master");
        git.deleteBranch(tmpBranch);

        // at this point we're back on master, there are no other branches, "mytag" has been updated to a new commit:
        assertTrue("scm polling should detect commit3 change in 'mytag'", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile3);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());
    }

    /*
     * Not specifying a branch string in the project implies that we should be polling for changes in
     * all branches.
     */
    @Test
    public void testMultipleBranchBuild() throws Exception {
        // empty string will result in a project that tracks against changes in all branches:
        final FreeStyleProject project = setupSimpleProject("");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        // create a branch here so we can get back to this point  later...
        final String fork = "fork";
        git.branch(fork);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling should detect changes in 'master' branch", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile1, commitFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());

        // now jump back...
        git.checkout(fork);

        // add some commits to the fork branch...
        final String forkFile1 = "forkFile1";
        commit(forkFile1, johnDoe, "Fork commit number 1");
        final String forkFile2 = "forkFile2";
        commit(forkFile2, johnDoe, "Fork commit number 2");
        assertTrue("scm polling should detect changes in 'fork' branch", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, forkFile1, forkFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());
    }

    @Test
    public void testMultipleBranchesWithTags() throws Exception {
        List<BranchSpec> branchSpecs = Arrays.asList(
                new BranchSpec("refs/tags/v*"),
                new BranchSpec("refs/remotes/origin/non-existent"));
        FreeStyleProject project = setupProject(branchSpecs, false, null, null, janeDoe.getName(), null, false, null);

        // create initial commit and then run the build against it:
        // Here the changelog is by default empty (because changelog for first commit is always empty
        commit("commitFileBase", johnDoe, "Initial Commit");

        // there are no branches to be build
        FreeStyleBuild freeStyleBuild = build(project, Result.FAILURE);

        final String v1 = "v1";

        git.tag(v1, "version 1");
        assertTrue("v1 tag exists", git.tagExists(v1));

        freeStyleBuild = build(project, Result.SUCCESS);
        assertTrue("change set is empty", freeStyleBuild.getChangeSet().isEmptySet());

        commit("file1", johnDoe, "change to file1");
        git.tag("none", "latest");

        freeStyleBuild = build(project, Result.SUCCESS);

        ObjectId tag = git.revParse(Constants.R_TAGS + v1);
        GitSCM scm = (GitSCM)project.getScm();
        BuildData buildData = scm.getBuildData(freeStyleBuild);

        assertEquals("last build matches the v1 tag revision", tag, buildData.lastBuild.getSHA1());
    }

    @Issue("JENKINS-19037")
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Test
    public void testBlankRepositoryName() throws Exception {
        new GitSCM(null);
    }

    @Issue("JENKINS-10060")
    @Test
    public void testSubmoduleFixup() throws Exception {
        /* Unreliable on Windows and not a platform specific test */
        if (isWindows()) {
            return;
        }
        File repo = secondRepo.getRoot();
        FilePath moduleWs = new FilePath(repo);
        org.jenkinsci.plugins.gitclient.GitClient moduleRepo = Git.with(listener, new EnvVars()).in(repo).getClient();

        {// first we create a Git repository with submodule
            moduleRepo.init();
            moduleWs.child("a").touch(0);
            moduleRepo.add("a");
            moduleRepo.commit("creating a module");

            git.addSubmodule(repo.getAbsolutePath(), "module1");
            git.commit("creating a super project");
        }

        // configure two uproject 'u' -> 'd' that's chained together.
        FreeStyleProject u = createFreeStyleProject();
        FreeStyleProject d = createFreeStyleProject();

        u.setScm(new GitSCM(workDir.getPath()));
        u.getPublishersList().add(new BuildTrigger(new hudson.plugins.parameterizedtrigger.BuildTriggerConfig(d.getName(), ResultCondition.SUCCESS,
                new GitRevisionBuildParameters())));

        d.setScm(new GitSCM(workDir.getPath()));
        rule.jenkins.rebuildDependencyGraph();


        FreeStyleBuild ub = rule.buildAndAssertSuccess(u);
        for  (int i=0; (d.getLastBuild()==null || d.getLastBuild().isBuilding()) && i<100; i++) // wait only up to 10 sec to avoid infinite loop
            Thread.sleep(100);

        FreeStyleBuild db = d.getLastBuild();
        assertNotNull("downstream build didn't happen",db);
        rule.assertBuildStatusSuccess(db);
    }

    @Test
    public void testBuildChooserContext() throws Exception {
        final FreeStyleProject p = createFreeStyleProject();
        final FreeStyleBuild b = rule.buildAndAssertSuccess(p);

        BuildChooserContextImpl c = new BuildChooserContextImpl(p, b, null);
        c.actOnBuild(new ContextCallable<Run<?,?>, Object>() {
            public Object invoke(Run param, VirtualChannel channel) throws IOException, InterruptedException {
                assertSame(param,b);
                return null;
            }
        });
        c.actOnProject(new ContextCallable<Job<?,?>, Object>() {
            public Object invoke(Job param, VirtualChannel channel) throws IOException, InterruptedException {
                assertSame(param,p);
                return null;
            }
        });
        DumbSlave agent = rule.createOnlineSlave();
        assertEquals(p.toString(), agent.getChannel().call(new BuildChooserContextTestCallable(c)));
    }

    private static class BuildChooserContextTestCallable extends MasterToSlaveCallable<String,IOException> {
        private final BuildChooserContext c;

        public BuildChooserContextTestCallable(BuildChooserContext c) {
            this.c = c;
        }

        public String call() throws IOException {
            try {
                return c.actOnProject(new ContextCallable<Job<?,?>, String>() {
                    public String invoke(Job<?,?> param, VirtualChannel channel) throws IOException, InterruptedException {
                        assertTrue(channel instanceof Channel);
                        assertTrue(Jenkins.getInstanceOrNull()!=null);
                        return param.toString();
                    }
                });
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

    }

    // eg: "jane doe and john doe should be the culprits", culprits, [johnDoe, janeDoe])
    static public void assertCulprits(String assertMsg, Set<User> actual, PersonIdent[] expected)
    {
        List<String> fullNames =
                actual.stream().map(User::getFullName).collect(Collectors.toList());

        for(PersonIdent p : expected)
        {
            assertTrue(assertMsg, fullNames.contains(p.getName()));
        }
    }

    @Test
    public void testHideCredentials() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        // setup global config
        List<UserRemoteConfig> remoteConfigs = GitSCM.createRepoList("https://github.com/jenkinsci/git-plugin", "github");
        project.setScm(new GitSCM(remoteConfigs,
                Collections.singletonList(new BranchSpec("master")), false, null, null, null, null));

        GitSCM scm = (GitSCM) project.getScm();
        final DescriptorImpl descriptor = (DescriptorImpl) scm.getDescriptor();
        assertFalse("Wrong initial value for hide credentials", scm.isHideCredentials());
        descriptor.setHideCredentials(true);
        assertTrue("Hide credentials not set", scm.isHideCredentials());

        /* Exit test early if running on Windows and path will be too long */
        /* Known limitation of git for Windows 2.28.0 and earlier */
        /* Needs a longpath fix in git for Windows */
        String currentDirectoryPath = new File(".").getCanonicalPath();
        if (isWindows() && currentDirectoryPath.length() > 95) {
            return;
        }

        descriptor.setHideCredentials(false);
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS);
        List<String> logLines = project.getLastBuild().getLog(100);
        assertThat(logLines, hasItem("using credential github"));

        descriptor.setHideCredentials(true);
        build(project, Result.SUCCESS);
        logLines = project.getLastBuild().getLog(100);
        assertThat(logLines, not(hasItem("using credential github")));

    }


    @Test
    public void testEmailCommitter() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // setup global config
        GitSCM scm = (GitSCM) project.getScm();
        final DescriptorImpl descriptor = (DescriptorImpl) scm.getDescriptor();
        assertFalse("Wrong initial value for create account based on e-mail", scm.isCreateAccountBasedOnEmail());
        descriptor.setCreateAccountBasedOnEmail(true);
        assertTrue("Create account based on e-mail not set", scm.isCreateAccountBasedOnEmail());

        assertFalse("Wrong initial value for use existing user if same e-mail already found", scm.isUseExistingAccountWithSameEmail());
        descriptor.setUseExistingAccountWithSameEmail(true);
        assertTrue("Use existing user if same e-mail already found is not set", scm.isUseExistingAccountWithSameEmail());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";

        final PersonIdent jeffDoe = new PersonIdent("Jeff Doe", "jeff@doe.com");
        commit(commitFile2, jeffDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...

        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();

        assertEquals("The build should have only one culprit", 1, culprits.size());
        User culprit = culprits.iterator().next();
        assertEquals("", jeffDoe.getEmailAddress(), culprit.getId());
        assertEquals("", jeffDoe.getName(), culprit.getFullName());

        rule.assertBuildStatusSuccess(build);
    }
    
    @Issue("JENKINS-59868")
    @Test
    public void testNonExistentWorkingDirectoryPoll() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        project.setScm(new GitSCM(
                ((GitSCM)project.getScm()).getUserRemoteConfigs(),
                Collections.singletonList(new BranchSpec("master")),
                null, null,
                // configure GitSCM with the DisableRemotePoll extension to ensure that polling use the workspace
                Collections.singletonList(new DisableRemotePoll())));
        FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        // Empty the workspace directory
        build1.getWorkspace().deleteRecursive();

        // Setup a recorder for polling logs
        RingBufferLogHandler pollLogHandler = new RingBufferLogHandler(10);
        Logger pollLogger = Logger.getLogger(GitSCMTest.class.getName());
        pollLogger.addHandler(pollLogHandler);
        TaskListener taskListener = new LogTaskListener(pollLogger, Level.INFO);

        // Make sure that polling returns BUILD_NOW and properly log the reason
        FilePath filePath = build1.getWorkspace();
        assertThat(project.getScm().compareRemoteRevisionWith(project, new Launcher.LocalLauncher(taskListener), 
                filePath, taskListener, null), is(PollingResult.BUILD_NOW));
        assertTrue(pollLogHandler.getView().stream().anyMatch(m -> 
                m.getMessage().contains("[poll] Working Directory does not exist")));
    }

    // Disabled - consistently fails, needs more analysis
    // @Test
    public void testFetchFromMultipleRepositories() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("second", secondRepo.getRoot(), listener);
        List<UserRemoteConfig> remotes = new ArrayList<>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        project.setScm(new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("master")),
                null, null,
                Collections.<GitSCMExtension>emptyList()));

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        /* Diagnostic help - for later use */
        SCMRevisionState baseline = project.poll(listener).baseline;
        Change change = project.poll(listener).change;
        SCMRevisionState remote = project.poll(listener).remote;
        String assertionMessage = MessageFormat.format("polling incorrectly detected change after build. Baseline: {0}, Change: {1}, Remote: {2}", baseline, change, remote);
        assertFalse(assertionMessage, project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        secondTestRepo.commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    private void branchSpecWithMultipleRepositories(String branchName) throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("second", secondRepo.getRoot(), listener);
        List<UserRemoteConfig> remotes = new ArrayList<>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");

        project.setScm(new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec(branchName)),
                null, null,
                Collections.<GitSCMExtension>emptyList()));

        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);
        rule.assertBuildStatusSuccess(build);
    }

    @Issue("JENKINS-26268")
    public void testBranchSpecAsSHA1WithMultipleRepositories() throws Exception {
        branchSpecWithMultipleRepositories(testRepo.git.revParse("HEAD").getName());
    }

    @Issue("JENKINS-26268")
    public void testBranchSpecAsRemotesOriginMasterWithMultipleRepositories() throws Exception {
        branchSpecWithMultipleRepositories("remotes/origin/master");
    }

    @Issue("JENKINS-25639")
    @Test
    public void testCommitDetectedOnlyOnceInMultipleRepositories() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("secondRepo", secondRepo.getRoot(), listener);
        List<UserRemoteConfig> remotes = new ArrayList<>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        GitSCM gitSCM = new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("origin/master")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(gitSCM);

        /* Check that polling would force build through
         * compareRemoteRevisionWith by detecting no last build */
        FilePath filePath = new FilePath(new File("."));
        assertThat(gitSCM.compareRemoteRevisionWith(project, new Launcher.LocalLauncher(listener), filePath, listener, null), is(PollingResult.BUILD_NOW));

        commit("commitFile1", johnDoe, "Commit number 1");
        FreeStyleBuild build = build(project, Result.SUCCESS, "commitFile1");

        commit("commitFile2", johnDoe, "Commit number 2");
        git = Git.with(listener, new EnvVars()).in(build.getWorkspace()).getClient();
        for (RemoteConfig remoteConfig : gitSCM.getRepositories()) {
            git.fetch_().from(remoteConfig.getURIs().get(0), remoteConfig.getFetchRefSpecs());
        }
        BuildChooser buildChooser = gitSCM.getBuildChooser();
        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, "origin/master", git, listener, project.getLastBuild().getAction(BuildData.class), null);
        assertEquals(1, candidateRevisions.size());
        gitSCM.setBuildChooser(buildChooser); // Should be a no-op
        Collection<Revision> candidateRevisions2 = buildChooser.getCandidateRevisions(false, "origin/master", git, listener, project.getLastBuild().getAction(BuildData.class), null);
        assertThat(candidateRevisions2, is(candidateRevisions));
    }

    private final Random random = new Random();
    private boolean useChangelogToBranch = random.nextBoolean();

    private void addChangelogToBranchExtension(GitSCM scm) {
        if (useChangelogToBranch) {
            /* Changelog should be no different with this enabled or disabled */
            ChangelogToBranchOptions changelogOptions = new ChangelogToBranchOptions("origin", "master");
            scm.getExtensions().add(new ChangelogToBranch(changelogOptions));
        }
        useChangelogToBranch = !useChangelogToBranch;
    }

    @Test
    public void testMerge() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "default", MergeCommand.GitPluginFastForwardMode.FF)));
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

    @Issue("JENKINS-20392")
    @Test
    public void testMergeChangelog() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "default", MergeCommand.GitPluginFastForwardMode.FF)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        // Here the changelog is by default empty (because changelog for first commit is always empty
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        // Create second commit and run build
        // Here the changelog should contain exactly this one new commit
        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        String commitMessage = "Commit number 2";
        commit(commitFile2, johnDoe, commitMessage);
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);

        ChangeLogSet<? extends ChangeLogSet.Entry> changeLog = build2.getChangeSet();
        assertEquals("Changelog should contain one item", 1, changeLog.getItems().length);

        GitChangeSet singleChange = (GitChangeSet) changeLog.getItems()[0];
        assertEquals("Changelog should contain commit number 2", commitMessage, singleChange.getComment().trim());
    }

    @Test
    public void testMergeWithAgent() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
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
    public void testMergeFailed() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "", MergeCommand.GitPluginFastForwardMode.FF)));
        addChangelogToBranchExtension(scm);

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
    
    @Issue("JENKINS-25191")
    @Test
    public void testMultipleMergeFailed() throws Exception {
    	FreeStyleProject project = setupSimpleProject("master");
    	
    	GitSCM scm = new GitSCM(
    			createRemoteRepositories(),
    			Collections.singletonList(new BranchSpec("master")),
    			null, null,
    			Collections.<GitSCMExtension>emptyList());
    	project.setScm(scm);
	scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration1", "", MergeCommand.GitPluginFastForwardMode.FF)));
	scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration2", "", MergeCommand.GitPluginFastForwardMode.FF)));
        addChangelogToBranchExtension(scm);
    	
    	commit("dummyFile", johnDoe, "Initial Commit");
    	testRepo.git.branch("integration1");
    	testRepo.git.branch("integration2");
    	build(project, Result.SUCCESS);
    	
    	final String commitFile = "commitFile";
    	testRepo.git.checkoutBranch("integration1","master");
    	commit(commitFile,"abc", johnDoe, "merge conflict with integration2");
    	
    	testRepo.git.checkoutBranch("integration2","master");
    	commit(commitFile,"cde", johnDoe, "merge conflict with integration1");
    	
    	final FreeStyleBuild build = build(project, Result.FAILURE);
    	
    	assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testMergeFailedWithAgent() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
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
    public void testMergeWithMatrixBuild() throws Exception {
        
        //Create a matrix project and a couple of axes
        MatrixProject project = rule.jenkins.createProject(MatrixProject.class, "xyz");
        project.setAxes(new AxisList(new Axis("VAR","a","b")));
        
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
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
    public void testEnvironmentVariableExpansion() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.setScm(new GitSCM("${CAT}"+testRepo.gitDir.getPath()));

        // create initial commit and then run the build against it:
        commit("a.txt", johnDoe, "Initial Commit");

        build(project, Result.SUCCESS, "a.txt");

        PollingResult r = project.poll(StreamTaskListener.fromStdout());
        assertFalse(r.hasChanges());

        commit("b.txt", johnDoe, "Another commit");

        r = project.poll(StreamTaskListener.fromStdout());
        assertTrue(r.hasChanges());

        build(project, Result.SUCCESS, "b.txt");
    }

    @TestExtension("testEnvironmentVariableExpansion")
    public static class SupplySomeEnvVars extends EnvironmentContributor {
        @Override
        public void buildEnvironmentFor(Run r, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
            envs.put("CAT","");
        }
    }

    private List<UserRemoteConfig> createRepoList(String url) {
        List<UserRemoteConfig> repoList = new ArrayList<>();
        repoList.add(new UserRemoteConfig(url, null, null, null));
        return repoList;
    }

    /*
     * Makes sure that git browser URL is preserved across config round trip.
     */
    @Issue("JENKINS-22604")
    @Test
    public void testConfigRoundtripURLPreserved() throws Exception {
        /* Long running test of low value on Windows */
        /* Only run on non-Windows and approximately 50% of test runs */
        /* On Windows, it requires 24 seconds before test finishes */
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
        rule.assertEqualDataBoundBeans(scm,p.getScm());
        assertEquals("Wrong key", "git " + url, scm.getKey());
    }

    /*
     * Makes sure that git extensions are preserved across config round trip.
     */
    @Issue("JENKINS-33695")
    @Test
    public void testConfigRoundtripExtensionsPreserved() throws Exception {
        /* Long running test of low value on Windows */
        /* Only run on non-Windows and approximately 50% of test runs */
        /* On Windows, it requires 26 seconds before test finishes */
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
        List<GitSCMExtension> extensions = scm.getExtensions().toList();;
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
        /* Long running test of low value on Windows */
        /* Only run on non-Windows and approximately 50% of test runs */
        /* On Windows, it requires 20 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        FreeStyleProject p = createFreeStyleProject();
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/jenkins");
        p.setScm(scm);
        rule.configRoundtrip(p);
        rule.assertEqualDataBoundBeans(scm,p.getScm());
    }

    /*
     * Sample configuration that should result in no extensions at all
     */
    @Test
    public void testDataCompatibility1() throws Exception {
        FreeStyleProject p = (FreeStyleProject) rule.jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("GitSCMTest/old1.xml"));
        GitSCM oldGit = (GitSCM) p.getScm();
        assertEquals(Collections.emptyList(), oldGit.getExtensions().toList());
        assertEquals(0, oldGit.getSubmoduleCfg().size());
        assertEquals("git https://github.com/jenkinsci/model-ant-project.git", oldGit.getKey());
        assertThat(oldGit.getEffectiveBrowser(), instanceOf(GithubWeb.class));
        GithubWeb browser = (GithubWeb) oldGit.getEffectiveBrowser();
        assertEquals(browser.getRepoUrl(), "https://github.com/jenkinsci/model-ant-project.git/");
    }

    /**
     * Test a pipeline getting the value from several checkout steps gets the latest data everytime.
     * @throws Exception If anything wrong happens
     */
    @Issue("JENKINS-53346")
    @Test
    public void testCheckoutReturnsLatestValues() throws Exception {

        /* Exit test early if running on Windows and path will be too long */
        /* Known limitation of git for Windows 2.28.0 and earlier */
        /* Needs a longpath fix in git for Windows */
        String currentDirectoryPath = new File(".").getCanonicalPath();
        if (isWindows() && currentDirectoryPath.length() > 95) {
            return;
        }

        WorkflowJob p = rule.jenkins.createProject(WorkflowJob.class, "pipeline-checkout-3-tags");
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "    def tokenBranch = ''\n" +
            "    def tokenRevision = ''\n" +
            "    def checkout1 = checkout([$class: 'GitSCM', branches: [[name: 'git-1.1']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/jenkinsci/git-plugin.git']]])\n" +
            "    echo \"checkout1: ${checkout1}\"\n" +
            "    tokenBranch = tm '${GIT_BRANCH}'\n" +
            "    tokenRevision = tm '${GIT_REVISION}'\n" +
            "    echo \"token1: ${tokenBranch}\"\n" +
            "    echo \"revision1: ${tokenRevision}\"\n" +
            "    def checkout2 = checkout([$class: 'GitSCM', branches: [[name: 'git-2.0.2']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/jenkinsci/git-plugin.git']]])\n" +
            "    echo \"checkout2: ${checkout2}\"\n" +
            "    tokenBranch = tm '${GIT_BRANCH,all=true}'\n" +
            "    tokenRevision = tm '${GIT_REVISION,length=8}'\n" +
            "    echo \"token2: ${tokenBranch}\"\n" +
            "    echo \"revision2: ${tokenRevision}\"\n" +
            "    def checkout3 = checkout([$class: 'GitSCM', branches: [[name: 'git-3.0.0']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/jenkinsci/git-plugin.git']]])\n" +
            "    echo \"checkout3: ${checkout3}\"\n" +
            "    tokenBranch = tm '${GIT_BRANCH,fullName=true}'\n" +
            "    tokenRevision = tm '${GIT_REVISION,length=6}'\n" +
            "    echo \"token3: ${tokenBranch}\"\n" +
            "    echo \"revision3: ${tokenRevision}\"\n" +
            "}", true));
        WorkflowRun b = rule.buildAndAssertSuccess(p);
        
        String log = b.getLog();
        // The getLineStartsWith is to ease reading the test failure, to avoid Hamcrest shows all the log
        assertThat(getLineStartsWith(log, "checkout1:"), containsString("checkout1: [GIT_BRANCH:git-1.1, GIT_COMMIT:82db9509c068f60c41d7a4572c0114cc6d23cd0d, GIT_URL:https://github.com/jenkinsci/git-plugin.git]"));
        assertThat(getLineStartsWith(log, "checkout2:"), containsString("checkout2: [GIT_BRANCH:git-2.0.2, GIT_COMMIT:377a0fdbfbf07f70a3e9a566d749b2a185909c33, GIT_URL:https://github.com/jenkinsci/git-plugin.git]"));
        assertThat(getLineStartsWith(log, "checkout3:"), containsString("checkout3: [GIT_BRANCH:git-3.0.0, GIT_COMMIT:858dee578b79ac6683419faa57a281ccb9d347aa, GIT_URL:https://github.com/jenkinsci/git-plugin.git]"));
        assertThat(getLineStartsWith(log, "token1:"), containsString("token1: git-1.1"));
        assertThat(getLineStartsWith(log, "token2:"), containsString("token2: git-1.1")); // Unexpected but current behavior
        assertThat(getLineStartsWith(log, "token3:"), containsString("token3: git-1.1")); // Unexpected but current behavior
        assertThat(getLineStartsWith(log, "revision1:"), containsString("revision1: 82db9509c068f60c41d7a4572c0114cc6d23cd0d"));
        assertThat(getLineStartsWith(log, "revision2:"), containsString("revision2: 82db9509")); // Unexpected but current behavior - should be 377a0fdb
        assertThat(getLineStartsWith(log, "revision3:"), containsString("revision3: 82db95"));   // Unexpected but current behavior - should be 858dee
    }

    private String getLineStartsWith(String text, String startOfLine) {
        try (Scanner scanner = new Scanner(text)) {
            while(scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith(startOfLine)) {
                    return line;
                }
            }
        }
        return "";
    }
    
    @Test
    public void testPleaseDontContinueAnyway() throws Exception {
        /* Wastes time waiting for the build to fail */
        /* Only run on non-Windows and approximately 50% of test runs */
        /* On Windows, it requires 150 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        // create an empty repository with some commits
        testRepo.commit("a","foo",johnDoe, "added");

        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new GitSCM(testRepo.gitDir.getAbsolutePath()));

        rule.buildAndAssertSuccess(p);

        // this should fail as it fails to fetch
        p.setScm(new GitSCM("http://localhost:4321/no/such/repository.git"));
        rule.buildAndAssertStatus(Result.FAILURE, p);
    }

    @Issue("JENKINS-19108")
    @Test
    public void testCheckoutToSpecificBranch() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        GitSCM oldGit = new GitSCM("https://github.com/jenkinsci/model-ant-project.git/");
        setupJGit(oldGit);
        oldGit.getExtensions().add(new LocalBranch("master"));
        p.setScm(oldGit);

        FreeStyleBuild b = rule.buildAndAssertSuccess(p);
        GitClient gc = Git.with(StreamTaskListener.fromStdout(),null).in(b.getWorkspace()).getClient();
        gc.withRepository(new RepositoryCallback<Void>() {
            public Void invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
                Ref head = repo.findRef("HEAD");
                assertTrue("Detached HEAD",head.isSymbolic());
                Ref t = head.getTarget();
                assertEquals(t.getName(),"refs/heads/master");

                return null;
            }
        });
    }
    
    /**
     * Verifies that if project specifies LocalBranch with value of "**" 
     * that the checkout to a local branch using remote branch name sans 'origin'.
     * This feature is necessary to support Maven release builds that push updated
     * pom.xml to remote branch as 
     * <pre>
     * git push origin localbranch:localbranch
     * </pre>
     * @throws Exception on error
     */
    @Test
    public void testCheckoutToDefaultLocalBranch_StarStar() throws Exception {
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new LocalBranch("**"));
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("GIT_BRANCH", "origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
       assertEquals("GIT_LOCAL_BRANCH", "master", getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH));
    }

    /**
     * Verifies that if project specifies LocalBranch with null value (empty string) 
     * that the checkout to a local branch using remote branch name sans 'origin'.
     * This feature is necessary to support Maven release builds that push updated
     * pom.xml to remote branch as 
     * <pre>
     * git push origin localbranch:localbranch
     * </pre>
     * @throws Exception on error
     */
    @Test
    public void testCheckoutToDefaultLocalBranch_NULL() throws Exception {
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new LocalBranch(""));
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("GIT_BRANCH", "origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
       assertEquals("GIT_LOCAL_BRANCH", "master", getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH));
    }

    /*
     * Verifies that GIT_LOCAL_BRANCH is not set if LocalBranch extension
     * is not configured.
     */
    @Test
    public void testCheckoutSansLocalBranchExtension() throws Exception {
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("GIT_BRANCH", "origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
       assertEquals("GIT_LOCAL_BRANCH", null, getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH));
    }
    
    /*
     * Verifies that GIT_CHECKOUT_DIR is set to "checkoutDir" if RelativeTargetDirectory extension
     * is configured.
     */
    @Test
    public void testCheckoutRelativeTargetDirectoryExtension() throws Exception {
       FreeStyleProject project = setupProject("master", false, "checkoutDir");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new RelativeTargetDirectory("checkoutDir"));
       FreeStyleBuild build1 = build(project, "checkoutDir", Result.SUCCESS, commitFile1);

       assertEquals("GIT_CHECKOUT_DIR", "checkoutDir", getEnvVars(project).get(GitSCM.GIT_CHECKOUT_DIR));
    }

    /*
     * Verifies that GIT_CHECKOUT_DIR is not set if RelativeTargetDirectory extension
     * is not configured.
     */
    @Test
    public void testCheckoutSansRelativeTargetDirectoryExtension() throws Exception {
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("GIT_CHECKOUT_DIR", null, getEnvVars(project).get(GitSCM.GIT_CHECKOUT_DIR));
    }
    @Test
    public void testCheckoutFailureIsRetryable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // run build first to create workspace
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");

        // create lock file to simulate lock collision
        File lock = new File(build1.getWorkspace().getRemote(), ".git/index.lock");
        try {
            FileUtils.touch(lock);
            final FreeStyleBuild build2 = build(project, Result.FAILURE);
            rule.waitForMessage("java.io.IOException: Could not checkout", build2);
        } finally {
            lock.delete();
        }
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
    public void testSparseCheckoutAfterNormalCheckout() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
        FreeStyleProject project = setupSimpleProject("master");

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("titi").exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        assertTrue(build1.getWorkspace().child("toto").exists());
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        ((GitSCM) project.getScm()).getExtensions().add(new SparseCheckoutPaths(Collections.singletonList(new SparseCheckoutPath("titi"))));

        final FreeStyleBuild build2 = build(project, Result.SUCCESS);
        assertTrue(build2.getWorkspace().child("titi").exists());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertFalse(build2.getWorkspace().child("toto").exists());
        assertFalse(build2.getWorkspace().child(commitFile1).exists());
    }

    @Test
    public void testNormalCheckoutAfterSparseCheckout() throws Exception {
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

        final FreeStyleBuild build2 = build(project, Result.SUCCESS);
        assertTrue(build2.getWorkspace().child("titi").exists());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertFalse(build2.getWorkspace().child("toto").exists());
        assertFalse(build2.getWorkspace().child(commitFile1).exists());

        ((GitSCM) project.getScm()).getExtensions().remove(SparseCheckoutPaths.class);

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("titi").exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        assertTrue(build1.getWorkspace().child("toto").exists());
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

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

    @Test
    @Issue("JENKINS-22009")
    public void testPolling_environmentValueInBranchSpec() throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_BRANCH", "master")));

        // commit something in order to create an initial base version in git
        commit("toto/commitFile1", johnDoe, "Commit number 1");

        // build the project
        build(project, Result.SUCCESS);

        assertFalse("No changes to git since last build, thus no new build is expected", project.poll(listener).hasChanges());
    }

    @Issue("JENKINS-29066")
    public void baseTestPolling_parentHead(List<GitSCMExtension> extensions) throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("**")),
                null, null,
                extensions);
        project.setScm(scm);

        // commit something in order to create an initial base version in git
        commit("toto/commitFile1", johnDoe, "Commit number 1");
        git.branch("someBranch");
        commit("toto/commitFile2", johnDoe, "Commit number 2");

        assertTrue("polling should detect changes",project.poll(listener).hasChanges());

        // build the project
        build(project, Result.SUCCESS);

        /* Expects 1 build because the build of someBranch incorporates all
         * the changes from the master branch as well as the changes from someBranch.
         */
        assertEquals("Wrong number of builds", 1, project.getBuilds().size());

        assertFalse("polling should not detect changes",project.poll(listener).hasChanges());
    }

    @Issue("JENKINS-29066")
    @Test
    public void testPolling_parentHead() throws Exception {
        baseTestPolling_parentHead(Collections.<GitSCMExtension>emptyList());
    }

    @Issue("JENKINS-29066")
    @Test
    public void testPolling_parentHead_DisableRemotePoll() throws Exception {
        baseTestPolling_parentHead(Collections.<GitSCMExtension>singletonList(new DisableRemotePoll()));
    }

    @Test
    public void testPollingAfterManualBuildWithParametrizedBranchSpec() throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_BRANCH", "trackedbranch")));

        // Initial commit to master
        commit("file1", johnDoe, "Initial Commit");
        
        // Create the branches
        git.branch("trackedbranch");
        git.branch("manualbranch");
        
        final StringParameterValue branchParam = new StringParameterValue("MY_BRANCH", "manualbranch");
        final Action[] actions = {new ParametersAction(branchParam)};
        FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause(), actions).get();
        rule.assertBuildStatus(Result.SUCCESS, build);

        assertFalse("No changes to git since last build", project.poll(listener).hasChanges());

        git.checkout("manualbranch");
        commit("file2", johnDoe, "Commit to manually build branch");
        assertFalse("No changes to tracked branch", project.poll(listener).hasChanges());

        git.checkout("trackedbranch");
        commit("file3", johnDoe, "Commit to tracked branch");
        assertTrue("A change should be detected in tracked branch", project.poll(listener).hasChanges());
        
    }
    
    private final class FakeParametersAction implements EnvironmentContributingAction, Serializable {
        // Test class for testPolling_environmentValueAsEnvironmentContributingAction test case
        final ParametersAction m_forwardingAction;

        public FakeParametersAction(StringParameterValue params) {
            this.m_forwardingAction = new ParametersAction(params);
        }

        @Deprecated
        public void buildEnvVars(AbstractBuild<?, ?> ab, EnvVars ev) {
            this.m_forwardingAction.buildEnvVars(ab, ev);
        }

        public String getIconFileName() {
            return this.m_forwardingAction.getIconFileName();
        }

        public String getDisplayName() {
            return this.m_forwardingAction.getDisplayName();
        }

        public String getUrlName() {
            return this.m_forwardingAction.getUrlName();
        }

        public List<ParameterValue> getParameters() {
            return this.m_forwardingAction.getParameters();
        }

        private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        }

        private void readObjectNoData() throws ObjectStreamException {
        }
    }

    @Test
	public void testPolling_CanDoRemotePollingIfOneBranchButMultipleRepositories() throws Exception {
		FreeStyleProject project = createFreeStyleProject();
		List<UserRemoteConfig> remoteConfigs = new ArrayList<>();
		remoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "", null));
		remoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "someOtherRepo", "", null));
		GitSCM scm = new GitSCM(remoteConfigs,
				Collections.singletonList(new BranchSpec("origin/master")), false,
				Collections.<SubmoduleConfig> emptyList(), null, null,
				Collections.<GitSCMExtension> emptyList());
		project.setScm(scm);
		commit("commitFile1", johnDoe, "Commit number 1");

		FreeStyleBuild first_build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
        rule.assertBuildStatus(Result.SUCCESS, first_build);

		first_build.getWorkspace().deleteContents();
		PollingResult pollingResult = scm.poll(project, null, first_build.getWorkspace(), listener, null);
		assertFalse(pollingResult.hasChanges());
	}

    @Issue("JENKINS-24467")
    @Test
    public void testPolling_environmentValueAsEnvironmentContributingAction() throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);

        // Initial commit and build
        commit("toto/commitFile1", johnDoe, "Commit number 1");
        String brokenPath = "\\broken/path\\of/doom";
        if (!sampleRepo.gitVersionAtLeast(1, 8)) {
            /* Git 1.7.10.4 fails the first build unless the git-upload-pack
             * program is available in its PATH.
             * Later versions of git don't have that problem.
             */
            final String systemPath = System.getenv("PATH");
            brokenPath = systemPath + File.pathSeparator + brokenPath;
        }
        final StringParameterValue real_param = new StringParameterValue("MY_BRANCH", "master");
        final StringParameterValue fake_param = new StringParameterValue("PATH", brokenPath);

        final Action[] actions = {new ParametersAction(real_param), new FakeParametersAction(fake_param)};

        // SECURITY-170 - have to use ParametersDefinitionProperty
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_BRANCH", "master")));

        FreeStyleBuild first_build = project.scheduleBuild2(0, new Cause.UserIdCause(), actions).get();
        rule.assertBuildStatus(Result.SUCCESS, first_build);

        Launcher launcher = workspace.createLauncher(listener);
        final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener);

        assertEquals(environment.get("MY_BRANCH"), "master");
        assertNotSame("Environment path should not be broken path", environment.get("PATH"), brokenPath);
    }

    /**
     * Method performs HTTP get on "notifyCommit" URL, passing it commit by SHA1
     * and tests for custom SCM name build data consistency.
     * @param project project to build
     * @param commit commit to build
     * @param expectedScmName Expected SCM name for commit.
     * @param ordinal number of commit to log into errors, if any
     * @param git git SCM
     * @throws Exception on error
     */
    private int notifyAndCheckScmName(FreeStyleProject project, ObjectId commit,
            String expectedScmName, int ordinal, GitSCM git, ObjectId... priorCommits) throws Exception {
        String priorCommitIDs = "";
        for (ObjectId priorCommit : priorCommits) {
            priorCommitIDs = priorCommitIDs + " " + priorCommit;
        }
        assertTrue("scm polling should detect commit " + ordinal, notifyCommit(project, commit));

        final Build build = project.getLastBuild();
        final BuildData buildData = git.getBuildData(build);
        assertEquals("Expected SHA1 != built SHA1 for commit " + ordinal + " priors:" + priorCommitIDs, commit, buildData
                .getLastBuiltRevision().getSha1());
        assertEquals("Expected SHA1 != retrieved SHA1 for commit " + ordinal + " priors:" + priorCommitIDs, commit, buildData.getLastBuild(commit).getSHA1());
        assertTrue("Commit " + ordinal + " not marked as built", buildData.hasBeenBuilt(commit));

        assertEquals("Wrong SCM Name for commit " + ordinal, expectedScmName, buildData.getScmName());

        return build.getNumber();
    }

    private void checkNumberedBuildScmName(FreeStyleProject project, int buildNumber,
            String expectedScmName, GitSCM git) throws Exception {

        final BuildData buildData = git.getBuildData(project.getBuildByNumber(buildNumber));
        assertEquals("Wrong SCM Name", expectedScmName, buildData.getScmName());
    }

    /*
     * Tests that builds have the correctly specified branches, associated with
     * the commit id, passed with "notifyCommit" URL.
     */
    @Ignore("Intermittent failures on stable-3.10 branch, not on stable-3.9 or master")
    @Issue("JENKINS-24133")
    // Flaky test distracting from primary focus
    // @Test
    public void testSha1NotificationBranches() throws Exception {
        final String branchName = "master";
        final FreeStyleProject project = setupProject(branchName, false);
        project.addTrigger(new SCMTrigger(""));
        final GitSCM git = (GitSCM) project.getScm();
        setupJGit(git);

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        assertTrue("scm polling should detect commit 1",
                project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile1);
        final ObjectId commit1 = testRepo.git.revListAll().get(0);
        notifyAndCheckBranch(project, commit1, branchName, 1, git);

        commit("commitFile2", johnDoe, "Commit number 2");
        assertTrue("scm polling should detect commit 2", project.poll(listener).hasChanges());
        final ObjectId commit2 = testRepo.git.revListAll().get(0);
        notifyAndCheckBranch(project, commit2, branchName, 2, git);

        notifyAndCheckBranch(project, commit1, branchName, 1, git);
    }

    /* A null pointer exception was detected because the plugin failed to
     * write a branch name to the build data, so there was a SHA1 recorded 
     * in the build data, but no branch name.
     */
    @Test
    @Deprecated // Testing deprecated buildEnvVars
    public void testNoNullPointerExceptionWithNullBranch() throws Exception {
        ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

        /* This is the null that causes NPE */
        Branch branch = new Branch(null, sha1);

        List<Branch> branchList = new ArrayList<>();
        branchList.add(branch);

        Revision revision = new Revision(sha1, branchList);

        /* BuildData mock that will use the Revision with null branch name */
        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
        Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

        /* List of build data that will be returned by the mocked BuildData */
        List<BuildData> buildDataList = new ArrayList<>();
        buildDataList.add(buildData);

        /* AbstractBuild mock which returns the buildDataList that contains a null branch name */
        AbstractBuild build = Mockito.mock(AbstractBuild.class);
        Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

        final FreeStyleProject project = setupProject("*/*", false);
        GitSCM scm = (GitSCM) project.getScm();
        scm.buildEnvVars(build, new EnvVars()); // NPE here before fix applied

        /* Verify mocks were called as expected */
        verify(buildData, times(1)).getLastBuiltRevision();
        verify(buildData, times(1)).hasBeenReferenced(anyString());
        verify(build, times(1)).getActions(BuildData.class);
    }

    @Test
    @Deprecated // Testing deprecated buildEnvVars
    public void testBuildEnvVarsLocalBranchStarStar() throws Exception {
       ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

       /* This is the null that causes NPE */
       Branch branch = new Branch("origin/master", sha1);

       List<Branch> branchList = new ArrayList<>();
       branchList.add(branch);

       Revision revision = new Revision(sha1, branchList);

       /* BuildData mock that will use the Revision with null branch name */
       BuildData buildData = Mockito.mock(BuildData.class);
       Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
       Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

       /* List of build data that will be returned by the mocked BuildData */
       List<BuildData> buildDataList = new ArrayList<>();
       buildDataList.add(buildData);

       /* AbstractBuild mock which returns the buildDataList that contains a null branch name */
       AbstractBuild build = Mockito.mock(AbstractBuild.class);
       Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

       final FreeStyleProject project = setupProject("*/*", false);
       GitSCM scm = (GitSCM) project.getScm();
       scm.getExtensions().add(new LocalBranch("**"));

       EnvVars env = new EnvVars();
       scm.buildEnvVars(build, env); // NPE here before fix applied
       
       assertEquals("GIT_BRANCH", "origin/master", env.get("GIT_BRANCH"));
       assertEquals("GIT_LOCAL_BRANCH", "master", env.get("GIT_LOCAL_BRANCH"));

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    @Test
    @Deprecated // Testing deprecated buildEnvVars
    public void testBuildEnvVarsLocalBranchNull() throws Exception {
       ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

       /* This is the null that causes NPE */
       Branch branch = new Branch("origin/master", sha1);

       List<Branch> branchList = new ArrayList<>();
       branchList.add(branch);

       Revision revision = new Revision(sha1, branchList);

       /* BuildData mock that will use the Revision with null branch name */
       BuildData buildData = Mockito.mock(BuildData.class);
       Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
       Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

       /* List of build data that will be returned by the mocked BuildData */
       List<BuildData> buildDataList = new ArrayList<>();
       buildDataList.add(buildData);

       /* AbstractBuild mock which returns the buildDataList that contains a null branch name */
       AbstractBuild build = Mockito.mock(AbstractBuild.class);
       Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

       final FreeStyleProject project = setupProject("*/*", false);
       GitSCM scm = (GitSCM) project.getScm();
       scm.getExtensions().add(new LocalBranch(""));

       EnvVars env = new EnvVars();
       scm.buildEnvVars(build, env); // NPE here before fix applied
       
       assertEquals("GIT_BRANCH", "origin/master", env.get("GIT_BRANCH"));
       assertEquals("GIT_LOCAL_BRANCH", "master", env.get("GIT_LOCAL_BRANCH"));

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    @Test
    @Deprecated // testing deprecated buildEnvVars
    public void testBuildEnvVarsLocalBranchNotSet() throws Exception {
       ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

       /* This is the null that causes NPE */
       Branch branch = new Branch("origin/master", sha1);

       List<Branch> branchList = new ArrayList<>();
       branchList.add(branch);

       Revision revision = new Revision(sha1, branchList);

       /* BuildData mock that will use the Revision with null branch name */
       BuildData buildData = Mockito.mock(BuildData.class);
       Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
       Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

       /* List of build data that will be returned by the mocked BuildData */
       List<BuildData> buildDataList = new ArrayList<>();
       buildDataList.add(buildData);

       /* AbstractBuild mock which returns the buildDataList that contains a null branch name */
       AbstractBuild build = Mockito.mock(AbstractBuild.class);
       Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

       final FreeStyleProject project = setupProject("*/*", false);
       GitSCM scm = (GitSCM) project.getScm();

       EnvVars env = new EnvVars();
       scm.buildEnvVars(build, env); // NPE here before fix applied
       
       assertEquals("GIT_BRANCH", "origin/master", env.get("GIT_BRANCH"));
       assertEquals("GIT_LOCAL_BRANCH", null, env.get("GIT_LOCAL_BRANCH"));

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    @Test
    public void testBuildEnvironmentVariablesSingleRemote() throws Exception {
        ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

        List<Branch> branchList = new ArrayList<>();
        Branch branch = new Branch("origin/master", sha1);
        branchList.add(branch);

        Revision revision = new Revision(sha1, branchList);

        /* BuildData mock that will use the Revision */
        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
        Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

        /* List of build data that will be returned by the mocked BuildData */
        List<BuildData> buildDataList = new ArrayList<>();
        buildDataList.add(buildData);

        /* Run mock which returns the buildDataList */
        Run<?, ?> build = Mockito.mock(Run.class);
        Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

        FreeStyleProject project = setupSimpleProject("*/*");
        GitSCM scm = (GitSCM) project.getScm();

        Map<String, String> env = new HashMap<>();
        scm.buildEnvironment(build, env);

        assertEquals("GIT_BRANCH is invalid", "origin/master", env.get("GIT_BRANCH"));
        assertEquals("GIT_LOCAL_BRANCH is invalid", null, env.get("GIT_LOCAL_BRANCH"));
        assertEquals("GIT_COMMIT is invalid", sha1.getName(), env.get("GIT_COMMIT"));
        assertEquals("GIT_URL is invalid", testRepo.gitDir.getAbsolutePath(), env.get("GIT_URL"));
        assertNull("GIT_URL_1 should not have been set", env.get("GIT_URL_1"));
    }

    @Test
    public void testBuildEnvironmentVariablesMultipleRemotes() throws Exception {
        ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

        List<Branch> branchList = new ArrayList<>();
        Branch branch = new Branch("origin/master", sha1);
        branchList.add(branch);

        Revision revision = new Revision(sha1, branchList);

        /* BuildData mock that will use the Revision */
        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
        Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

        /* List of build data that will be returned by the mocked BuildData */
        List<BuildData> buildDataList = new ArrayList<>();
        buildDataList.add(buildData);

        /* Run mock which returns the buildDataList */
        Run<?, ?> build = Mockito.mock(Run.class);
        Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

        FreeStyleProject project = setupSimpleProject("*/*");
        /* Update project so we have two remote configs */
        List<UserRemoteConfig> userRemoteConfigs = new ArrayList<>();
        userRemoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "", null));
        final String upstreamRepoUrl = "/upstream/url";
        userRemoteConfigs.add(new UserRemoteConfig(upstreamRepoUrl, "upstream", "", null));
        GitSCM scm = new GitSCM(
                userRemoteConfigs,
                Collections.singletonList(new BranchSpec(branch.getName())),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);

        Map<String, String> env = new HashMap<>();
        scm.buildEnvironment(build, env);

        assertEquals("GIT_BRANCH is invalid", "origin/master", env.get("GIT_BRANCH"));
        assertEquals("GIT_LOCAL_BRANCH is invalid", null, env.get("GIT_LOCAL_BRANCH"));
        assertEquals("GIT_COMMIT is invalid", sha1.getName(), env.get("GIT_COMMIT"));
        assertEquals("GIT_URL is invalid", testRepo.gitDir.getAbsolutePath(), env.get("GIT_URL"));
        assertEquals("GIT_URL_1 is invalid", testRepo.gitDir.getAbsolutePath(), env.get("GIT_URL_1"));
        assertEquals("GIT_URL_2 is invalid", upstreamRepoUrl, env.get("GIT_URL_2"));
        assertNull("GIT_URL_3 should not have been set", env.get("GIT_URL_3"));
    }

    @Issue("JENKINS-38241")
    @Test
    public void testCommitMessageIsPrintedToLogs() throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "v1");
        sampleRepo.git("commit", "--all", "--message=test commit");
        FreeStyleProject p = setupSimpleProject("master");
        Run<?,?> run = rule.buildAndAssertSuccess(p);
        rule.waitForMessage("Commit message: \"test commit\"", run);
    }

    /**
     * Method performs HTTP get on "notifyCommit" URL, passing it commit by SHA1
     * and tests for build data consistency.
     * @param project project to build
     * @param commit commit to build
     * @param expectedBranch branch, that is expected to be built
     * @param ordinal number of commit to log into errors, if any
     * @param git git SCM
     * @throws Exception on error
     */
    private void notifyAndCheckBranch(FreeStyleProject project, ObjectId commit,
            String expectedBranch, int ordinal, GitSCM git) throws Exception {
        assertTrue("scm polling should detect commit " + ordinal, notifyCommit(project, commit));
        final BuildData buildData = git.getBuildData(project.getLastBuild());
        final Collection<Branch> builtBranches = buildData.lastBuild.getRevision().getBranches();
        assertEquals("Commit " + ordinal + " should be built", commit, buildData
                .getLastBuiltRevision().getSha1());

        final String expectedBranchString = "origin/" + expectedBranch;
        assertFalse("Branches should be detected for the build", builtBranches.isEmpty());
        assertEquals(expectedBranch + " branch should be detected", expectedBranchString,
                     builtBranches.iterator().next().getName());
        assertEquals(expectedBranchString, getEnvVars(project).get(GitSCM.GIT_BRANCH));
    }

    /**
     * Method performs commit notification for the last committed SHA1 using
     * notifyCommit URL.
     * @param project project to trigger
     * @return whether the new build has been triggered (<code>true</code>) or
     *         not (<code>false</code>).
     * @throws Exception on error
     */
    private boolean notifyCommit(FreeStyleProject project, ObjectId commitId) throws Exception {
        final int initialBuildNumber = project.getLastBuild().getNumber();
        final String commit1 = ObjectId.toString(commitId);

        final String notificationPath = rule.getURL().toExternalForm()
                + "git/notifyCommit?url=" + testRepo.gitDir.toString() + "&sha1=" + commit1;
        final URL notifyUrl = new URL(notificationPath);
        String notifyContent = null;
        try (final InputStream is = notifyUrl.openStream()) {
            notifyContent = IOUtils.toString(is, "UTF-8");
        }
        assertThat(notifyContent, containsString("No Git consumers using SCM API plugin for: " + testRepo.gitDir.toString()));

        if ((project.getLastBuild().getNumber() == initialBuildNumber)
                && (rule.jenkins.getQueue().isEmpty())) {
            return false;
        } else {
            while (!rule.jenkins.getQueue().isEmpty()) {
                Thread.sleep(100);
            }
            final FreeStyleBuild build = project.getLastBuild();
            while (build.isBuilding()) {
                Thread.sleep(100);
            }
            return true;
        }
    }

    private void setupJGit(GitSCM git) {
        git.gitTool="jgit";
        rule.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(new JGitTool(Collections.<ToolProperty<?>>emptyList()));
    }

    /** We clean the environment, just in case the test is being run from a Jenkins job using this same plugin :). */
    @TestExtension
    public static class CleanEnvironment extends EnvironmentContributor {
        @Override
        public void buildEnvironmentFor(Run run, EnvVars envs, TaskListener listener) {
            envs.remove(GitSCM.GIT_BRANCH);
            envs.remove(GitSCM.GIT_LOCAL_BRANCH);
            envs.remove(GitSCM.GIT_COMMIT);
            envs.remove(GitSCM.GIT_PREVIOUS_COMMIT);
            envs.remove(GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT);
        }
    }

    /** Returns true if test cleanup is not reliable */
    private boolean cleanupIsUnreliable() {
        // Windows cleanup is unreliable on ci.jenkins.io
        String jobUrl = System.getenv("JOB_URL");
        return isWindows() && jobUrl != null && jobUrl.contains("ci.jenkins.io");
    }

    /** inline ${@link hudson.Functions#isWindows()} to prevent a transient remote classloader issue */
    private boolean isWindows() {
        return java.io.File.pathSeparatorChar==';';
    }

    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password");
    }
}
