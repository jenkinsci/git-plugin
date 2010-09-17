package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.util.StreamTaskListener;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.transport.RemoteConfig;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link GitSCM}.
 * @author ishaaq
 */
public class GitSCMTest extends HudsonTestCase {

    private File workDir;
    private GitAPI git;
    private TaskListener listener;
    private EnvVars envVars;
    private FilePath workspace;

    private final PersonIdent johnDoe = new PersonIdent("John Doe", "john@doe.com");
    private final PersonIdent janeDoe = new PersonIdent("Jane Doe", "jane@doe.com");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        workDir = createTmpDir();
        listener = new StreamTaskListener();
        envVars = new EnvVars();
        setAuthor(johnDoe);
        setCommitter(johnDoe);
        workspace = new FilePath(workDir);
        git = new GitAPI("git", workspace, listener, envVars);
        git.init();
    }

    private void setAuthor(final PersonIdent author) {
        envVars.put("GIT_AUTHOR_NAME", author.getName());
        envVars.put("GIT_AUTHOR_EMAIL", author.getEmailAddress());
    }

    private void setCommitter(final PersonIdent committer) {
        envVars.put("GIT_COMMITTER_NAME", committer.getName());
        envVars.put("GIT_COMMITTER_EMAIL", committer.getEmailAddress());
    }

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

    public void testBasicExcludedRegion() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, ".*2", null);

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
        assertEquals("", johnDoe.getName(), ((User)culprits.toArray()[0]).getFullName());
        assertEquals("", janeDoe.getName(), ((User)culprits.toArray()[1]).getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.pollSCMChanges(listener));
    }

    public void testBasicExcludedUser() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, "Jane Doe");

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
        assertEquals("", johnDoe.getName(), ((User)culprits.toArray()[0]).getFullName());
        assertEquals("", janeDoe.getName(), ((User)culprits.toArray()[1]).getFullName());
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
        project.setAssignedLabel(createSlave(null, null).getSelfLabel());

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

    /**
     * A previous version of GitSCM would only build against branches, not tags. This test checks that that
     * regression has been fixed.
     */
    public void testGitSCMCanBuildAgainstTags() throws Exception {
        final String mytag = "mytag";
        FreeStyleProject project = setupSimpleProject(mytag);
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");

        //now create and checkout a new branch:
        final String tmpBranch = "tmp";
        git.branch(tmpBranch);
        git.checkout(tmpBranch);
        // commit to it
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertFalse("scm polling should not detect any more changes since mytag is untouched right now", project.pollSCMChanges(listener));
        build(project, Result.FAILURE, commitFile2);

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
        project.setScm(new GitSCM(
                createRemoteRepositories(relativeTargetDir),
                Collections.singletonList(new BranchSpec(branchString)),
                new PreBuildMergeOptions(), false, Collections.<SubmoduleConfig>emptyList(), false,
                false, new DefaultBuildChooser(), null, null, authorOrCommitter, relativeTargetDir,
                excludedRegions, excludedUsers, localBranch, false));
        project.getBuildersList().add(new CaptureEnvironmentBuilder());
        return project;
    }

    private FreeStyleProject setupSimpleProject(String branchString) throws Exception {
        return setupProject(branchString,false);
    }

    private List<RemoteConfig> createRemoteRepositories(String relativeTargetDir) throws IOException {
        return GitSCM.DescriptorImpl.createRepositoryConfigurations(
                                                                    new String[]{workDir.getAbsolutePath()},
                                                                    new String[]{"origin"},
                                                                    new String[]{""},
                                                                    File.createTempFile("tmp", "config", hudson.getRootDir())
        );
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

    private void commit(final String fileName, final PersonIdent committer, final String message) throws GitException {
        setAuthor(committer);
        setCommitter(committer);
        FilePath file = workspace.child(fileName);
        try {
            file.write(fileName, null);
        } catch (Exception e) {
            throw new GitException("unable to write file", e);
        }

        git.add(fileName);
        git.launchCommand("commit", "-m", message);
    }

    private void commit(final String fileName, final PersonIdent author, final PersonIdent committer,
                        final String message) throws GitException {
        setAuthor(author);
        setCommitter(committer);
        FilePath file = workspace.child(fileName);
        try {
            file.write(fileName, null);
        } catch (Exception e) {
            throw new GitException("unable to write file", e);
        }
        git.add(fileName);
        git.launchCommand("commit", "-m", message);
    }

    private EnvVars getEnvVars(FreeStyleProject project) {
        for (hudson.tasks.Builder b : project.getBuilders()) {
            if (b instanceof CaptureEnvironmentBuilder) {
                return ((CaptureEnvironmentBuilder)b).getEnvVars();
            }
        }
        return new EnvVars();
    }
}
