package hudson.plugins.git;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.View;
import hudson.triggers.SCMTrigger;
import hudson.util.RunList;
import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import jakarta.servlet.http.HttpServletRequest;

@RunWith(Theories.class)
public class GitStatusTheoriesTest extends AbstractGitProject {

    private GitStatus gitStatus;
    private HttpServletRequest requestWithNoParameter;
    private HttpServletRequest requestWithParameter;
    private String repoURL;
    private String branch;
    private String sha1;
    private String notifyCommitApiToken;

    @Before
    public void setUp() throws Exception {
        GitStatus.setAllowNotifyCommitParameters(false);
        GitStatus.setSafeParametersForTest(null);
        this.gitStatus = new GitStatus();
        this.requestWithNoParameter = mock(HttpServletRequest.class);
        this.requestWithParameter = mock(HttpServletRequest.class);
        this.repoURL = new File(".").getAbsolutePath();
        this.branch = "**";
        this.sha1 = "7bb68ef21dc90bd4f7b08eca876203b2e049198d";
        if (r.jenkins != null) {
            this.notifyCommitApiToken = ApiTokenPropertyConfiguration.get().generateApiToken("test").getString("value");
        }
    }

    @After
    public void resetAllowNotifyCommitParameters() throws Exception {
        GitStatus.setAllowNotifyCommitParameters(false);
        GitStatus.setSafeParametersForTest(null);
    }

    @After
    public void waitForAllJobsToComplete() throws Exception {
        // Put JenkinsRule into shutdown state, trying to reduce Windows cleanup exceptions
        if (r != null && r.jenkins != null) {
            r.jenkins.doQuietDown();
        }
        // JenkinsRule cleanup throws exceptions during tearDown.
        // Reduce exceptions by a random delay from 0.5 to 0.9 seconds.
        // Adding roughly 0.7 seconds to these JenkinsRule tests is a small price
        // for fewer exceptions and for better Windows job cleanup.
        java.util.Random random = new java.util.Random();
        Thread.sleep(500L + random.nextInt(400));
        /* Windows job cleanup fails to delete build logs in some of these tests.
         * Wait for the jobs to complete before exiting the test so that the
         * build logs will not be active when the cleanup process tries to
         * delete them.
         */
        if (!isWindows() || r == null || r.jenkins == null) {
            return;
        }
        View allView = r.jenkins.getView("All");
        if (allView == null) {
            return;
        }
        RunList<Run> runList = allView.getBuilds();
        if (runList == null) {
            return;
        }
        runList.forEach((Run run) -> {
            try {
                Logger.getLogger(GitStatusTheoriesTest.class.getName()).log(Level.INFO, "Waiting for {0}", run);
                r.waitForCompletion(run);
            } catch (InterruptedException ex) {
                Logger.getLogger(GitStatusTheoriesTest.class.getName()).log(Level.SEVERE, "Interrupted waiting for GitStatusTheoriesTest job", ex);
            }
        });
    }

    @DataPoints("branchSpecPrefixes")
    public static final String[] BRANCH_SPEC_PREFIXES = new String[] {
            "",
            "refs/remotes/",
            "refs/heads/",
            "origin/",
            "remotes/origin/"
    };

    @Theory
    public void testDoNotifyCommitBranchWithSlash(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", branchSpecPrefix + "feature/awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "feature/awesome-feature", null, notifyCommitApiToken);

        Mockito.verify(trigger).run();
    }

    @Theory
    public void testDoNotifyCommitBranchWithoutSlash(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", branchSpecPrefix + "awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "awesome-feature", null, notifyCommitApiToken);

        Mockito.verify(trigger).run();
    }

    @Theory
    public void testDoNotifyCommitBranchByBranchRef(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", branchSpecPrefix + "awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "refs/heads/awesome-feature", null, notifyCommitApiToken);

        Mockito.verify(trigger).run();
    }

    private SCMTrigger setupProjectWithTrigger(String url, String branchString, boolean ignoreNotifyCommit) throws Exception {
        SCMTrigger trigger = Mockito.mock(SCMTrigger.class);
        Mockito.doReturn(ignoreNotifyCommit).when(trigger).isIgnorePostCommitHooks();
        setupProject(url, branchString, trigger);
        return trigger;
    }

    private void setupProject(String url, String branchString, SCMTrigger trigger) throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        GitSCM git = new GitSCM(
                Collections.singletonList(new UserRemoteConfig(url, null, null, null)),
                Collections.singletonList(new BranchSpec(branchString)),
                null, null,
                Collections.emptyList());
        project.setScm(git);
        if (trigger != null) project.addTrigger(trigger);
    }

    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote classloader issue
     */
    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
