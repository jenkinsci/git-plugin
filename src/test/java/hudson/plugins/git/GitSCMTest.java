package hudson.plugins.git;

import hudson.BulkChange;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.*;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.transport.RemoteConfig;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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

    public void testBasicExcludedRegion() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, ".*2", null);

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
        assertEquals("", johnDoe.getName(), ((User)culprits.toArray()[0]).getFullName());
        assertEquals("", janeDoe.getName(), ((User)culprits.toArray()[1]).getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    public void testBasicExcludedUser() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, "Jane Doe");

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
        assertEquals("", johnDoe.getName(), ((User)culprits.toArray()[0]).getFullName());
        assertEquals("", janeDoe.getName(), ((User)culprits.toArray()[1]).getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

    }

    public void testBasicInSubdir() throws Exception {
        FreeStyleProject project = setupProject("master", false, "subdir");

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
        project.setAssignedLabel(createSlave(null, null).getSelfLabel());

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
        
        project.setAssignedLabel(createSlave(null, null).getSelfLabel());

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
        FreeStyleProject project = setupProject("master", false);

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
        FreeStyleProject project = setupProject("master", true);

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
        git.branch("untracked");
        git.checkout("untracked");
        //.. and commit to it:
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertFalse("scm polling should not detect commit2 change because it is not in the branch we are tracking.", project.poll(listener).hasChanges());
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
        Node s = createSlave(null,null);
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
        git.launchCommand("branch", "-D", tmpBranch);

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
        git.launchCommand("branch", "-D", tmpBranch);

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

    public void testMultipleModuleBuild() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.setScm(
                new GitSCM(
                    Arrays.asList(
                            new GitSCMModule(module1.createRemoteRepositories(), Collections.singletonList(new BranchSpec("master")), null, new PreBuildMergeOptions(), "module1"),
                            new GitSCMModule(module2.createRemoteRepositories(), Collections.singletonList(new BranchSpec("master")), null, new PreBuildMergeOptions(), "module2")
                    ),
                    false,
                    false, new DefaultBuildChooser(), null, null, false,
                    null, null, false, null, null, false
                )
        );
        project.getBuildersList().add(new CaptureEnvironmentBuilder());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        module1.commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "commitFile2";
        module2.commit(commitFile2, janeDoe, "Commit number 2");
        build(project, Result.SUCCESS, "module1/" + commitFile1, "module2/" + commitFile2);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile3 = "commitFile3";
        module2.commit(commitFile3, janeDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change for module2", project.poll(listener).hasChanges());

        build(project, Result.SUCCESS, "module2/" + commitFile3);

        final String commitFile4 = "commitFile4";
        module1.commit(commitFile4, janeDoe, "Commit number 4");
        assertTrue("scm polling did not detect commit4 change for module1", project.poll(listener).hasChanges());

        final FreeStyleBuild build2 = build(project, Result.SUCCESS, "module1/" + commitFile4);
        
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child("module1").child(commitFile1).exists());
        assertTrue(build2.getWorkspace().child("module2").child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child("module2").child(commitFile3).exists());
        assertTrue(build2.getWorkspace().child("module1").child(commitFile4).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

    }

    @LocalData
    public void testMigrate3() {
        FreeStyleProject job = hudson.getItemByFullName("old", FreeStyleProject.class);
        assertNotNull(job);
        assertTrue(job.getScm() instanceof GitSCM);
        GitSCM scm = (GitSCM) job.getScm();
        assertEquals(1, scm.getModules().size());
        GitSCMModule module = scm.getModules().get(0);
        assertEquals("file:///c:/temp/repo1", module.getRepositories().get(0).getURIs().get(0).toPrivateString());
        assertEquals("**", module.getBranches().get(0).getName());
    }

    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter) throws Exception {
        return setupProject(branchString, authorOrCommitter, null);
    }

    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
                                          String relativeTargetDir) throws Exception {
        return setupProject(branchString, authorOrCommitter, relativeTargetDir, null, null);
    }
    
    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
                                          String relativeTargetDir,
                                          String excludedRegions,
                                          String excludedUsers) throws Exception {
        return setupProject(branchString, authorOrCommitter, relativeTargetDir, excludedRegions, excludedUsers, null);
    }
    
    private FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
                                          String relativeTargetDir, String excludedRegions,
                                          String excludedUsers, String localBranch) throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.setScm(
                new GitSCM(
                    Arrays.asList(new GitSCMModule(module1.createRemoteRepositories(), Collections.singletonList(new BranchSpec(branchString)), localBranch, new PreBuildMergeOptions(), relativeTargetDir)),
                    false,
                    false, new DefaultBuildChooser(), null, null, authorOrCommitter,
                    excludedRegions, excludedUsers, false, null, null, false
                )
        );
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
