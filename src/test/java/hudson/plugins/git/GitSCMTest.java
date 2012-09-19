package hudson.plugins.git;

import hudson.BulkChange;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.plugins.git.GitSCM.BuildChooserContextImpl;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.util.BuildChooserContext.ContextCallable;
import hudson.plugins.parameterizedtrigger.BuildTrigger;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.util.IOException2;
import hudson.util.StreamTaskListener;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

import org.eclipse.jgit.lib.PersonIdent;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link GitSCM}.
 * @author ishaaq
 */
public class GitSCMTest extends AbstractGitTestCase {

    /**
     * Basic test - create a GitSCM based project, check it out and build for the first time.
     * Next test that polling works correctly, make another commit, check that polling finds it,
     * then build it and finally test the build culprits as well as the contents of the workspace.
     * @throws Exception if an exception gets thrown.
     */
    public void testBasic() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.pollSCMChanges(listener));
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));
    }

    public void testBasicRemotePoll() throws Exception {
//        FreeStyleProject project = setupProject("master", true, false);
        FreeStyleProject project = setupProject("master", false, null, null, null, true, null);
        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.pollSCMChanges(listener));
        // ... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));
    }

    public void testBasicIncludedRegion() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, null, ".*3");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should not have been included", project.pollSCMChanges(listener));

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.pollSCMChanges(listener));

        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());
        
        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));
    }

    public void testBasicExcludedRegion() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, ".*2", null, null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should have been excluded", project.pollSCMChanges(listener));

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.pollSCMChanges(listener));
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());

        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));
    }

    @Bug(value = 8342)
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

    public void testBasicExcludedUser() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, "Jane Doe", null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should have been excluded", project.pollSCMChanges(listener));
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.pollSCMChanges(listener));
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());

        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

    }

    public void testBasicInSubdir() throws Exception {
        FreeStyleProject project = setupProject("master", false, "subdir");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, "subdir", Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.pollSCMChanges(listener));
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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));
    }

    public void testBasicWithSlave() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(createSlave().getSelfLabel());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.pollSCMChanges(listener));
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));
    }

    // For HUDSON-7547
    public void testBasicWithSlaveNoExecutorsOnMaster() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        hudson.setNumExecutors(0);
        hudson.setNodes(hudson.getNodes());

        project.setAssignedLabel(createSlave().getSelfLabel());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.pollSCMChanges(listener));
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));
    }

    public void testAuthorOrCommitterFalse() throws Exception {
        // Test with authorOrCommitter set to false and make sure we get the committer.
        FreeStyleProject project = setupProject("master", false);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.pollSCMChanges(listener));

        final FreeStyleBuild secondBuild = build(project, Result.SUCCESS, commitFile2);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final Set<User> secondCulprits = secondBuild.getCulprits();

        assertEquals("The build should have only one culprit", 1, secondCulprits.size());
        assertEquals("Did not get the committer as the change author with authorOrCommiter==false",
                     janeDoe.getName(), secondCulprits.iterator().next().getFullName());
    }

    public void testAuthorOrCommitterTrue() throws Exception {
        // Next, test with authorOrCommitter set to true and make sure we get the author.
        FreeStyleProject project = setupProject("master", true);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.pollSCMChanges(listener));

        final FreeStyleBuild secondBuild = build(project, Result.SUCCESS, commitFile2);

        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));

        final Set<User> secondCulprits = secondBuild.getCulprits();

        assertEquals("The build should have only one culprit", 1, secondCulprits.size());
        assertEquals("Did not get the author as the change author with authorOrCommiter==true",
                johnDoe.getName(), secondCulprits.iterator().next().getFullName());
    }

    /**
     * Method name is self-explanatory.
     */
    public void testNewCommitToUntrackedBranchDoesNotTriggerBuild() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        //now create and checkout a new branch:
        git.branch("untracked");
        git.checkout("untracked");
        //.. and commit to it:
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertFalse("scm polling should not detect commit2 change because it is not in the branch we are tracking.", project.pollSCMChanges(listener));
    }

    public void testBranchIsAvailableInEvironment() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertEquals("master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
    }

    // For HUDSON-7411
    public void testNodeEnvVarsAvailable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        Node s = createSlave();
        setVariables(s, new Entry("TESTKEY", "slaveValue"));
        project.setAssignedLabel(s.getSelfLabel());
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertEquals("slaveValue", getEnvVars(project).get("TESTKEY"));
    }

    /**
     * A previous version of GitSCM would only build against branches, not tags. This test checks that that
     * regression has been fixed.
     */
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
        assertFalse("scm polling should not detect any more changes since mytag is untouched right now", project.pollSCMChanges(listener));
        build(project, Result.FAILURE);  // fail, because there's nothing to be checked out here

        // tag it, then delete the tmp branch
        git.tag(mytag, "mytag initial");
        git.checkout("master");
        git.launchCommand("branch", "-D", tmpBranch);

        // at this point we're back on master, there are no other branches, tag "mytag" exists but is
        // not part of "master"
        assertTrue("scm polling should detect commit2 change in 'mytag'", project.pollSCMChanges(listener));
        build(project, Result.SUCCESS, commitFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.pollSCMChanges(listener));

        // now, create tmp branch again against mytag:
        git.checkout(mytag);
        git.branch(tmpBranch);
        // another commit:
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertFalse("scm polling should not detect any more changes since mytag is untouched right now", project.pollSCMChanges(listener));

        // now we're going to force mytag to point to the new commit, if everything goes well, gitSCM should pick the change up:
        git.tag(mytag, "mytag moved");
        git.checkout("master");
        git.launchCommand("branch", "-D", tmpBranch);

        // at this point we're back on master, there are no other branches, "mytag" has been updated to a new commit:
        assertTrue("scm polling should detect commit3 change in 'mytag'", project.pollSCMChanges(listener));
        build(project, Result.SUCCESS, commitFile3);
        assertFalse("scm polling should not detect any more changes after last build", project.pollSCMChanges(listener));
    }

    /**
     * Not specifying a branch string in the project implies that we should be polling for changes in
     * all branches.
     */
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
        assertTrue("scm polling should detect changes in 'master' branch", project.pollSCMChanges(listener));
        build(project, Result.SUCCESS, commitFile1, commitFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.pollSCMChanges(listener));

        // now jump back...
        git.checkout(fork);

        // add some commits to the fork branch...
        final String forkFile1 = "forkFile1";
        commit(forkFile1, johnDoe, "Fork commit number 1");
        final String forkFile2 = "forkFile2";
        commit(forkFile2, johnDoe, "Fork commit number 2");
        assertTrue("scm polling should detect changes in 'fork' branch", project.pollSCMChanges(listener));
        build(project, Result.SUCCESS, forkFile1, forkFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.pollSCMChanges(listener));
    }

    /**
     * With multiple branches specified in the project and having commits from a user
     * excluded should not build the excluded revisions when another branch changes.
     */
    public void testMultipleBranchWithExcludedUser() throws Exception {
        final String branch1 = "Branch1";
        final String branch2 = "Branch2";

        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("master"));
        branches.add(new BranchSpec(branch1));
        branches.add(new BranchSpec(branch2));
        final FreeStyleProject project = setupProject(branches, false, null,
                                                      null, janeDoe.getName(),
                                                      null, false, null);

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
    }

    public void testIncExcRegionMergeCommit() throws Exception {
        final String master = "master";
        final String branch1 = "Branch1";
        final String branch2 = "Branch2";

        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec(master));
        branches.add(new BranchSpec(branch1));
        branches.add(new BranchSpec(branch2));

        // Got 2 projects, each one should only build if changes in its own file
        FreeStyleProject projectA = setupProject(branches, false, null, ".*B", null, null, false, null);
        FreeStyleProject projectB = setupProject(branches, false, null, null, null, null, false, ".*B");

        final String commitFileA = "commitFile.A";
        final String commitFileB = "commitFile.B";
        commit(commitFileA, johnDoe, "Commit number 1");
        commit(commitFileB, johnDoe, "Commit number 2");

        // create a branch so we can get back to it later...
        git.branch(branch1);

        build(projectA, Result.SUCCESS, commitFileA, commitFileB);
        build(projectB, Result.SUCCESS, commitFileA, commitFileB);

        assertFalse("scm polling should not detect changes in projectA after initial build", projectA.poll(listener).hasChanges());
        assertFalse("scm polling should not detect changes in projectB after initial build", projectB.poll(listener).hasChanges());

        final String masterFile1A = "masterFile1.A";
        final String masterFile2A = "masterFile2.A";
        commit(masterFile1A, johnDoe, "Commit number 3");
        commit(masterFile2A, johnDoe, "Commit number 4");

        assertTrue("scm polling should detect changes in projectA after commit 4", projectA.poll(listener).hasChanges());
        assertFalse("scm polling should not detect changes in projectB after commit 4", projectB.poll(listener).hasChanges());

        build(projectA, Result.SUCCESS, masterFile1A, masterFile2A);

        assertFalse("scm polling should not detect changes in projectA after commit 4 build", projectA.poll(listener).hasChanges());

        // Change only B on this branch.
        git.checkout(branch1);

        final String branch1File1B = "branch1File1.B";
        final String branch1File2B = "branch1File2.B";
        commit(branch1File1B, johnDoe, "Commit number 5");
        commit(branch1File2B, johnDoe, "Commit number 6");

        assertFalse("scm polling should not detect changes in projectA on branch1", projectA.poll(listener).hasChanges());
        assertTrue("scm polling should detect changes in projectB on branch1", projectB.poll(listener).hasChanges());

        build(projectB, Result.SUCCESS, branch1File1B, branch1File2B);

        assertFalse("scm polling should not detect changes in projectB after branch1 build", projectB.poll(listener).hasChanges());

        // Merge the B changes back to master
        git.checkout(master);
        git.merge(branch1);

        assertFalse("scm polling should not detect changes in projectA after branch1 merge", projectA.poll(listener).hasChanges());
        assertTrue("scm polling should detect changes in projectB after branch1 merge", projectB.poll(listener).hasChanges());

        // create a branch so we can get back to it later...
        git.branch(branch2);

        build(projectB, Result.SUCCESS, branch1File1B, branch1File2B);

        assertFalse("scm polling should not detect changes in projectA after branch1 merge build", projectA.poll(listener).hasChanges());
        assertFalse("scm polling should not detect changes in projectB after branch1 merge build", projectB.poll(listener).hasChanges());

        final String masterFile3A = "masterFile3.A";
        final String masterFile4A = "masterFile4.A";
        commit(masterFile3A, johnDoe, "Commit number 7");
        commit(masterFile4A, johnDoe, "Commit number 8");

        assertTrue("scm polling should detect changes in projectA after commit 8", projectA.poll(listener).hasChanges());
        assertFalse("scm polling should not detect changes in projectB after commit 8", projectB.poll(listener).hasChanges());

        build(projectA, Result.SUCCESS, masterFile3A, masterFile4A);

        assertFalse("scm polling should not detect changes in projectA after commit 8 build", projectA.poll(listener).hasChanges());

        // Change both A and B on this branch
        git.checkout(branch2);

        final String branch2File1A = "branch2File1.A";
        final String branch2File2B = "branch2File2.B";
        commit(branch2File1A, johnDoe, "Commit number 9");
        commit(branch2File2B, johnDoe, "Commit number 10");

        assertTrue("scm polling should detect changes in projectA on branch2", projectA.poll(listener).hasChanges());
        assertTrue("scm polling should detect changes in projectB on branch2", projectB.poll(listener).hasChanges());

        build(projectA, Result.SUCCESS, branch2File1A, branch2File2B);
        build(projectB, Result.SUCCESS, branch2File1A, branch2File2B);

        assertFalse("scm polling should not detect changes in projectA after branch2 build", projectA.poll(listener).hasChanges());
        assertFalse("scm polling should not detect changes in projectB after branch2 build", projectB.poll(listener).hasChanges());

        // Merge A and B changes back to master
        git.checkout(master);
        git.merge(branch2);

        assertTrue("scm polling should detect changes in projectA after branch2 merge", projectA.poll(listener).hasChanges());
        assertTrue("scm polling should detect changes in projectB after branch2 merge", projectB.poll(listener).hasChanges());

        build(projectA, Result.SUCCESS, branch2File1A, branch2File2B);
        build(projectB, Result.SUCCESS, branch2File1A, branch2File2B);

        assertFalse("scm polling should not detect changes in projectA after branch2 merge build", projectA.poll(listener).hasChanges());
        assertFalse("scm polling should not detect changes in projectB after branch2 merge build", projectB.poll(listener).hasChanges());
    }

    @Bug(10060)
    public void testSubmoduleFixup() throws Exception {
        FilePath moduleWs = new FilePath(createTmpDir());
        GitAPI moduleRepo = new GitAPI("git", moduleWs, listener, new EnvVars());

        {// first we create a Git repository with submodule
            moduleRepo.init();
            moduleWs.child("a").touch(0);
            moduleRepo.add("a");
            moduleRepo.launchCommand("commit", "-m", "creating a module");

            git.launchCommand("submodule","add",moduleWs.getRemote(),"module1");
            git.launchCommand("commit", "-m", "creating a super project");
        }

        // configure two uproject 'u' -> 'd' that's chained together.
        FreeStyleProject u = createFreeStyleProject();
        FreeStyleProject d = createFreeStyleProject();

        u.setScm(new GitSCM(workDir.getPath()));
        u.getPublishersList().add(new BuildTrigger(new BuildTriggerConfig(d.getName(), ResultCondition.SUCCESS,
                new GitRevisionBuildParameters())));

        d.setScm(new GitSCM(workDir.getPath()));
        hudson.rebuildDependencyGraph();


        FreeStyleBuild ub = assertBuildStatusSuccess(u.scheduleBuild2(0));
        System.out.println(ub.getLog());
        for (int i=0; (d.getLastBuild()==null || d.getLastBuild().isBuilding()) && i<1200; i++) // wait only up to 2 mins to avoid infinite loop
            Thread.sleep(100);

        FreeStyleBuild db = d.getLastBuild();
        assertNotNull("downstream build didn't happen",db);
        assertBuildStatusSuccess(db);
    }

    public void testBuildChooserContext() throws Exception {
        final FreeStyleProject p = createFreeStyleProject();
        final FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0));

        BuildChooserContextImpl c = new BuildChooserContextImpl(p, b);
        c.actOnBuild(new ContextCallable<AbstractBuild<?,?>, Object>() {
            public Object invoke(AbstractBuild param, VirtualChannel channel) throws IOException, InterruptedException {
                assertSame(param,b);
                return null;
            }
        });
        c.actOnProject(new ContextCallable<AbstractProject<?,?>, Object>() {
            public Object invoke(AbstractProject param, VirtualChannel channel) throws IOException, InterruptedException {
                assertSame(param,p);
                return null;
            }
        });
        DumbSlave s = createOnlineSlave();
        assertEquals(p.toString(), s.getChannel().call(new BuildChooserContextTestCallable(c)));
    }

    private static class BuildChooserContextTestCallable implements Callable<String,IOException> {
        private final BuildChooserContext c;

        public BuildChooserContextTestCallable(BuildChooserContext c) {
            this.c = c;
        }

        public String call() throws IOException {
            try {
                return c.actOnProject(new ContextCallable<AbstractProject<?,?>, String>() {
                    public String invoke(AbstractProject<?,?> param, VirtualChannel channel) throws IOException, InterruptedException {
                        assertTrue(channel instanceof Channel);
                        assertTrue(Hudson.getInstance()!=null);
                        return param.toString();
                    }
                });
            } catch (InterruptedException e) {
                throw new IOException2(e);
            }
        }
    }

    // eg: "jane doe and john doe should be the culprits", culprits, [johnDoe, janeDoe])
    static public void assertCulprits(String assertMsg, Set<User> actual, PersonIdent[] expected)
    {
        Collection<String> fullNames = Collections2.transform(actual, new Function<User,String>() {
            public String apply(User u)
            {
                return u.getFullName();
            }
        });

        for(PersonIdent p : expected)
        {
            assertTrue(assertMsg, fullNames.contains(p.getName()));
        }
    }

    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter) throws Exception {
        return setupProject(branchString, authorOrCommitter, null);
    }

    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
                                          String relativeTargetDir) throws Exception {
        return setupProject(branchString, authorOrCommitter, relativeTargetDir, null, null, null);
    }

    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
                                          String relativeTargetDir,
                                          String excludedRegions,
                                          String excludedUsers,
                                          String includedRegions) throws Exception {
        return setupProject(branchString, authorOrCommitter, relativeTargetDir, excludedRegions, excludedUsers, null, false, includedRegions);
    }

    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
            String relativeTargetDir,
            String excludedRegions,
            String excludedUsers,
            boolean fastRemotePoll,
            String includedRegions) throws Exception {
        return setupProject(branchString, authorOrCommitter, relativeTargetDir, excludedRegions, excludedUsers, null, fastRemotePoll, includedRegions);
    }

    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
                                          String relativeTargetDir, String excludedRegions,
                                          String excludedUsers, String localBranch, boolean fastRemotePoll,
                                          String includedRegions) throws Exception {
        return setupProject(Collections.singletonList(new BranchSpec(branchString)),
                            authorOrCommitter, relativeTargetDir, excludedRegions,
                            excludedUsers, localBranch, fastRemotePoll,
                            includedRegions);
    }

    private FreeStyleProject setupProject(List<BranchSpec> branches, boolean authorOrCommitter,
                                          String relativeTargetDir, String excludedRegions,
                                          String excludedUsers, String localBranch, boolean fastRemotePoll,
                                          String includedRegions) throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.setScm(new GitSCM(
                null,
                createRemoteRepositories(relativeTargetDir),
                branches,
                null,
                false, Collections.<SubmoduleConfig>emptyList(), false,
                false, new DefaultBuildChooser(), null, null, authorOrCommitter, relativeTargetDir, null,
                excludedRegions, excludedUsers, localBranch, false, false, false, fastRemotePoll, null, null, false,
                includedRegions, false));
        project.getBuildersList().add(new CaptureEnvironmentBuilder());
        return project;
    }

    private FreeStyleProject setupSimpleProject(String branchString) throws Exception {
        return setupProject(branchString,false);
    }

    private FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
        final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(build.getWorkspace().child(expectedNewlyCommittedFile).exists());
        }
        if(expectedResult != null) {
            assertBuildStatus(expectedResult, build);
        }
        return build;
    }

    private FreeStyleBuild build(final FreeStyleProject project, final String parentDir, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
        final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(build.getWorkspace().child(parentDir).child(expectedNewlyCommittedFile).exists());
        }
        if(expectedResult != null) {
            assertBuildStatus(expectedResult, build);
        }
        return build;
    }

    private EnvVars getEnvVars(FreeStyleProject project) {
        for (hudson.tasks.Builder b : project.getBuilders()) {
            if (b instanceof CaptureEnvironmentBuilder) {
                return ((CaptureEnvironmentBuilder)b).getEnvVars();
            }
        }
        return new EnvVars();
    }

    private void setVariables(Node node, Entry... entries) throws IOException {
        node.getNodeProperties().replaceBy(
                                           Collections.singleton(new EnvironmentVariablesNodeProperty(
                                                                                                      entries)));

    }

}
