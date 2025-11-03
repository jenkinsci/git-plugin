package hudson.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.htmlunit.html.HtmlPage;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.EnvironmentContributingAction;
import hudson.model.EnvironmentContributor;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.*;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.GitUtils;
import hudson.plugins.parameterizedtrigger.BuildTrigger;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.DumbSlave;
import hudson.tools.ToolLocationNodeProperty;
import hudson.util.LogTaskListener;
import hudson.util.RingBufferLogHandler;
import hudson.util.StreamTaskListener;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.SystemReader;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jenkinsci.plugins.gitclient.*;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;

import static hudson.Functions.isWindows;
import static org.junit.jupiter.api.Assertions.*;
import static org.jvnet.hudson.test.LogRecorder.recorded;

import java.io.File;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
@TestMethodOrder(MethodOrderer.Random.class)
@WithGitSampleRepo
class GitSCMTest extends AbstractGitTestCase {

    private GitSampleRepoRule secondRepo;

    private final LogRecorder recorder = new LogRecorder();

    private CredentialsStore store = null;

    private static final Instant START_TIME = Instant.now();

    private static final int MAX_SECONDS_FOR_THESE_TESTS = 570;

    private boolean isTimeAvailable() {
        String env = System.getenv("CI");
        if (!Boolean.parseBoolean(env)) {
            // Run all tests when not in CI environment
            return true;
        }
        return Duration.between(START_TIME, Instant.now()).toSeconds() <= MAX_SECONDS_FOR_THESE_TESTS;
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        SystemReader.getInstance().getUserConfig().clear();
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
    }

    @BeforeEach
    void beforeEach(GitSampleRepoRule repo) throws Exception {
        secondRepo = repo;
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.get())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;

            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
    }

    @AfterEach
    void afterEach() throws Exception {
        if (cleanupIsUnreliable()) {
            r.waitUntilNoActivityUpTo(5001);
        }
    }

    private StandardCredentials getInvalidCredential() throws FormException {
        String username = "bad-user";
        String password = "bad-password-but-long-enough";
        CredentialsScope scope = CredentialsScope.GLOBAL;
        String id = "username-" + username + "-password-" + password;
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, username, password);
    }

    @Test
    void testAddGitTagAction() throws Exception {
        /* Low value test of low value feature, never run on Windows, run 50% on others */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");
        List<UserRemoteConfig> remoteConfigs = GitSCM.createRepoList("https://github.com/jenkinsci/git-plugin", "github");
        project.setScm(new GitSCM(remoteConfigs,
                Collections.singletonList(new BranchSpec("master")), false, null, null, null, null));

        GitSCM scm = (GitSCM) project.getScm();
        final DescriptorImpl descriptor = scm.getDescriptor();
        boolean originalValue = scm.isAddGitTagAction();
        assertFalse(originalValue, "Wrong initial value for hide tag action");
        descriptor.setAddGitTagAction(true);
        assertTrue(scm.isAddGitTagAction(), "Hide tag action not set");
        descriptor.setAddGitTagAction(false);
        assertFalse(scm.isAddGitTagAction(), "Wrong final value for hide tag action");
        descriptor.setAddGitTagAction(originalValue); // restore original value of addGitTagAction

        /* Exit test early if running on Windows and path will be too long */
        /* Known limitation of git for Windows 2.28.0 and earlier */
        /* Needs a longpath fix in git for Windows */
        String currentDirectoryPath = new File(".").getCanonicalPath();
        if (isWindows() && currentDirectoryPath.length() > 95) {
            return;
        }

        recorder.record(GitSCM.class, Level.FINE).capture(20);

        // Build 1 will not add a tag action
        commit("commitFileWithoutGitTagAction", johnDoe, "Commit 1 without git tag action");
        build(project, Result.SUCCESS);
        assertThat(recorder, recorded(containsString("Not adding GitTagAction to build 1")));

        // Build 2 will add a tag action
        descriptor.setAddGitTagAction(true);
        build(project, Result.SUCCESS);
        assertThat(recorder, recorded(containsString("Adding GitTagAction to build 2")));

        // Build 3 will not add a tag action
        descriptor.setAddGitTagAction(false);
        build(project, Result.SUCCESS);
        assertThat(recorder, recorded(containsString("Not adding GitTagAction to build 3")));
    }

    @Test
    void manageShouldAccessGlobalConfig() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        final String USER = "user";
        final String MANAGER = "manager";
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
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
            assertTrue(found.isPresent(), "Global configuration should be accessible to MANAGE users");
        }
    }

    @Test
    void trackCredentials() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        StandardCredentials credential = getInvalidCredential();
        store.addCredentials(Domain.global(), credential);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should not be set before job definition", fingerprint, nullValue());

        JenkinsRule.WebClient wc = r.createWebClient();
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
    void testBasic() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

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

    @Test
    @Issue("JENKINS-56176")
    void testBasicRemotePoll() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
//        FreeStyleProject project = setupProject("master", true, false);
        FreeStyleProject project = setupProject("master", false, null, null, null, true, null);
        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        String sha1String = commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");
        // ... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals(1, culprits.size(), "The build should have only one culprit");
        assertEquals(janeDoe.getName(), culprits.iterator().next().getFullName(), "");
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
        // JENKINS-56176 token macro expansion broke when BuildData was no longer updated
        assertThat(TokenMacro.expandAll(build2, listener, "${GIT_REVISION,length=7}"), is(sha1String.substring(0, 7)));
        assertThat(TokenMacro.expandAll(build2, listener, "${GIT_REVISION}"), is(sha1String));
        assertThat(TokenMacro.expandAll(build2, listener, "$GIT_REVISION"), is(sha1String));
    }

    @Test
    void testBranchSpecWithRemotesMaster() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testSpecificRefspecs() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testAvoidRedundantFetch() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testAvoidRedundantFetchWithoutHonorRefSpec() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testAvoidRedundantFetchWithHonorRefSpec() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testAvoidRedundantFetchWithNullRefspec() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testRetainRedundantFetch() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testRetainRedundantFetchIfSecondFetchIsAllowed() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        String refspec = "+refs/heads/*:refs/remotes/*";
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", refspec, null));

        /* Without honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);

        GitSCM scm = (GitSCM) projectWithMaster.getScm();
        final DescriptorImpl descriptor = scm.getDescriptor();
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
    void testSpecificRefspecsWithoutCloneOption() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testAddFirstRepositoryWithNullRepoURL() throws Exception{
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testAddSecondRepositoryWithNullRepoURL() throws Exception{
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testBranchSpecWithRemotesHierarchical() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testBranchSpecUsingTagWithSlash() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject projectMasterBranch = setupProject("path/tag", false, null, null, null, true, null);
        // create initial commit and build
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1 will be tagged with path/tag");
        testRepo.git.tag("path/tag", "tag with a slash in the tag name");
        build(projectMasterBranch, Result.SUCCESS, commitFile1);
      }

    @Test
    void testBasicIncludedRegion() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupProject("master", false, null, null, null, ".*3");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse(project.poll(listener).hasChanges(), "scm polling detected commit2 change, which should not have been included");

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit3 change");

        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals(2, culprits.size(), "The build should have two culprit");
        
        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
    }

    /**
     * testMergeCommitInExcludedRegionIsIgnored() confirms behavior of excluded regions with merge commits.
     * This test has excluded and included regions, for files ending with .excluded and .included,
     * respectively. The git repository is set up so that a non-fast-forward merge commit comes
     * to master. The newly merged commit is a file ending with .excluded, so it should be ignored.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389", "JENKINS-23606"})
    @Test
    void testMergeCommitInExcludedRegionIsIgnored() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertFalse(project.poll(listener).hasChanges(),
                "Polling should report no changes, because they are in the excluded region.");
    }

    /**
     * testMergeCommitInExcludedDirectoryIsIgnored() confirms behavior of excluded directories with merge commits.
     * This test has excluded and included directories, named /excluded/ and /included/,respectively. The repository
     * is set up so that a non-fast-forward merge commit comes to master, and is in the directory /excluded/,
     * so it should be ignored.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389", "JENKINS-23606"})
    @Test
    void testMergeCommitInExcludedDirectoryIsIgnored() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertFalse(project.poll(listener).hasChanges(),
                "Polling should see no changes, because they are in the excluded directory.");
    }

    /**
     * testMergeCommitInIncludedRegionIsProcessed() confirms behavior of included regions with merge commits.
     * This test has excluded and included regions, for files ending with .excluded and .included, respectively.
     * The git repository is set up so that a non-fast-forward merge commit comes to master. The newly merged
     * commit is a file ending with .included, so it should be processed as a new change.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389", "JENKINS-23606"})
    @Test
    void testMergeCommitInIncludedRegionIsProcessed() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertTrue(project.poll(listener).hasChanges(),
                "Polling should report changes, because they fall within the included region.");
    }

    /**
     * testMergeCommitInIncludedRegionIsProcessed() confirms behavior of included directories with merge commits.
     * This test has excluded and included directories, named /excluded/ and /included/, respectively. The repository
     * is set up so that a non-fast-forward merge commit comes to master, and is in the directory /included/,
     * so it should be processed as a new change.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389", "JENKINS-23606"})
    @Test
    void testMergeCommitInIncludedDirectoryIsProcessed() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertTrue(project.poll(listener).hasChanges(),
                "Polling should report changes, because they are in the included directory.");
    }

    /**
     * testMergeCommitOutsideIncludedRegionIsIgnored() confirms behavior of included regions with merge commits.
     * This test has an included region defined, for files ending with .included. There is no excluded region
     * defined. The repository is set up and a non-fast-forward merge commit comes to master. The newly merged commit
     * is a file ending with .should-be-ignored, thus falling outside of the included region, so it should ignored.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389", "JENKINS-23606"})
    @Test
    void testMergeCommitOutsideIncludedRegionIsIgnored() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertFalse(project.poll(listener).hasChanges(),
                "Polling should ignore the change, because it falls outside the included region.");
    }

    /**
     * testMergeCommitOutsideIncludedDirectoryIsIgnored() confirms behavior of included directories with merge commits.
     * This test has only an included directory `/included`  defined. The git repository is set up so that
     * a non-fast-forward, but mergeable, commit comes to master. The newly merged commit is outside of the
     * /included/ directory, so polling should report no changes.
     *
     * @throws Exception on error
     */
    @Issue({"JENKINS-20389", "JENKINS-23606"})
    @Test
    void testMergeCommitOutsideIncludedDirectoryIsIgnored() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertFalse(project.poll(listener).hasChanges(),
                "Polling should ignore the change, because it falls outside the included directory.");
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
    @Issue({"JENKINS-20389", "JENKINS-23606"})
    @Test
    void testMergeCommitOutsideExcludedRegionIsProcessed() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertTrue(project.poll(listener).hasChanges(),
                "Polling should process the change, because it falls outside the excluded region.");
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
    @Issue({"JENKINS-20389", "JENKINS-23606"})
    @Test
    void testMergeCommitOutsideExcludedDirectoryIsProcessed() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertTrue(project.poll(listener).hasChanges(),
                "SCM polling should process the change, because it falls outside the excluded directory.");
    }

    @Test
    void testIncludedRegionWithDeeperCommits() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupProject("master", false, null, null, null, ".*3");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse(project.poll(listener).hasChanges(), "scm polling detected commit2 change, which should not have been included");
        

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        
        final String commitFile4 = "commitFile4";
        commit(commitFile4, janeDoe, "Commit number 4");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit3 change");

        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals(2, culprits.size(), "The build should have two culprit");
        
        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
    }

    @Test
    void testBasicExcludedRegion() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupProject("master", false, null, ".*2", null, null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse(project.poll(listener).hasChanges(), "scm polling detected commit2 change, which should have been excluded");

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit3 change");
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals(2, culprits.size(), "The build should have two culprit");

        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
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
    void testCleanBeforeCheckout() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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

    @Test
    void testFirstBuiltChangelog() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject p = setupProject("master", false, null, null, "Jane Doe", null);
        FirstBuildChangelog fbc = new FirstBuildChangelog();
        ((GitSCM) p.getScm()).getExtensions().add(fbc);

        /* First build should should generate a changelog */
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(p, Result.SUCCESS, commitFile1);
        assertThat(firstBuild.getLog(50), hasItem("First time build. Latest changes added to changelog."));
        /* Second build should have normal behavior */
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        final FreeStyleBuild secondBuild = build(p, Result.SUCCESS, commitFile2);
        assertThat(secondBuild.getLog(50), not(hasItem("First time build. Latest changes added to changelog.")));
    }

    @Issue("JENKINS-8342")
    @Test
    void testExcludedRegionMultiCommit() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        // Got 2 projects, each one should only build if changes in its own file
        FreeStyleProject clientProject = setupProject("master", false, null, ".*serverFile", null, null);
        FreeStyleProject serverProject = setupProject("master", false, null, ".*clientFile", null, null);
        String initialCommitFile = "initialFile";
        commit(initialCommitFile, johnDoe, "initial commit");
        build(clientProject, Result.SUCCESS, initialCommitFile);
        build(serverProject, Result.SUCCESS, initialCommitFile);

        assertFalse(clientProject.poll(listener).hasChanges(), "scm polling should not detect any more changes after initial build");
        assertFalse(serverProject.poll(listener).hasChanges(), "scm polling should not detect any more changes after initial build");

        // Got commits on serverFile, so only server project should build.
        commit("myserverFile", johnDoe, "commit first server file");

        assertFalse(clientProject.poll(listener).hasChanges(), "scm polling should not detect any changes in client project");
        assertTrue(serverProject.poll(listener).hasChanges(), "scm polling did not detect changes in server project");

        // Got commits on both client and serverFile, so both projects should build.
        commit("myNewserverFile", johnDoe, "commit new server file");
        commit("myclientFile", johnDoe, "commit first clientfile");

        assertTrue(clientProject.poll(listener).hasChanges(), "scm polling did not detect changes in client project");
        assertTrue(serverProject.poll(listener).hasChanges(), "scm polling did not detect changes in server project");
    }

    /*
     * With multiple branches specified in the project and having commits from a user
     * excluded should not build the excluded revisions when another branch changes.
     */
    /*
    @Issue("JENKINS-8342")
    @Test
    public void testMultipleBranchWithExcludedUser() throws Exception {
        assumeTrue("Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded", isTimeAvailable());
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
    void testBasicExcludedUser() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupProject("master", false, null, null, "Jane Doe", null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse(project.poll(listener).hasChanges(), "scm polling detected commit2 change, which should have been excluded");
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit3 change");
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals(2, culprits.size(), "The build should have two culprit");

        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

    }

    @Test
    void testBasicInSubdir() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");
        ((GitSCM)project.getScm()).getExtensions().add(new RelativeTargetDirectory("subdir"));

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, "subdir", Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");
        //... and build it...
        final FreeStyleBuild build2 = build(project, "subdir", Result.SUCCESS,
                                            commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals(1, culprits.size(), "The build should have only one culprit");
        assertEquals(janeDoe.getName(), culprits.iterator().next().getFullName(), "");
        assertTrue(build2.getWorkspace().child("subdir").exists(), "The workspace should have a 'subdir' subdirectory, but does not.");
        assertTrue(build2.getWorkspace().child("subdir").child(commitFile2).exists(), "The 'subdir' subdirectory should contain commitFile2, but does not.");
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
    }

    @Issue("HUDSON-7547")
    @Test
    void testBasicWithAgentNoExecutorsOnMaster() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

        r.jenkins.setNumExecutors(0);

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

    @Test
    void testAuthorOrCommitterFalse() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        // Test with authorOrCommitter set to false and make sure we get the committer.
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");

        final FreeStyleBuild secondBuild = build(project, Result.SUCCESS, commitFile2);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final Set<User> secondCulprits = secondBuild.getCulprits();

        assertEquals(1, secondCulprits.size(), "The build should have only one culprit");
        assertEquals(janeDoe.getName(),
                     secondCulprits.iterator().next().getFullName(), "Did not get the committer as the change author with authorOrCommitter==false");
    }

    @Test
    void testAuthorOrCommitterTrue() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        // Next, test with authorOrCommitter set to true and make sure we get the author.
        FreeStyleProject project = setupSimpleProject("master");
        ((GitSCM)project.getScm()).getExtensions().add(new AuthorInChangelog());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");

        final FreeStyleBuild secondBuild = build(project, Result.SUCCESS, commitFile2);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final Set<User> secondCulprits = secondBuild.getCulprits();

        assertEquals(1, secondCulprits.size(), "The build should have only one culprit");
        assertEquals(johnDoe.getName(),
                secondCulprits.iterator().next().getFullName(), "Did not get the author as the change author with authorOrCommitter==true");
    }

    @Test
    void testNewCommitToUntrackedBranchDoesNotTriggerBuild() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect commit2 change because it is not in the branch we are tracking.");
    }

    private String checkoutString(FreeStyleProject project, String envVar) {
        return "checkout -f " + getEnvVars(project).get(envVar);
    }

    @Test
    void testEnvVarsAvailable() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        assertEquals("origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
        r.waitForMessage(getEnvVars(project).get(GitSCM.GIT_BRANCH), build1);

        r.waitForMessage(checkoutString(project, GitSCM.GIT_COMMIT), build1);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);

        r.assertLogNotContains(checkoutString(project, GitSCM.GIT_PREVIOUS_COMMIT), build2);
        r.waitForMessage(checkoutString(project, GitSCM.GIT_PREVIOUS_COMMIT), build1);

        r.assertLogNotContains(checkoutString(project, GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT), build2);
        r.waitForMessage(checkoutString(project, GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT), build1);
    }

    @Test
    void testNodeOverrideGit() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        GitSCM scm = new GitSCM(null);

        DumbSlave agent = r.createSlave();
        GitTool.DescriptorImpl gitToolDescriptor = r.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class);
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
    void testGitSCMCanBuildAgainstTags() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes since mytag is untouched right now");
        build(project, Result.FAILURE);  // fail, because there's nothing to be checked out here

        // tag it, then delete the tmp branch
        git.tag(mytag, "mytag initial");
        git.checkout("master");
        git.deleteBranch(tmpBranch);

        // at this point we're back on master, there are no other branches, tag "mytag" exists but is
        // not part of "master"
        assertTrue(project.poll(listener).hasChanges(), "scm polling should detect commit2 change in 'mytag'");
        build(project, Result.SUCCESS, commitFile2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after last build");

        // now, create tmp branch again against mytag:
        git.checkout(mytag);
        git.branch(tmpBranch);
        // another commit:
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes since mytag is untouched right now");

        // now we're going to force mytag to point to the new commit, if everything goes well, gitSCM should pick the change up:
        git.tag(mytag, "mytag moved");
        git.checkout("master");
        git.deleteBranch(tmpBranch);

        // at this point we're back on master, there are no other branches, "mytag" has been updated to a new commit:
        assertTrue(project.poll(listener).hasChanges(), "scm polling should detect commit3 change in 'mytag'");
        build(project, Result.SUCCESS, commitFile3);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after last build");
    }

    /*
     * Not specifying a branch string in the project implies that we should be polling for changes in
     * all branches.
     */
    @Test
    void testMultipleBranchBuild() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertTrue(project.poll(listener).hasChanges(), "scm polling should detect changes in 'master' branch");
        build(project, Result.SUCCESS, commitFile1, commitFile2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after last build");

        // now jump back...
        git.checkout(fork);

        // add some commits to the fork branch...
        final String forkFile1 = "forkFile1";
        commit(forkFile1, johnDoe, "Fork commit number 1");
        final String forkFile2 = "forkFile2";
        commit(forkFile2, johnDoe, "Fork commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling should detect changes in 'fork' branch");
        build(project, Result.SUCCESS, forkFile1, forkFile2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after last build");
    }

    @Test
    void testMultipleBranchesWithTags() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        assertTrue(git.tagExists(v1), "v1 tag exists");

        freeStyleBuild = build(project, Result.SUCCESS);
        assertTrue(freeStyleBuild.getChangeSet().isEmptySet(), "change set is empty");

        commit("file1", johnDoe, "change to file1");
        git.tag("none", "latest");

        freeStyleBuild = build(project, Result.SUCCESS);

        ObjectId tag = git.revParse(Constants.R_TAGS + v1);
        GitSCM scm = (GitSCM)project.getScm();
        BuildData buildData = scm.getBuildData(freeStyleBuild);

        assertEquals(tag, buildData.lastBuild.getSHA1(), "last build matches the v1 tag revision");
    }

    @Issue("JENKINS-19037")
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Test
    void testBlankRepositoryName() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        new GitSCM(null);
    }

    @Issue("JENKINS-10060")
    @Test
    void testSubmoduleFixup() throws Exception {
        /* Unreliable on Windows and not a platform specific test */
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
        r.jenkins.rebuildDependencyGraph();


        FreeStyleBuild ub = r.buildAndAssertSuccess(u);
        for  (int i=0; (d.getLastBuild()==null || d.getLastBuild().isBuilding()) && i<100; i++) // wait only up to 10 sec to avoid infinite loop
            Thread.sleep(100);

        FreeStyleBuild db = d.getLastBuild();
        assertNotNull(db,"downstream build didn't happen");

        db = r.waitForCompletion(db);
        r.assertBuildStatusSuccess(db);
    }

    // eg: "jane doe and john doe should be the culprits", culprits, [johnDoe, janeDoe])
    static public void assertCulprits(String assertMsg, Set<User> actual, PersonIdent[] expected)
    {
        List<String> fullNames =
                actual.stream().map(User::getFullName).toList();

        for(PersonIdent p : expected)
        {
            assertTrue(fullNames.contains(p.getName()), assertMsg);
        }
    }

    @Test
    void testHideCredentials() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        // setup global config
        List<UserRemoteConfig> remoteConfigs = GitSCM.createRepoList("https://github.com/jenkinsci/git-plugin", "github");
        project.setScm(new GitSCM(remoteConfigs,
                Collections.singletonList(new BranchSpec("master")), false, null, null, null, null));

        GitSCM scm = (GitSCM) project.getScm();
        final DescriptorImpl descriptor = scm.getDescriptor();
        assertFalse(scm.isHideCredentials(), "Wrong initial value for hide credentials");
        descriptor.setHideCredentials(true);
        assertTrue(scm.isHideCredentials(), "Hide credentials not set");

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
    void testEmailCommitter() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

        // setup global config
        GitSCM scm = (GitSCM) project.getScm();
        final DescriptorImpl descriptor = scm.getDescriptor();
        assertFalse(scm.isCreateAccountBasedOnEmail(), "Wrong initial value for create account based on e-mail");
        descriptor.setCreateAccountBasedOnEmail(true);
        assertTrue(scm.isCreateAccountBasedOnEmail(), "Create account based on e-mail not set");

        assertFalse(scm.isUseExistingAccountWithSameEmail(), "Wrong initial value for use existing user if same e-mail already found");
        descriptor.setUseExistingAccountWithSameEmail(true);
        assertTrue(scm.isUseExistingAccountWithSameEmail(), "Use existing user if same e-mail already found is not set");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

        final String commitFile2 = "commitFile2";

        final PersonIdent jeffDoe = new PersonIdent("Jeff Doe", "jeff@doe.com");
        commit(commitFile2, jeffDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");
        //... and build it...

        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();

        assertEquals(1, culprits.size(), "The build should have only one culprit");
        User culprit = culprits.iterator().next();
        assertEquals(jeffDoe.getEmailAddress(), culprit.getId(), "");
        assertEquals(jeffDoe.getName(), culprit.getFullName(), "");

        r.assertBuildStatusSuccess(build);
    }

    @Issue("JENKINS-59868")
    @Test
    void testNonExistentWorkingDirectoryPoll() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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

    @Disabled("consistently fails, needs more analysis")
    @Test
    void testFetchFromMultipleRepositories() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("second", secondRepo.getRoot(), listener);
        List<UserRemoteConfig> remotes = new ArrayList<>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        project.setScm(new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("master")),
                null, null,
                Collections.emptyList()));

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        /* Diagnostic help - for later use */
        SCMRevisionState baseline = project.poll(listener).baseline;
        Change change = project.poll(listener).change;
        SCMRevisionState remote = project.poll(listener).remote;
        String assertionMessage = MessageFormat.format("polling incorrectly detected change after build. Baseline: {0}, Change: {1}, Remote: {2}", baseline, change, remote);
        assertFalse(project.poll(listener).hasChanges(), assertionMessage);

        final String commitFile2 = "commitFile2";
        secondTestRepo.commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue(project.poll(listener).hasChanges(), "scm polling did not detect commit2 change");
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        r.assertBuildStatusSuccess(build2);
        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
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
                Collections.emptyList()));

        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);
        r.assertBuildStatusSuccess(build);
    }

    @Issue("JENKINS-26268")
    @Test
    void testBranchSpecAsSHA1WithMultipleRepositories() throws Exception {
        String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit 1 from testBranchSpecAsSHA1WithMultipleRepositories");
        branchSpecWithMultipleRepositories(testRepo.git.revParse("HEAD").getName());
    }

    @Issue("JENKINS-26268")
    @Test
    void testBranchSpecAsRemotesOriginMasterWithMultipleRepositories() throws Exception {
        String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit 1 from testBranchSpecAsSHA1WithMultipleRepositories");
        branchSpecWithMultipleRepositories("remotes/origin/master");
    }

    @Issue("JENKINS-25639")
    @Test
    void testCommitDetectedOnlyOnceInMultipleRepositories() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("secondRepo", secondRepo.getRoot(), listener);
        List<UserRemoteConfig> remotes = new ArrayList<>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        GitSCM gitSCM = new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("origin/master")),
                null, null,
                Collections.emptyList());
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
    void testMerge() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new GitSCMSlowTest.TestPreBuildMerge(new UserMergeOptions("origin", "integration", "default", MergeCommand.GitPluginFastForwardMode.FF)));
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

    @Issue("JENKINS-20392")
    @Test
    void testMergeChangelog() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
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
        assertEquals(1, changeLog.getItems().length, "Changelog should contain one item");

        GitChangeSet singleChange = (GitChangeSet) changeLog.getItems()[0];
        assertEquals(commitMessage, singleChange.getComment().trim(), "Changelog should contain commit number 2");
    }

    @Test
    void testMergeFailed() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
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

    @Issue("JENKINS-25191")
    @Test
    void testMultipleMergeFailed() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
    	FreeStyleProject project = setupSimpleProject("master");
    	
    	GitSCM scm = new GitSCM(
    			createRemoteRepositories(),
    			Collections.singletonList(new BranchSpec("master")),
    			null, null,
    			Collections.emptyList());
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
    	
    	assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
    }

    @Test
    void testEnvironmentVariableExpansion() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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

    /*
     * Sample configuration that should result in no extensions at all
     */
    @Test
    void testDataCompatibility1() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject p = (FreeStyleProject) r.jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("GitSCMTest/old1.xml"));
        GitSCM oldGit = (GitSCM) p.getScm();
        assertEquals(Collections.emptyList(), oldGit.getExtensions().toList());
        assertEquals(0, oldGit.getSubmoduleCfg().size());
        assertEquals("git https://github.com/jenkinsci/model-ant-project.git", oldGit.getKey());
        assertThat(oldGit.getEffectiveBrowser(), instanceOf(GithubWeb.class));
        GithubWeb browser = (GithubWeb) oldGit.getEffectiveBrowser();
        assertEquals("https://github.com/jenkinsci/model-ant-project.git/", browser.getRepoUrl());
    }

    /**
     * Test a pipeline getting the value from several checkout steps gets the latest data every time.
     * @throws Exception If anything wrong happens
     */
    @Issue("JENKINS-53346")
    @Test
    void testCheckoutReturnsLatestValues() throws Exception {

        /* Exit test early if running on Windows and path will be too long */
        /* Known limitation of git for Windows 2.28.0 and earlier */
        /* Needs a longpath fix in git for Windows */
        String currentDirectoryPath = new File(".").getCanonicalPath();
        if (isWindows() && currentDirectoryPath.length() > 95) {
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pipeline-checkout-3-tags");
        p.setDefinition(new CpsFlowDefinition(
            """
            node {
                def tokenBranch = ''
                def tokenRevision = ''
                def checkout1 = checkout([$class: 'GitSCM', branches: [[name: 'git-1.1']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/jenkinsci/git-plugin.git']]])
                echo "checkout1: ${checkout1}"
                tokenBranch = tm '${GIT_BRANCH}'
                tokenRevision = tm '${GIT_REVISION}'
                echo "token1: ${tokenBranch}"
                echo "revision1: ${tokenRevision}"
                def checkout2 = checkout([$class: 'GitSCM', branches: [[name: 'git-2.0.2']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/jenkinsci/git-plugin.git']]])
                echo "checkout2: ${checkout2}"
                tokenBranch = tm '${GIT_BRANCH,all=true}'
                tokenRevision = tm '${GIT_REVISION,length=8}'
                echo "token2: ${tokenBranch}"
                echo "revision2: ${tokenRevision}"
                def checkout3 = checkout([$class: 'GitSCM', branches: [[name: 'git-3.0.0']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/jenkinsci/git-plugin.git']]])
                echo "checkout3: ${checkout3}"
                tokenBranch = tm '${GIT_BRANCH,fullName=true}'
                tokenRevision = tm '${GIT_REVISION,length=6}'
                echo "token3: ${tokenBranch}"
                echo "revision3: ${tokenRevision}"
            }
            """, true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        
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
    void testPleaseDontContinueAnyway() throws Exception {
        /* Wastes time waiting for the build to fail */
        /* Only run on non-Windows and approximately 50% of test runs */
        /* On Windows, it requires 150 seconds before test finishes */
        if (isWindows() || random.nextBoolean()) {
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        // create an empty repository with some commits
        testRepo.commit("a","foo",johnDoe, "added");

        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new GitSCM(testRepo.gitDir.getAbsolutePath()));

        r.buildAndAssertSuccess(p);

        // this should fail as it fails to fetch
        p.setScm(new GitSCM("http://localhost:4321/no/such/repository.git"));
        r.buildAndAssertStatus(Result.FAILURE, p);
    }

    @Issue("JENKINS-19108")
    @Test
    void testCheckoutToSpecificBranch() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject p = createFreeStyleProject();
        GitSCM oldGit = new GitSCM("https://github.com/jenkinsci/model-ant-project.git/");
        setupJGit(oldGit);
        oldGit.getExtensions().add(new LocalBranch("master"));
        p.setScm(oldGit);

        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        GitClient gc = Git.with(StreamTaskListener.fromStdout(),null).in(b.getWorkspace()).getClient();
        gc.withRepository((RepositoryCallback<Void>) (repo, channel) -> {
            Ref head = repo.findRef("HEAD");
            assertTrue(head.isSymbolic(),"Detached HEAD");
            Ref t = head.getTarget();
            assertEquals("refs/heads/master", t.getName());

            return null;
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
    void testCheckoutToDefaultLocalBranch_StarStar() throws Exception {
       assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new LocalBranch("**"));
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH), "GIT_BRANCH");
       assertEquals("master", getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH), "GIT_LOCAL_BRANCH");
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
    void testCheckoutToDefaultLocalBranch_NULL() throws Exception {
       assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new LocalBranch(""));
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH), "GIT_BRANCH");
       assertEquals("master", getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH), "GIT_LOCAL_BRANCH");
    }

    /*
     * Verifies that GIT_LOCAL_BRANCH is not set if LocalBranch extension
     * is not configured.
     */
    @Test
    void testCheckoutSansLocalBranchExtension() throws Exception {
       assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH), "GIT_BRANCH");
        assertNull(getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH), "GIT_LOCAL_BRANCH");
    }

    /*
     * Verifies that GIT_CHECKOUT_DIR is set to "checkoutDir" if RelativeTargetDirectory extension
     * is configured.
     */
    @Test
    void testCheckoutRelativeTargetDirectoryExtension() throws Exception {
       assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
       FreeStyleProject project = setupProject("master", false, "checkoutDir");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new RelativeTargetDirectory("checkoutDir"));
       FreeStyleBuild build1 = build(project, "checkoutDir", Result.SUCCESS, commitFile1);

       assertEquals("checkoutDir", getEnvVars(project).get(GitSCM.GIT_CHECKOUT_DIR), "GIT_CHECKOUT_DIR");
    }

    /*
     * Verifies that GIT_CHECKOUT_DIR is not set if RelativeTargetDirectory extension
     * is not configured.
     */
    @Test
    void testCheckoutSansRelativeTargetDirectoryExtension() throws Exception {
       assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        assertNull(getEnvVars(project).get(GitSCM.GIT_CHECKOUT_DIR), "GIT_CHECKOUT_DIR");
    }

    @Test
    void testCheckoutFailureIsRetryable() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
            r.waitForMessage("java.io.IOException: Could not checkout", build2);
        } finally {
            lock.delete();
        }
    }

    @Test
    void testSparseCheckoutAfterNormalCheckout() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    void testNormalCheckoutAfterSparseCheckout() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
    @Issue("JENKINS-22009")
    void testPolling_environmentValueInBranchSpec() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                null, null,
                Collections.emptyList());
        project.setScm(scm);
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_BRANCH", "master")));

        // commit something in order to create an initial base version in git
        commit("toto/commitFile1", johnDoe, "Commit number 1");

        // build the project
        build(project, Result.SUCCESS);

        assertFalse(project.poll(listener).hasChanges(), "No changes to git since last build, thus no new build is expected");
    }

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

        assertTrue(project.poll(listener).hasChanges(),"polling should detect changes");

        // build the project
        build(project, Result.SUCCESS);

        /* Expects 1 build because the build of someBranch incorporates all
         * the changes from the master branch as well as the changes from someBranch.
         */
        assertEquals(1, project.getBuilds().size(), "Wrong number of builds");

        assertFalse(project.poll(listener).hasChanges(),"polling should not detect changes");
    }

    @Issue("JENKINS-29066")
    @Test
    void testPolling_parentHead() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        baseTestPolling_parentHead(Collections.emptyList());
    }

    @Issue("JENKINS-29066")
    @Test
    void testPolling_parentHead_DisableRemotePoll() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        baseTestPolling_parentHead(Collections.singletonList(new DisableRemotePoll()));
    }

    @Test
    void testPollingAfterManualBuildWithParametrizedBranchSpec() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                null, null,
                Collections.emptyList());
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
        r.assertBuildStatus(Result.SUCCESS, build);

        assertFalse(project.poll(listener).hasChanges(), "No changes to git since last build");

        git.checkout("manualbranch");
        commit("file2", johnDoe, "Commit to manually build branch");
        assertFalse(project.poll(listener).hasChanges(), "No changes to tracked branch");

        git.checkout("trackedbranch");
        commit("file3", johnDoe, "Commit to tracked branch");
        assertTrue(project.poll(listener).hasChanges(), "A change should be detected in tracked branch");
        
    }
    
    private static final class FakeParametersAction implements EnvironmentContributingAction, Serializable {
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

        @Serial
        private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        }

        @Serial
        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        }

        @Serial
        private void readObjectNoData() throws ObjectStreamException {
        }
    }

    @Test
    void testPolling_CanDoRemotePollingIfOneBranchButMultipleRepositories() throws Exception {
                assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
		FreeStyleProject project = createFreeStyleProject();
		List<UserRemoteConfig> remoteConfigs = new ArrayList<>();
		remoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "", null));
		remoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "someOtherRepo", "", null));
		GitSCM scm = new GitSCM(remoteConfigs,
				Collections.singletonList(new BranchSpec("origin/master")), false,
				Collections.emptyList(), null, null,
				Collections.emptyList());
		project.setScm(scm);
		commit("commitFile1", johnDoe, "Commit number 1");

		FreeStyleBuild first_build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
        r.assertBuildStatus(Result.SUCCESS, first_build);

		first_build.getWorkspace().deleteContents();
		PollingResult pollingResult = scm.poll(project, null, first_build.getWorkspace(), listener, null);
		assertFalse(pollingResult.hasChanges());
	}

    @Issue("JENKINS-24467")
    @Test
    void testPolling_environmentValueAsEnvironmentContributingAction() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        // branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                null, null,
                Collections.emptyList());
        project.setScm(scm);

        // Initial commit and build
        commit("toto/commitFile1", johnDoe, "Commit number 1");
        String brokenPath = "\\broken/path\\of/doom";
        final StringParameterValue real_param = new StringParameterValue("MY_BRANCH", "master");
        final StringParameterValue fake_param = new StringParameterValue("PATH", brokenPath);

        final Action[] actions = {new ParametersAction(real_param), new FakeParametersAction(fake_param)};

        // SECURITY-170 - have to use ParametersDefinitionProperty
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_BRANCH", "master")));

        FreeStyleBuild first_build = project.scheduleBuild2(0, new Cause.UserIdCause(), actions).get();
        r.assertBuildStatus(Result.SUCCESS, first_build);

        Launcher launcher = workspace.createLauncher(listener);
        final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener);

        assertEquals("master", environment.get("MY_BRANCH"));
        assertNotSame(brokenPath, environment.get("PATH"), "Environment path should not be broken path");
    }

    private void checkNumberedBuildScmName(FreeStyleProject project, int buildNumber,
            String expectedScmName, GitSCM git) throws Exception {

        final BuildData buildData = git.getBuildData(project.getBuildByNumber(buildNumber));
        assertEquals(expectedScmName, buildData.getScmName(), "Wrong SCM Name");
    }

    /* A null pointer exception was detected because the plugin failed to
     * write a branch name to the build data, so there was a SHA1 recorded 
     * in the build data, but no branch name.
     */
    // Testing deprecated buildEnvVars
    @Test
    @Deprecated
    void testNoNullPointerExceptionWithNullBranch() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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

    // Testing deprecated buildEnvVars
    @Test
    @Deprecated
    void testBuildEnvVarsLocalBranchStarStar() throws Exception {
       assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
       
       assertEquals("origin/master", env.get("GIT_BRANCH"), "GIT_BRANCH");
       assertEquals("master", env.get("GIT_LOCAL_BRANCH"), "GIT_LOCAL_BRANCH");

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    // Testing deprecated buildEnvVars
    @Test
    @Deprecated
    void testBuildEnvVarsLocalBranchNull() throws Exception {
       assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
       
       assertEquals("origin/master", env.get("GIT_BRANCH"), "GIT_BRANCH");
       assertEquals("master", env.get("GIT_LOCAL_BRANCH"), "GIT_LOCAL_BRANCH");

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    // testing deprecated buildEnvVars
    @Test
    @Deprecated
    void testBuildEnvVarsLocalBranchNotSet() throws Exception {
       assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
       
       assertEquals("origin/master", env.get("GIT_BRANCH"), "GIT_BRANCH");
        assertNull(env.get("GIT_LOCAL_BRANCH"), "GIT_LOCAL_BRANCH");

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    @Test
    void testBuildEnvironmentVariablesSingleRemote() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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

        assertEquals("origin/master", env.get("GIT_BRANCH"), "GIT_BRANCH is invalid");
        assertNull(env.get("GIT_LOCAL_BRANCH"), "GIT_LOCAL_BRANCH is invalid");
        assertEquals(sha1.getName(), env.get("GIT_COMMIT"), "GIT_COMMIT is invalid");
        assertEquals(testRepo.gitDir.getAbsolutePath(), env.get("GIT_URL"), "GIT_URL is invalid");
        assertNull(env.get("GIT_URL_1"), "GIT_URL_1 should not have been set");
    }

    @Test
    void testBuildEnvironmentVariablesMultipleRemotes() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
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
                Collections.emptyList());
        project.setScm(scm);

        Map<String, String> env = new HashMap<>();
        scm.buildEnvironment(build, env);

        assertEquals("origin/master", env.get("GIT_BRANCH"), "GIT_BRANCH is invalid");
        assertNull(env.get("GIT_LOCAL_BRANCH"), "GIT_LOCAL_BRANCH is invalid");
        assertEquals(sha1.getName(), env.get("GIT_COMMIT"), "GIT_COMMIT is invalid");
        assertEquals(testRepo.gitDir.getAbsolutePath(), env.get("GIT_URL"), "GIT_URL is invalid");
        assertEquals(testRepo.gitDir.getAbsolutePath(), env.get("GIT_URL_1"), "GIT_URL_1 is invalid");
        assertEquals(upstreamRepoUrl, env.get("GIT_URL_2"), "GIT_URL_2 is invalid");
        assertNull(env.get("GIT_URL_3"), "GIT_URL_3 should not have been set");
    }

    @Issue("JENKINS-38241")
    @Test
    void testCommitMessageIsPrintedToLogs() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        sampleRepo.init();
        sampleRepo.write("file", "v1");
        sampleRepo.git("commit", "--all", "--message=test commit");
        FreeStyleProject p = setupSimpleProject("master");
        Run<?,?> run = r.buildAndAssertSuccess(p);
        r.waitForMessage("Commit message: \"test commit\"", run);
    }

    @Issue("JENKINS-73677")
    @Test
    void testExtensionsDecorateClientAfterSettingCredentials() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        FreeStyleProject project = setupSimpleProject("master");
        StandardCredentials extensionCredentials = createCredential(CredentialsScope.GLOBAL, "github");
        store.addCredentials(Domain.global(), extensionCredentials);
        // setup global config
        List<UserRemoteConfig> remoteConfigs = GitSCM.createRepoList("https://github.com/jenkinsci/git-plugin", null);
        project.setScm(new GitSCM(
            remoteConfigs,
            Collections.singletonList(new BranchSpec("master")),
            false,
            null,
            null,
            null,
            List.of(new TestSetCredentialsGitSCMExtension((StandardUsernameCredentials) extensionCredentials))));
        sampleRepo.init();
        sampleRepo.write("file", "v1");
        sampleRepo.git("commit", "--all", "--message=test commit");
        Run<?, ?> run = r.buildAndAssertSuccess(project);
        r.waitForMessage("using GIT_ASKPASS to set credentials " + extensionCredentials.getDescription(), run);
    }

    private void setupJGit(GitSCM git) {
        git.gitTool="jgit";
        r.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(new JGitTool(Collections.emptyList()));
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

    private StandardCredentials createCredential(CredentialsScope scope, String id) throws FormException {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password-needs-to-be-14");
    }

    public static class TestSetCredentialsGitSCMExtension extends GitSCMExtension {

        private final StandardUsernameCredentials credentials;

        public TestSetCredentialsGitSCMExtension(StandardUsernameCredentials credentials) {
            this.credentials = credentials;
        }

        @Override
        public GitClient decorate(GitSCM scm, GitClient git) throws GitException {
            git.setCredentials(credentials);
            return git;
        }
    }
}
