package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.View;
import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitStatusTest;
import hudson.util.RunList;
import java.io.File;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;

public class BuildSingleRevisionOnlyTest extends AbstractGitTestCase {

    @After
    public void waitForAllJobsToComplete() {
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
                Logger.getLogger(GitStatusTest.class.getName()).log(Level.INFO, "Waiting for {0}", run);
                r.waitForCompletion(run);
            } catch (InterruptedException ex) {
                Logger.getLogger(GitStatusTest.class.getName()).log(Level.SEVERE, "Interrupted waiting for GitStatusTest job", ex);
            }
        });
    }

    @Test
    public void testSingleRevision() throws Exception {
        // This is the additional behaviour
        List<BranchSpec> branchSpec = new ArrayList<>();
        branchSpec.add(new BranchSpec("master"));
        branchSpec.add(new BranchSpec("foo"));
        branchSpec.add(new BranchSpec("bar"));
        FreeStyleProject project = setupProject(branchSpec, false, "",
                "","",
                "", false, "");

        ((GitSCM) project.getScm()).getExtensions().add(new BuildSingleRevisionOnly());
        final String commitFile = "commitFile1";
        // create the initial master commit
        commit(commitFile, johnDoe, "Initial commit in master");

        // create additional branches and commits
        git.branch("foo");
        git.branch("bar");
        git.checkoutBranch("foo", "master");
        commit(commitFile, johnDoe, "Commit in foo");
        git.checkoutBranch("bar", "master");
        commit(commitFile, johnDoe, "Commit in bar");

        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile);

        r.assertBuildStatusSuccess(build);
        boolean result = build.getLog(100).contains(
                "Scheduling another build to catch up with %s".formatted(project.getName()));
        Assert.assertFalse("Single revision scheduling did not prevent a build of a different revision", result);
    }

    @Test
    public void testMultiRevision() throws Exception {
        // This is the old and now default behaviour
        List<BranchSpec> branchSpec = new ArrayList<>();
        branchSpec.add(new BranchSpec("master"));
        branchSpec.add(new BranchSpec("foo"));
        branchSpec.add(new BranchSpec("bar"));
        FreeStyleProject project = setupProject(branchSpec, false, "",
                "","",
                "", false, "");

        final String commitFile = "commitFile1";
        // create the initial master commit
        commit(commitFile, johnDoe, "Initial commit in master");

        // create additional branches and commits
        git.branch("foo");
        git.branch("bar");
        git.checkoutBranch("foo", "master");
        commit(commitFile, johnDoe, "Commit in foo");
        git.checkoutBranch("bar", "master");
        commit(commitFile, johnDoe, "Commit in bar");

        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile);

        r.assertBuildStatusSuccess(build);
        r.waitForMessage("Scheduling another build to catch up with %s".formatted(project.getName()), build);

        // Wait briefly for newly scheduled job to start.
        // Once job has started, waitForAllJobsToComplete will hold the test until job completes.
        // Windows can remove log files once job completes.
        // Wait on non-Windows reduces log file InterruptedException from rule teardown before job completion.
        // Wait on non-Windows not strictly required but gives one less exception in the test log.
        java.util.Random random = new java.util.Random();
        Thread.sleep(500L + random.nextInt(300));
    }

    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote class loader issue
     */
    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
