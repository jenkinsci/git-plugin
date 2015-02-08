package hudson.plugins.git;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.GitSCM.BuildChooserContextImpl;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.*;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.util.BuildChooserContext.ContextCallable;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.plugins.git.util.GitUtils;
import hudson.plugins.parameterizedtrigger.BuildTrigger;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.tools.ToolProperty;
import hudson.util.IOException2;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.*;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.TestExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.util.*;
import org.eclipse.jgit.transport.RemoteConfig;

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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    public void testBasicRemotePoll() throws Exception {
//        FreeStyleProject project = setupProject("master", true, false);
        FreeStyleProject project = setupProject("master", false, null, null, null, true, null);
        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        // ... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    public void testBranchSpecWithRemotesMaster() throws Exception {
        FreeStyleProject projectMasterBranch = setupProject("remotes/origin/master", false, null, null, null, true, null);
        // create initial commit and build
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(projectMasterBranch, Result.SUCCESS, commitFile1);
      }
    
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

    public void testBranchSpecUsingTagWithSlash() throws Exception {
        FreeStyleProject projectMasterBranch = setupProject("path/tag", false, null, null, null, true, null);
        // create initial commit and build
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1 will be tagged with path/tag");
        testRepo.git.tag("path/tag", "tag with a slash in the tag name");
        build(projectMasterBranch, Result.SUCCESS, commitFile1);
      }
    
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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }
    
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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    public void testCleanBeforeCheckout() throws Exception {
    	FreeStyleProject p = setupProject("master", false, null, null, "Jane Doe", null);
        ((GitSCM)p.getScm()).getExtensions().add(new CleanBeforeCheckout());
        final String commitFile1 = "commitFile1";
        final String commitFile2 = "commitFile2";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        final FreeStyleBuild firstBuild = build(p, Result.SUCCESS, commitFile1);
        final String branch1 = "Branch1";
        final String branch2 = "Branch2";
        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("master"));
        branches.add(new BranchSpec(branch1));
        branches.add(new BranchSpec(branch2));
        git.branch(branch1);
        git.checkout(branch1);
        p.poll(listener).hasChanges();
        assertTrue(firstBuild.getLog().contains("Cleaning"));
        assertTrue(firstBuild.getLog().indexOf("Cleaning") > firstBuild.getLog().indexOf("Cloning")); //clean should be after clone
        assertTrue(firstBuild.getLog().indexOf("Cleaning") < firstBuild.getLog().indexOf("Checking out")); //clean before checkout
        assertTrue(firstBuild.getWorkspace().child(commitFile1).exists());
        git.checkout(branch1);
        final FreeStyleBuild secondBuild = build(p, Result.SUCCESS, commitFile2);
        p.poll(listener).hasChanges();
        assertTrue(secondBuild.getLog().contains("Cleaning"));
        assertTrue(secondBuild.getLog().indexOf("Cleaning") < secondBuild.getLog().indexOf("Fetching upstream changes")); 
        assertTrue(secondBuild.getWorkspace().child(commitFile2).exists());

        
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

    /**
     * With multiple branches specified in the project and having commits from a user
     * excluded should not build the excluded revisions when another branch changes.
     */
    /*
    @Bug(value = 8342)
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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

    }

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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    public void testBasicWithSlave() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(createSlave().getSelfLabel());

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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

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
        assertEquals("Did not get the committer as the change author with authorOrCommiter==false",
                     janeDoe.getName(), secondCulprits.iterator().next().getFullName());
    }

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
        git.checkout(Constants.HEAD, "untracked");
        //.. and commit to it:
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertFalse("scm polling should not detect commit2 change because it is not in the branch we are tracking.", project.poll(listener).hasChanges());
    }

    private String checkoutString(FreeStyleProject project, String envVar) {
        return "checkout -f " + getEnvVars(project).get(envVar);
    }

    public void testEnvVarsAvailable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        assertEquals("origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
        assertLogContains(getEnvVars(project).get(GitSCM.GIT_BRANCH), build1);

        assertLogContains(checkoutString(project, GitSCM.GIT_COMMIT), build1);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);

        assertLogNotContains(checkoutString(project, GitSCM.GIT_PREVIOUS_COMMIT), build2);
        assertLogContains(checkoutString(project, GitSCM.GIT_PREVIOUS_COMMIT), build1);

        assertLogNotContains(checkoutString(project, GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT), build2);
        assertLogContains(checkoutString(project, GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT), build1);
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

    @Bug(19037)
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void testBlankRepositoryName() throws Exception {
        new GitSCM(null);
    }

    @Bug(10060)
    public void testSubmoduleFixup() throws Exception {
        File repo = createTmpDir();
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
        hudson.rebuildDependencyGraph();


        FreeStyleBuild ub = assertBuildStatusSuccess(u.scheduleBuild2(0));
        System.out.println(ub.getLog());
        for  (int i=0; (d.getLastBuild()==null || d.getLastBuild().isBuilding()) && i<100; i++) // wait only up to 10 sec to avoid infinite loop
            Thread.sleep(100);

        FreeStyleBuild db = d.getLastBuild();
        assertNotNull("downstream build didn't happen",db);
        assertBuildStatusSuccess(db);
    }

    public void testBuildChooserContext() throws Exception {
        final FreeStyleProject p = createFreeStyleProject();
        final FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0));

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
                return c.actOnProject(new ContextCallable<Job<?,?>, String>() {
                    public String invoke(Job<?,?> param, VirtualChannel channel) throws IOException, InterruptedException {
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

    public void testEmailCommitter() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // setup global config
        final DescriptorImpl descriptor = (DescriptorImpl) project.getScm().getDescriptor();
        descriptor.setCreateAccountBasedOnEmail(true);

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

        assertBuildStatusSuccess(build);
    }

    public void testFetchFromMultipleRepositories() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("second", this, listener);
        List<UserRemoteConfig> remotes = new ArrayList<UserRemoteConfig>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        project.setScm(new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList()));

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        secondTestRepo.commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Bug(25639)
    public void testCommitDetectedOnlyOnceInMultipleRepositories() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("secondRepo", this, listener);
        List<UserRemoteConfig> remotes = new ArrayList<UserRemoteConfig>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        GitSCM gitSCM = new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("origin/master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(gitSCM);

        commit("commitFile1", johnDoe, "Commit number 1");
        FreeStyleBuild build = build(project, Result.SUCCESS, "commitFile1");

        commit("commitFile2", johnDoe, "Commit number 2");
        git = Git.with(listener, new EnvVars()).in(build.getWorkspace()).getClient();
        for (RemoteConfig remoteConfig : gitSCM.getRepositories()) {
            git.fetch_().from(remoteConfig.getURIs().get(0), remoteConfig.getFetchRefSpecs());
        }
        Collection<Revision> candidateRevisions = ((DefaultBuildChooser) (gitSCM).getBuildChooser()).getCandidateRevisions(false, "origin/master", git, listener, project.getLastBuild().getAction(BuildData.class), null);
        assertEquals(1, candidateRevisions.size());
    }

    public void testMerge() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "default", MergeCommand.GitPluginFastForwardMode.FF)));
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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Bug(20392)
    public void testMergeChangelog() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "default", MergeCommand.GitPluginFastForwardMode.FF)));
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

    public void testMergeWithSlave() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(createSlave().getSelfLabel());

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    public void testMergeFailed() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "", MergeCommand.GitPluginFastForwardMode.FF)));

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
        final FreeStyleBuild build2 = build(project, Result.FAILURE);
        assertBuildStatus(Result.FAILURE, build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }
    
    @Bug(25191)
    public void testMultipleMergeFailed() throws Exception {
    	FreeStyleProject project = setupSimpleProject("master");
    	
    	GitSCM scm = new GitSCM(
    			createRemoteRepositories(),
    			Collections.singletonList(new BranchSpec("master")),
    			false, Collections.<SubmoduleConfig>emptyList(),
    			null, null,
    			Collections.<GitSCMExtension>emptyList());
    	project.setScm(scm);
	scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration1", "", MergeCommand.GitPluginFastForwardMode.FF)));
	scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration2", "", MergeCommand.GitPluginFastForwardMode.FF)));
    	
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

    public void testMergeFailedWithSlave() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(createSlave().getSelfLabel());

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
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
        final FreeStyleBuild build2 = build(project, Result.FAILURE);
        assertBuildStatus(Result.FAILURE, build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }


    public void testMergeWithMatrixBuild() throws Exception {
        
        //Create a matrix project and a couple of axes
        MatrixProject project = createMatrixProject("xyz");
        project.setAxes(new AxisList(new Axis("VAR","a","b")));
        
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
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
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

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
        List<UserRemoteConfig> repoList = new ArrayList<UserRemoteConfig>();
        repoList.add(new UserRemoteConfig(url, null, null, null));
        return repoList;
    }

    /**
     * Makes sure that git browser URL is preserved across config round trip.
     */
    @Bug(22604)
    public void testConfigRoundtripURLPreserved() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        final String url = "https://github.com/jenkinsci/jenkins";
        GitRepositoryBrowser browser = new GithubWeb(url);
        GitSCM scm = new GitSCM(createRepoList(url),
                                Collections.singletonList(new BranchSpec("")),
                                false, Collections.<SubmoduleConfig>emptyList(),
                                browser, null, null);
        p.setScm(scm);
        configRoundtrip(p);
        assertEqualDataBoundBeans(scm,p.getScm());
        assertEquals("Wrong key", "git " + url, scm.getKey());
    }

    /**
     * Makes sure that the configuration form works.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/jenkins");
        p.setScm(scm);
        configRoundtrip(p);
        assertEqualDataBoundBeans(scm,p.getScm());
    }

    /**
     * Sample configuration that should result in no extensions at all
     */
    public void testDataCompatibility1() throws Exception {
        FreeStyleProject p = (FreeStyleProject) jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("GitSCMTest/old1.xml"));
        GitSCM git = (GitSCM) p.getScm();
        assertEquals(Collections.emptyList(), git.getExtensions().toList());
    }

    public void testPleaseDontContinueAnyway() throws Exception {
        // create an empty repository with some commits
        testRepo.commit("a","foo",johnDoe, "added");

        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new GitSCM(testRepo.gitDir.getAbsolutePath()));

        assertBuildStatusSuccess(p.scheduleBuild2(0));

        // this should fail as it fails to fetch
        p.setScm(new GitSCM("http://www.google.com/no/such/repository.git"));
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
    }

    @Bug(19108)
    public void testCheckoutToSpecificBranch() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        GitSCM git = new GitSCM("https://github.com/imod/dummy-tester.git");
        setupJGit(git);
        git.getExtensions().add(new LocalBranch("master"));
        p.setScm(git);

        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0));
        GitClient gc = Git.with(StreamTaskListener.fromStdout(),null).in(b.getWorkspace()).getClient();
        gc.withRepository(new RepositoryCallback<Void>() {
            public Void invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
                Ref head = repo.getRef("HEAD");
                assertTrue("Detached HEAD",head.isSymbolic());
                Ref t = head.getTarget();
                assertEquals(t.getName(),"refs/heads/master");

                return null;
            }
        });
    }

    public void testCheckoutFailureIsRetryable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // run build first to create workspace
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");

        // create lock file to simulate lock collision
        File lock = new File(build1.getWorkspace().toString(), ".git/index.lock");
        try {
            FileUtils.touch(lock);
            final FreeStyleBuild build2 = build(project, Result.FAILURE);
            assertLogContains("java.io.IOException: Could not checkout", build2);
        } finally {
            lock.delete();
        }
    }

    public void testInitSparseCheckout() throws Exception {
        FreeStyleProject project = setupProject("master", Lists.newArrayList(new SparseCheckoutPath("toto")));

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

    public void testInitSparseCheckoutBis() throws Exception {
        FreeStyleProject project = setupProject("master", Lists.newArrayList(new SparseCheckoutPath("titi")));

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

    public void testSparseCheckoutAfterNormalCheckout() throws Exception {
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

        ((GitSCM) project.getScm()).getExtensions().add(new SparseCheckoutPaths(Lists.newArrayList(new SparseCheckoutPath("titi"))));

        final FreeStyleBuild build2 = build(project, Result.SUCCESS);
        assertTrue(build2.getWorkspace().child("titi").exists());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertFalse(build2.getWorkspace().child("toto").exists());
        assertFalse(build2.getWorkspace().child(commitFile1).exists());
    }

    public void testNormalCheckoutAfterSparseCheckout() throws Exception {
        FreeStyleProject project = setupProject("master", Lists.newArrayList(new SparseCheckoutPath("titi")));

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

    public void testInitSparseCheckoutOverSlave() throws Exception {
        FreeStyleProject project = setupProject("master", Lists.newArrayList(new SparseCheckoutPath("titi")));
        project.setAssignedLabel(createSlave().getSelfLabel());

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

    /**
     * Test for JENKINS-22009.
     *
     * @throws Exception
     */
    public void testPolling_environmentValueInBranchSpec() throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                false, Collections.<SubmoduleConfig>emptyList(),
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

    private final class FakeParametersAction implements EnvironmentContributingAction, Serializable {
        // Test class for testPolling_environmentValueAsEnvironmentContributingAction test case
        final ParametersAction m_forwardingAction;

        public FakeParametersAction(StringParameterValue params) {
            this.m_forwardingAction = new ParametersAction(params);
        }

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

    private boolean gitVersionAtLeast(int neededMajor, int neededMinor) throws IOException, InterruptedException {
        final TaskListener procListener = StreamTaskListener.fromStderr();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final int returnCode = new Launcher.LocalLauncher(procListener).launch().cmds("git", "--version").stdout(out).join();
        assertEquals("git --version non-zero return code", 0, returnCode);
        assertFalse("Process listener logged an error", procListener.getLogger().checkError());
        final String versionOutput = out.toString().trim();
        final String[] fields = versionOutput.split(" ")[2].replaceAll("msysgit.", "").split("\\.");
        final int gitMajor = Integer.parseInt(fields[0]);
        final int gitMinor = Integer.parseInt(fields[1]);
        return gitMajor >= neededMajor && gitMinor >= neededMinor;
    }
    
	public void testPolling_CanDoRemotePollingIfOneBranchButMultipleRepositories() throws Exception {
		FreeStyleProject project = createFreeStyleProject();
		List<UserRemoteConfig> remoteConfigs = new ArrayList<UserRemoteConfig>();
		remoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "", null));
		remoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "someOtherRepo", "", null));
		GitSCM scm = new GitSCM(remoteConfigs,
				Collections.singletonList(new BranchSpec("origin/master")), false,
				Collections.<SubmoduleConfig> emptyList(), null, null,
				Collections.<GitSCMExtension> emptyList());
		project.setScm(scm);
		commit("commitFile1", johnDoe, "Commit number 1");

		FreeStyleBuild first_build = project.scheduleBuild2(0, new Cause.UserCause()).get();
		assertBuildStatus(Result.SUCCESS, first_build);

		first_build.getWorkspace().deleteContents();
		PollingResult pollingResult = scm.poll(project, null, first_build.getWorkspace(), listener, null);
		assertFalse(pollingResult.hasChanges());
	}

    /**
     * Test for JENKINS-24467.
     *
     * @throws Exception
     */
    public void testPolling_environmentValueAsEnvironmentContributingAction() throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);

        // Inital commit and build
        commit("toto/commitFile1", johnDoe, "Commit number 1");
        String brokenPath = "\\broken/path\\of/doom";
        if (!gitVersionAtLeast(1, 8)) {
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

        FreeStyleBuild first_build = project.scheduleBuild2(0, new Cause.UserCause(), actions).get();
        assertBuildStatus(Result.SUCCESS, first_build);

        Launcher launcher = workspace.createLauncher(listener);
        final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener);

        assertEquals(environment.get("MY_BRANCH"), "master");
        assertNotSame("Enviroment path should not be broken path", environment.get("PATH"), brokenPath);
    }

    /**
     * Tests that builds have the correctly specified Custom SCM names, associated with
     * each build.
     * @throws Exception on various exceptions
     */
    public void testCustomSCMName() throws Exception {
        final String branchName = "master";
        final FreeStyleProject project = setupProject(branchName, false);
        GitSCM git = (GitSCM) project.getScm();
        setupJGit(git);

        final String commitFile1 = "commitFile1";
        final String scmNameString1 = "";
        commit(commitFile1, johnDoe, "Commit number 1");
        assertTrue("scm polling should not detect any more changes after build",
                project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile1);
        final ObjectId commit1 = testRepo.git.revListAll().get(0);

        // Check unset build SCM Name carries
        final int buildNumber1 = notifyAndCheckScmName(
            project, commit1, scmNameString1, 1, git);

        final String scmNameString2 = "ScmName2";
        git.getExtensions().replace(new ScmName(scmNameString2));

        commit("commitFile2", johnDoe, "Commit number 2");
        assertTrue("scm polling should detect commit 2", project.poll(listener).hasChanges());
        final ObjectId commit2 = testRepo.git.revListAll().get(0);

        // Check second set SCM Name
        final int buildNumber2 = notifyAndCheckScmName(
            project, commit2, scmNameString2, 2, git);
        checkNumberedBuildScmName(project, buildNumber1, scmNameString1, git);

        final String scmNameString3 = "ScmName3";
        git.getExtensions().replace(new ScmName(scmNameString3));

        commit("commitFile3", johnDoe, "Commit number 3");
        assertTrue("scm polling should detect commit 3", project.poll(listener).hasChanges());
        final ObjectId commit3 = testRepo.git.revListAll().get(0);

        // Check third set SCM Name
        final int buildNumber3 = notifyAndCheckScmName(
            project, commit3, scmNameString3, 3, git);
        checkNumberedBuildScmName(project, buildNumber1, scmNameString1, git);
        checkNumberedBuildScmName(project, buildNumber2, scmNameString2, git);

        commit("commitFile4", johnDoe, "Commit number 4");
        assertTrue("scm polling should detect commit 4", project.poll(listener).hasChanges());
        final ObjectId commit4 = testRepo.git.revListAll().get(0);

        // Check third set SCM Name still set
        final int buildNumber4 = notifyAndCheckScmName(
            project, commit4, scmNameString3, 4, git);
        checkNumberedBuildScmName(project, buildNumber1, scmNameString1, git);
        checkNumberedBuildScmName(project, buildNumber2, scmNameString2, git);
        checkNumberedBuildScmName(project, buildNumber3, scmNameString3, git);
    }

    /**
     * Method performs HTTP get on "notifyCommit" URL, passing it commit by SHA1
     * and tests for custom SCM name build data consistency.
     * @param project project to build
     * @param commit commit to build
     * @param expectedBranch branch, that is expected to be built
     * @param ordinal number of commit to log into errors, if any
     * @param git git SCM
     * @throws Exception on various exceptions occur
     */
    private int notifyAndCheckScmName(FreeStyleProject project, ObjectId commit,
            String expectedScmName, int ordinal, GitSCM git) throws Exception {
        assertTrue("scm polling should detect commit " + ordinal, notifyCommit(project, commit));

        final Build build = project.getLastBuild();
        final BuildData buildData = git.getBuildData(build);
        assertEquals("Commit " + ordinal + " should be built", commit, buildData
                .getLastBuiltRevision().getSha1());

        assertEquals("SCM Name should be <" + expectedScmName + ">", expectedScmName, buildData
                .getScmName());

        return build.getNumber();
    }

    private void checkNumberedBuildScmName(FreeStyleProject project, int buildNumber,
            String expectedScmName, GitSCM git) throws Exception {

        final BuildData buildData = git.getBuildData(project.getBuildByNumber(buildNumber));
        System.out.println(buildData.toString());
        assertEquals("SCM Name should be " + expectedScmName, expectedScmName, buildData
                .getScmName());
    }

    /**
     * Tests that builds have the correctly specified branches, associated with
     * the commit id, passed with "notifyCommit" URL.
     * @see JENKINS-24133
     * @throws Exception on various exceptions
     */
    public void testSha1NotificationBranches() throws Exception {
        final String branchName = "master";
        final FreeStyleProject project = setupProject(branchName, false);
        final GitSCM git = (GitSCM) project.getScm();
        setupJGit(git);

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        assertTrue("scm polling should not detect any more changes after build",
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

    /**
     * Method performs HTTP get on "notifyCommit" URL, passing it commit by SHA1
     * and tests for build data consistency.
     * @param project project to build
     * @param commit commit to build
     * @param expectedBranch branch, that is expected to be built
     * @param ordinal number of commit to log into errors, if any
     * @param git git SCM
     * @throws Exception on various exceptions occur
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
     * @throws Exception on various exceptions
     */
    private boolean notifyCommit(FreeStyleProject project, ObjectId commitId) throws Exception {
        final int initialBuildNumber = project.getLastBuild().getNumber();
        final String commit1 = ObjectId.toString(commitId);

        final int port = server.getConnectors()[0].getLocalPort();
        if (port < 0) {
            throw new IllegalStateException("Could not locate Jetty server port");
        }
        final String notificationPath = "http://localhost:" + Integer.toString(port)
                + "/git/notifyCommit?url=" + testRepo.gitDir.toString() + "&sha1=" + commit1;
        final URL notifyUrl = new URL(notificationPath);
        final InputStream is = notifyUrl.openStream();
        IOUtils.toString(is);
        IOUtils.closeQuietly(is);

        if ((project.getLastBuild().getNumber() == initialBuildNumber)
                && (jenkins.getQueue().isEmpty())) {
            return false;
        } else {
            while (!jenkins.getQueue().isEmpty()) {
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
        jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(new JGitTool(Collections.<ToolProperty<?>>emptyList()));
    }
}
