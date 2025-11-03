/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.plugins.git.GitPublisher.BranchToPush;
import hudson.plugins.git.GitPublisher.NoteToPush;
import hudson.plugins.git.GitPublisher.TagToPush;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.scm.NullSCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.StreamTaskListener;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.SystemReader;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.gitclient.UnsupportedCommand;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jenkins.model.Jenkins;
import jenkins.plugins.git.CliGitCommand;
import jenkins.plugins.git.GitToolChooser;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.GitClient;

import static hudson.Functions.isWindows;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GitPublisher}
 *
 * @author Kohsuke Kawaguchi
 */
class GitPublisherTest extends AbstractGitProject {

    @TempDir
    private File tmpFolder;

    @BeforeAll
    static void beforeAll() throws Exception {
        SystemReader.getInstance().getUserConfig().clear();
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
    }

    @Issue("JENKINS-5005")
    @Test
    void testMatrixBuild() throws Exception {
        final AtomicInteger run = new AtomicInteger(); // count the number of times the perform is called

        commitNewFile("a");

        MatrixProject mp = r.createProject(MatrixProject.class, "xyz");
        mp.setAxes(new AxisList(new Axis("VAR","a","b")));
        mp.setScm(new GitSCM(testGitDir.getAbsolutePath()));
        mp.getPublishersList().add(new TestGitPublisher(
                Collections.singletonList(new TagToPush("origin","foo","message",true, false)),
                Collections.emptyList(),
                Collections.emptyList(),
                true, true, false) {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                run.incrementAndGet();
                try {
                    return super.perform(build, launcher, listener);
                } finally {
                    // until the 3rd one (which is the last one), we shouldn't create a tag
                    if (run.get() < 3) {
                        try {
                            assertFalse(existsTag("foo"));
                        } catch (Exception x) {
                            throw new AssertionError(x);
                        }
                    }
                }
            }

            @Override
            public BuildStepDescriptor getDescriptor() {
                return (BuildStepDescriptor)Jenkins.get().getDescriptorOrDie(GitPublisher.class); // fake
            }

            @Serial
            private Object writeReplace() { return new NullSCM(); }
        });

        MatrixBuild b = r.buildAndAssertSuccess(mp);

        assertTrue(existsTag("foo"));

        assertTrue(containsTagMessage("foo", "message"));

        // twice for MatrixRun, which is to be ignored, then once for matrix completion
        assertEquals(3,run.get());
    }

    @Test
    void GitPublisherFreestylePushBranchWithJGit() throws Exception {
        GitTool tool = new JGitTool(Collections.emptyList());
        r.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);

        FreeStyleProject project = setupSimpleProject("master");

        // Store a cache size for the repository so that git tool chooser will choose JGit
        Random random = new Random();
        GitToolChooser.putRepositorySizeCache(testGitDir.getAbsolutePath(), 37 + random.nextInt(900));

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new TestGitPublisher(
                Collections.emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commitNewFile("commitFileBase");
        testGitClient.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testGitClient.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commitNewFile(commitFile1);
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        /* Confirm that JGit was used and that the branch push message was logged */
        assertThat(build1.getLog(50),
                   hasItems("The recommended git tool is: jgit", // JGit recommended by git tool chooser
                            "Pushing HEAD to branch integration at repo origin"));
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        /* Confirm the branch was pushed */
        String sha1 = getHeadRevision(build1, "integration");
        assertEquals(sha1, testGitClient.revParse(Constants.HEAD).name());
    }

    @Test
    void GitPublisherFailWithJGit() throws Exception {
        final AtomicInteger run = new AtomicInteger(); // count the number of times the perform is called

        commitNewFile("a");

        List<UserRemoteConfig> repoList = new ArrayList<>();
        repoList.add(new UserRemoteConfig(testGitDir.getAbsolutePath(), null, null, null));

        GitTool tool = new JGitTool(Collections.emptyList()); //testGitDir.getAbsolutePath()
        r.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);

        MatrixProject mp = r.createProject(MatrixProject.class, "xyz");
        mp.setAxes(new AxisList(new Axis("VAR","a","b")));
        mp.setScm(new GitSCM(repoList,
                Collections.singletonList(new BranchSpec("")),
                null, tool.getName(), Collections.emptyList()));
        mp.getPublishersList().add(new TestGitPublisher(
                Collections.singletonList(new TagToPush("origin","foo","message",true, false)),
                Collections.emptyList(),
                Collections.emptyList(),
                true, true, false) {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                run.incrementAndGet();
                try {
                    return super.perform(build, launcher, listener);
                } finally {
                    // until the 3rd one (which is the last one), we shouldn't create a tag
                    if (run.get() < 3) {
                        try {
                            assertFalse(existsTag("foo"));
                        } catch (Exception x) {
                            throw new AssertionError(x);
                        }
                    }
                }
            }

            @Override
            public BuildStepDescriptor getDescriptor() {
                return (BuildStepDescriptor)Jenkins.get().getDescriptorOrDie(GitPublisher.class); // fake
            }

            @Serial
            private Object writeReplace() { return new NullSCM(); }
        });

        MatrixBuild b = r.buildAndAssertSuccess(mp);

        /* I don't understand why the log reports pushing tag to repo origin but the tag is not pushed */
        assertThat(b.getLog(50),
                   hasItems("remote: Counting objects",
                            "Pushing tag foo to repo origin"));
        /* JGit implementation includes PushCommand, but it fails to push the tag */
        assertFalse(existsTag("foo"));
    }

    @Test
    void testMergeAndPush() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new TestGitPublisher(
                Collections.emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commitNewFile("commitFileBase");
        testGitClient.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testGitClient.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commitNewFile(commitFile1);
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        String sha1 = getHeadRevision(build1, "integration");
        assertEquals(sha1, testGitClient.revParse(Constants.HEAD).name());

    }

    @Issue("JENKINS-12402")
    @Test
    void testMergeAndPushFF() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, MergeCommand.GitPluginFastForwardMode.FF)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new TestGitPublisher(
                Collections.emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commitNewFile("commitFileBase");
        testGitClient.branch("integration");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, "commitFileBase");
        assertTrue(build1.getWorkspace().child("commitFileBase").exists());
        String shaIntegration = getHeadRevision(build1, "integration");
        assertEquals(shaIntegration, testGitClient.revParse(Constants.HEAD).name(), "the integration branch should be at HEAD");

        // create a new branch and build, this results in a fast-forward merge
        testGitClient.checkout("master");
        ObjectId master = testGitClient.revParse("HEAD");
        testGitClient.branch("branch1");
        testGitClient.checkout("branch1");
        final String commitFile1 = "commitFile1";
        commitNewFile(commitFile1);
        String shaBranch1 = testGitClient.revParse("branch1").name();
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile1);

        // Test that the build (including publish) performed as expected.
        //   - commitFile1 is in the workspace
        //   - HEAD and integration should line up with branch1 like so:
        //     * f4d190c (HEAD, integration, branch1) Commit number 1
        //     * f787536 (master) Initial Commit
        //
        assertTrue(build2.getWorkspace().child("commitFile1").exists());
        shaIntegration = getHeadRevision(build2, "integration");
        String shaHead = testGitClient.revParse(Constants.HEAD).name();
        assertEquals(shaIntegration,shaBranch1, "the integration branch and branch1 should line up");
        assertEquals(shaIntegration,shaHead, "the integration branch should be at HEAD");
        // integration should have master as the parent commit
        List<ObjectId> revList = testGitClient.revList("integration^1");
        ObjectId integrationParent = revList.get(0);
        assertEquals(master,integrationParent,"Fast-forward merge should have had master as a parent");

        // create a second branch off of master, so as to force a merge commit and to test
        // that --ff gracefully falls back to a merge commit
        testGitClient.checkout("master");
        testGitClient.branch("branch2");
        testGitClient.checkout("branch2");
        final String commitFile2 = "commitFile2";
        commitNewFile(commitFile2);
        String shaBranch2 = testGitClient.revParse("branch2").name();
        final FreeStyleBuild build3 = build(project, Result.SUCCESS, commitFile2);

        // Test that the build (including publish) performed as expected
        //   - commitFile1 is in the workspace
        //   - commitFile2 is in the workspace
        //   - the integration branch has branch1 and branch2 as parents, like so:
        //   *   f9b37d8 (integration) Merge commit '96a11fd...' into integration
        //   |\
        //   | * 96a11fd (HEAD, branch2) Commit number 2
        //   * | f4d190c (branch1) Commit number 1
        //   |/
        //   * f787536 (master) Initial Commit
        //
        assertTrue(build1.getWorkspace().child(commitFile1).exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        // the integration branch should have branch1 and branch2 as parents
        revList = testGitClient.revList("integration^1");
        assertEquals(revList.get(0).name(),shaBranch1,"Integration should have branch1 as a parent");
        revList = testGitClient.revList("integration^2");
        assertEquals(revList.get(0).name(),shaBranch2,"Integration should have branch2 as a parent");
    }

    @Issue("JENKINS-12402")
    @Test
    void testMergeAndPushNoFF() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, MergeCommand.GitPluginFastForwardMode.NO_FF)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new TestGitPublisher(
                Collections.emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commitNewFile("commitFileBase");
        testGitClient.branch("integration");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, "commitFileBase");
        assertTrue(build1.getWorkspace().child("commitFileBase").exists());
        String shaIntegration = getHeadRevision(build1, "integration");
        assertEquals(shaIntegration, testGitClient.revParse(Constants.HEAD).name(), "integration branch should be at HEAD");

        // create a new branch and build
        // This would be a fast-forward merge, but we're calling for --no-ff and that should work
        testGitClient.checkout("master");
        ObjectId master = testGitClient.revParse("HEAD");
        testGitClient.branch("branch1");
        testGitClient.checkout("branch1");
        final String commitFile1 = "commitFile1";
        commitNewFile(commitFile1);
        String shaBranch1 = testGitClient.revParse("branch1").name();
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile1);
        ObjectId mergeCommit = testGitClient.revParse("integration");

        // Test that the build and publish performed as expected.
        //   - commitFile1 is in the workspace
        //   - integration has branch1 and master as parents, like so:
        //     *   6913e57 (integration) Merge commit '257e33c...' into integration
        //     |\
        //     | * 257e33c (HEAD, branch1) Commit number 1
        //     |/
        //     * 3066c87 (master) Initial Commit
        //
        assertTrue(build2.getWorkspace().child("commitFile1").exists());
        List<ObjectId> revList = testGitClient.revList("integration^1");
        assertEquals(revList.get(0),master,"Integration should have master as a parent");
        revList = testGitClient.revList("integration^2");
        assertEquals(revList.get(0).name(),shaBranch1,"Integration should have branch1 as a parent");

        // create a second branch off of master, so as to test that --no-ff is published as expected
        testGitClient.checkout("master");
        testGitClient.branch("branch2");
        testGitClient.checkout("branch2");
        final String commitFile2 = "commitFile2";
        commitNewFile(commitFile2);
        String shaBranch2 = testGitClient.revParse("branch2").name();
        final FreeStyleBuild build3 = build(project, Result.SUCCESS, commitFile2);

        // Test that the build performed as expected
        //   - commitFile1 is in the workspace
        //   - commitFile2 is in the workspace
        //   - the integration branch has branch1 and the previous merge commit as parents, like so:
        //     *   5908447 (integration) Merge commit '157fd0b...' into integration
        //     |\
        //     | * 157fd0b (HEAD, branch2) Commit number 2
        //     * |   7afa661 Merge commit '0a37dd6...' into integration
        //     |\ \
        //     | |/
        //     |/|
        //     | * 0a37dd6 (branch1) Commit number 1
        //     |/
        //     * a5dda1a (master) Initial Commit
        //
        assertTrue(build1.getWorkspace().child(commitFile1).exists(),"commitFile1 should exist in the workspace");
        assertTrue(build1.getWorkspace().child(commitFile2).exists(),"commitFile2 should exist in the workspace");
        // the integration branch should have branch1 and branch2 as parents
        revList = testGitClient.revList("integration^1");
        assertEquals(revList.get(0),mergeCommit,"Integration should have the first merge commit as a parent");
        revList = testGitClient.revList("integration^2");
        assertEquals(revList.get(0).name(),shaBranch2,"Integration should have branch2 as a parent");
    }

    @Issue("JENKINS-12402")
    @Test
    void testMergeAndPushFFOnly() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, MergeCommand.GitPluginFastForwardMode.FF_ONLY)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new TestGitPublisher(
                Collections.emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commitNewFile("commitFileBase");
        testGitClient.branch("integration");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, "commitFileBase");
        assertTrue(build1.getWorkspace().child("commitFileBase").exists());
        String shaIntegration = getHeadRevision(build1, "integration");
        assertEquals(shaIntegration, testGitClient.revParse(Constants.HEAD).name(), "integration should be at HEAD");

        // create a new branch and build
        // This merge can work with --ff-only
        testGitClient.checkout("master");
        ObjectId master = testGitClient.revParse("HEAD");
        testGitClient.branch("branch1");
        testGitClient.checkout("branch1");
        final String commitFile1 = "commitFile1";
        commitNewFile(commitFile1);
        String shaBranch1 = testGitClient.revParse("branch1").name();
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile1);
        ObjectId mergeCommit = testGitClient.revParse("integration");

        // Test that the build (including publish) performed as expected.
        //   - commitFile1 is in the workspace
        //   - HEAD and integration should line up with branch1 like so:
        //     * f4d190c (HEAD, integration, branch1) Commit number 1
        //     * f787536 (master) Initial Commit
        //
        assertTrue(build2.getWorkspace().child("commitFile1").exists(),"commitFile1 should exist in the workspace");
        shaIntegration = getHeadRevision(build2, "integration");
        String shaHead = testGitClient.revParse(Constants.HEAD).name();
        assertEquals(shaIntegration,shaBranch1, "integration and branch1 should line up");
        assertEquals(shaIntegration,shaHead, "integration and head should line up");
        // integration should have master as the parent commit
        List<ObjectId> revList = testGitClient.revList("integration^1");
        ObjectId integrationParent = revList.get(0);
        assertEquals(master,integrationParent,"Fast-forward merge should have had master as a parent");

        // create a second branch off of master, so as to force a merge commit
        // but the publish will fail as --ff-only cannot work with a parallel branch
        testGitClient.checkout("master");
        testGitClient.branch("branch2");
        testGitClient.checkout("branch2");
        final String commitFile2 = "commitFile2";
        commitNewFile(commitFile2);
        String shaBranch2 = testGitClient.revParse("branch2").name();
        final FreeStyleBuild build3 = build(project, Result.FAILURE, commitFile2);

        // Test that the publish did not merge the branches
        //   - The workspace will contain commitFile2, but not branch1's file (commitFile1)
        //   - The repository will be left with branch2 unmerged like so:
        //     * c19a55d (HEAD, branch2) Commit number 2
        //     | * 79c49b2 (integration, branch1) Commit number 1
        //     |/
        //     * ebffeb3 (master) Initial Commit
        assertFalse(build2.getWorkspace().child("commitFile1").exists(),"commitFile1 should not exist in the workspace");
        assertTrue(build2.getWorkspace().child("commitFile2").exists(),"commitFile2 should exist in the workspace");
        revList = testGitClient.revList("branch2^1");
        assertEquals(revList.get(0),master,"branch2 should have master as a parent");
        assertThrows(NullPointerException.class, () -> testGitClient.revList("branch2^2"), "branch2 should have no other parent than master");
    }

    @Issue("JENKINS-24786")
    @Test
    void testPushEnvVarsInRemoteConfig() throws Exception{
    	FreeStyleProject project = setupSimpleProject("master");

        // create second (bare) test repository as target
        TaskListener listener = StreamTaskListener.fromStderr();
        TestGitRepo testTargetRepo = new TestGitRepo("target", newFolder(tmpFolder, "push_env_vars"), listener);
    	testTargetRepo.git.init_().workspace(testTargetRepo.gitDir.getAbsolutePath()).bare(true).execute();
        testTargetRepo.commit("lostTargetFile", new PersonIdent("John Doe", "john@example.com"), "Initial Target Commit");

        // add second test repository as remote repository with environment variables
        List<UserRemoteConfig> remoteRepositories = remoteConfigs();
    	remoteRepositories.add(new UserRemoteConfig("$TARGET_URL", "$TARGET_NAME", "+refs/heads/$TARGET_BRANCH:refs/remotes/$TARGET_NAME/$TARGET_BRANCH", null));

        GitSCM scm = new GitSCM(
                remoteRepositories,
                Collections.singletonList(new BranchSpec("origin/master")),
                null, null,
                Collections.emptyList());
        project.setScm(scm);

        // add parameters for remote repository configuration
        project.addProperty(new ParametersDefinitionProperty(
        		new StringParameterDefinition("TARGET_URL", testTargetRepo.gitDir.getAbsolutePath()),
        		new StringParameterDefinition("TARGET_NAME", "target"),
        		new StringParameterDefinition("TARGET_BRANCH", "master")));

        String tag_name = "test-tag";
        String note_content = "Test Note";

        project.getPublishersList().add(new TestGitPublisher(
        		Collections.singletonList(new TagToPush("$TARGET_NAME", tag_name, "", false, false)),
                Collections.singletonList(new BranchToPush("$TARGET_NAME", "$TARGET_BRANCH")),
                Collections.singletonList(new NoteToPush("$TARGET_NAME", note_content, Constants.R_NOTES_COMMITS, false)),
                true, false, true));

        commitNewFile("commitFile");
        testGitClient.tag(tag_name, "Comment");
        ObjectId expectedCommit = testGitClient.revParse("master");

        build(project, Result.SUCCESS, "commitFile");

        // check if everything reached target repository
        assertEquals(expectedCommit, testTargetRepo.git.revParse("master"));
        assertTrue(existsTagInRepo(testTargetRepo.git, tag_name));

    }

    @Issue("JENKINS-24082")
    @Test
    void testForcePush() throws Exception {
    	FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("master")),
                null, null,
                Collections.emptyList());
        project.setScm(scm);

        GitPublisher forcedPublisher = new TestGitPublisher(
                Collections.emptyList(),
                Collections.singletonList(new BranchToPush("origin", "otherbranch")),
                Collections.emptyList(),
                true, true, true);
        project.getPublishersList().add(forcedPublisher);

        // Create a commit on the master branch in the test repo
        commitNewFile("commitFile");
        ObjectId masterCommit1 = testGitClient.revParse("master");

        // Checkout and commit to "otherbranch" in the test repo
        testGitClient.branch("otherbranch");
        testGitClient.checkout("otherbranch");
        commitNewFile("otherCommitFile");
        ObjectId otherCommit = testGitClient.revParse("otherbranch");

        testGitClient.checkout("master");
        commitNewFile("commitFile2");
        ObjectId masterCommit2 = testGitClient.revParse("master");

        // masterCommit1 parent of both masterCommit2 and otherCommit
        assertEquals(masterCommit1, testGitClient.revParse("master^"));
        assertEquals(masterCommit1, testGitClient.revParse("otherbranch^"));

        // Confirm that otherbranch still points to otherCommit
        // build will merge and push to "otherbranch" in test repo
        // Without force, this would fail
        assertEquals(otherCommit, testGitClient.revParse("otherbranch")); // not merged yet
        assertTrue(testGitClient.revList("otherbranch").contains(otherCommit), "otherCommit not in otherbranch");
        build(project, Result.SUCCESS, "commitFile2");
        assertEquals(masterCommit2, testGitClient.revParse("otherbranch")); // merge done
        assertFalse(testGitClient.revList("otherbranch").contains(otherCommit), "otherCommit in otherbranch");

        // Commit to otherbranch in test repo so that next merge will fail
        testGitClient.checkout("otherbranch");
        commitNewFile("otherCommitFile2");
        ObjectId otherCommit2 = testGitClient.revParse("otherbranch");
        assertNotEquals(masterCommit2, otherCommit2);

        // Commit to master branch in test repo
        testGitClient.checkout("master");
        commitNewFile("commitFile3");
        ObjectId masterCommit3 = testGitClient.revParse("master");

        // Remove forcedPublisher, add unforcedPublisher
        project.getPublishersList().remove(forcedPublisher);
        GitPublisher unforcedPublisher = new TestGitPublisher(
                Collections.emptyList(),
                Collections.singletonList(new BranchToPush("origin", "otherbranch")),
                Collections.emptyList(),
                true, true, false);
        project.getPublishersList().add(unforcedPublisher);

        // build will attempts to merge and push to "otherbranch" in test repo.
        // Without force, will fail
        assertEquals(otherCommit2, testGitClient.revParse("otherbranch")); // not merged yet
        assertTrue(testGitClient.revList("otherbranch").contains(otherCommit2), "otherCommit2 not in otherbranch");
        build(project, Result.FAILURE, "commitFile3");
        assertEquals(otherCommit2, testGitClient.revParse("otherbranch")); // still not merged
        assertTrue(testGitClient.revList("otherbranch").contains(otherCommit2), "otherCommit2 not in otherbranch");

        // Remove unforcedPublisher, add forcedPublisher
        project.getPublishersList().remove(unforcedPublisher);
        project.getPublishersList().add(forcedPublisher);

        // Commit to master branch in test repo
        testGitClient.checkout("master");
        commitNewFile("commitFile4");
        ObjectId masterCommit4 = testGitClient.revParse("master");

        // build will merge and push to "otherbranch" in test repo.
        assertEquals(otherCommit2, testGitClient.revParse("otherbranch"));
        assertTrue(testGitClient.isCommitInRepo(otherCommit2), "otherCommit2 not in test repo");
        assertTrue(testGitClient.revList("otherbranch").contains(otherCommit2), "otherCommit2 not in otherbranch");
        build(project, Result.SUCCESS, "commitFile4");
        assertEquals(masterCommit4, testGitClient.revParse("otherbranch"));
        assertEquals(masterCommit3, testGitClient.revParse("otherbranch^"));
        assertFalse(testGitClient.revList("otherbranch").contains(otherCommit2), "otherCommit2 in otherbranch");
    }

    /* Fix push to remote when skipTag is enabled */
    @Issue("JENKINS-17769")
    @Test
    void testMergeAndPushWithSkipTagEnabled() throws Exception {
      FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                null, null, new ArrayList<>());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

      project.getPublishersList().add(new TestGitPublisher(
          Collections.emptyList(),
          Collections.singletonList(new BranchToPush("origin", "integration")),
          Collections.emptyList(),
          true, true, false));

      // create initial commit and then run the build against it:
      commitNewFile("commitFileBase");
      testGitClient.branch("integration");
      build(project, Result.SUCCESS, "commitFileBase");

      testGitClient.checkout(null, "topic1");
      final String commitFile1 = "commitFile1";
      commitNewFile(commitFile1);
      final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
      assertTrue(build1.getWorkspace().child(commitFile1).exists());

      String sha1 = getHeadRevision(build1, "integration");
      assertEquals(sha1, testGitClient.revParse(Constants.HEAD).name());
    }

    @Test
    void testRebaseBeforePush() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("master")),
                null, null,
                Collections.emptyList());
        project.setScm(scm);

        BranchToPush btp = new BranchToPush("origin", "master");
        btp.setRebaseBeforePush(true);

        GitPublisher rebasedPublisher = new TestGitPublisher(
                Collections.emptyList(),
                Collections.singletonList(btp),
                Collections.emptyList(),
                true, true, true);
        project.getPublishersList().add(rebasedPublisher);

        project.getBuildersList().add(new LongRunningCommit(testGitDir));
        project.save();

        // Assume during our build someone else pushed changes (commitFile1) to the remote repo.
        // So our own changes (commitFile2) cannot be pushed back to the remote origin.
        //
        // * 0eb2599 (HEAD) Added a file named commitFile2
        // | * 64e71e7 (origin/master) Added a file named commitFile1
        // |/
        // * b2578eb init
        //
        // What we can do is to fetch the remote changes and rebase our own changes:
        //
        // * 0e7674c (HEAD) Added a file named commitFile2
        // * 64e71e7 (origin/master) Added a file named commitFile1
        // * b2578eb init


        // as we have set "rebaseBeforePush" to true we expect all files to be present after the build.
        FreeStyleBuild build = build(project, Result.SUCCESS, "commitFile1", "commitFile2");
    }

    @Issue("JENKINS-24786")
    @Test
    void testMergeAndPushWithCharacteristicEnvVar() throws Exception {
        // jgit doesn't work because of missing PerBuildTag
        //GitTool tool = new JGitTool(Collections.emptyList());
        //r.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);
        FreeStyleProject project = setupSimpleProject("master");

        /*
         * JOB_NAME seemed like the more obvious choice, but when run from a
         * multi-configuration job, the value of JOB_NAME includes an equals
         * sign.  That makes log parsing and general use of the variable more
         * difficult.  JENKINS_SERVER_COOKIE is a characteristic env var which
         * probably never includes an equals sign.
         */
        String envName = "JENKINS_SERVER_COOKIE";
        String envValue = project.getCharacteristicEnvVars().get(envName, "NOT-SET");
        assertNotEquals("NOT-SET", envValue, "Env " + envName + " not set");

        checkEnvVar(project, envName, envValue);
    }

    @Issue("JENKINS-24786")
    @Test
    void testMergeAndPushWithSystemEnvVar() throws Exception {
        String envName = isWindows() ? "COMPUTERNAME" : "LOGNAME";
        String envValue = System.getenv().get(envName);
        if (envValue == null || envValue.isEmpty()) {
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }

        FreeStyleProject project = setupSimpleProject("master");

        assertNotNull(envValue, "Env " + envName + " not set");
        assertFalse(envValue.isEmpty(), "Env " + envName + " empty");

        checkEnvVar(project, envName, envValue);
    }

    private void checkEnvVar(FreeStyleProject project, String envName, String envValue) throws Exception {

        String envReference = "${" + envName + "}";

        List<GitSCMExtension> scmExtensions = new ArrayList<>();
        scmExtensions.add(new PreBuildMerge(new UserMergeOptions("origin", envReference, null, null)));
        scmExtensions.add(new LocalBranch(envReference));
        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                null, null, scmExtensions);
        project.setScm(scm);

        String tagNameReference = envReference + "-tag"; // ${BRANCH_NAME}-tag
        String tagNameValue = envValue + "-tag";         // master-tag
        String tagMessageReference = envReference + " tag message";
        String noteReference = "note for " + envReference;
        String noteValue = "note for " + envValue;
        GitPublisher publisher = new TestGitPublisher(
                Collections.singletonList(new TagToPush("origin", tagNameReference, tagMessageReference, false, true)),
                Collections.singletonList(new BranchToPush("origin", envReference)),
                Collections.singletonList(new NoteToPush("origin", noteReference, Constants.R_NOTES_COMMITS, false)),
                true, true, true);
        assertTrue(publisher.isForcePush());
        assertTrue(publisher.isPushBranches());
        assertTrue(publisher.isPushMerge());
        assertTrue(publisher.isPushNotes());
        assertTrue(publisher.isPushOnlyIfSuccess());
        assertTrue(publisher.isPushTags());
        project.getPublishersList().add(publisher);

        // create initial commit
        commitNewFile("commitFileBase");
        ObjectId initialCommit = testGitClient.getHeadRev(testGitDir.getAbsolutePath(), "master");
        assertTrue(testGitClient.isCommitInRepo(initialCommit));

        // Create branch in the test repo (pulled into the project workspace at build)
        assertFalse(hasBranch(envValue), "Test repo has " + envValue + " branch");
        testGitClient.branch(envValue);
        assertTrue(hasBranch(envValue), "Test repo missing " + envValue + " branch");
        assertFalse(testGitClient.tagExists(tagNameValue), tagNameValue + " in " + testGitClient);

        // Build the branch
        final FreeStyleBuild build0 = build(project, Result.SUCCESS, "commitFileBase");

        String build0HeadBranch = getHeadRevision(build0, envValue);
        assertEquals(build0HeadBranch, initialCommit.getName());
        assertTrue(testGitClient.tagExists(tagNameValue), tagNameValue + " not in " + testGitClient);
        assertTrue(build0.getWorkspace().child(".git/refs/tags/" + tagNameValue).exists(), tagNameValue + " not in build");

        // Create a topic branch in the source repository and commit to topic branch
        String topicBranch = envValue + "-topic1";
        assertFalse(hasBranch(topicBranch), "Test repo has " + topicBranch + " branch");
        testGitClient.checkout(null, topicBranch);
        assertTrue(hasBranch(topicBranch), "Test repo has no " + topicBranch + " branch");
        final String commitFile1 = "commitFile1";
        commitNewFile(commitFile1);
        ObjectId topicCommit = testGitClient.getHeadRev(testGitDir.getAbsolutePath(), topicBranch);
        assertTrue(testGitClient.isCommitInRepo(topicCommit));

        // Run a build, should be on the topic branch, tagged, and noted
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        FilePath myWorkspace = build1.getWorkspace();
        assertTrue(myWorkspace.child(commitFile1).exists());
        assertTrue(myWorkspace.child(".git/refs/tags/" + tagNameValue).exists(), "Tag " + tagNameValue + " not in build");

        String build1Head = getHeadRevision(build1, envValue);
        assertEquals(build1Head, testGitClient.revParse(Constants.HEAD).name());
        assertEquals(topicCommit.getName(), build1Head, "Wrong head commit in build1");

    }

    /**
     * doing this because some system and/or users may commit/tag gpgSign activated,
     * and we cannot answer the passphrase if needed so disabled it locally for the test
     */
    private static class TestGitPublisher extends GitPublisher {
        public TestGitPublisher(
                List<TagToPush> tagsToPush,
                List<BranchToPush> branchesToPush,
                List<NoteToPush> notesToPush,
                boolean pushOnlyIfSuccess,
                boolean pushMerge,
                boolean forcePush) {
            super(tagsToPush, branchesToPush, notesToPush, pushOnlyIfSuccess, pushMerge, forcePush);
        }

        @Override
        protected GitClient getGitClient(
                GitSCM gitSCM,
                BuildListener listener,
                EnvVars environment,
                AbstractBuild<?, ?> build,
                UnsupportedCommand cmd)
                throws IOException, InterruptedException {
            try {
                GitClient gitClient = super.getGitClient(gitSCM, listener, environment, build, cmd);
                gitClient.config(GitClient.ConfigLevel.LOCAL, "commit.gpgsign", "false");
                gitClient.config(GitClient.ConfigLevel.LOCAL, "tag.gpgSign", "false");
                return gitClient;
            } catch (GitException x) {
                throw new IOException(x);
            }
        }
    }

    private boolean existsTag(String tag) throws Exception {
        return existsTagInRepo(testGitClient, tag);
    }

    private boolean existsTagInRepo(GitClient gitClient, String tag) throws Exception {
        Set<String> tags = gitClient.getTagNames("*");
        return tags.contains(tag);
    }

    private boolean containsTagMessage(String tag, String str) throws Exception {
        String msg = testGitClient.getTagMessage(tag);
        return msg.contains(str);
    }

    private boolean hasBranch(String branchName) throws GitException, InterruptedException {
        Set<Branch> testRepoBranches = testGitClient.getBranches();
        for (Branch branch : testRepoBranches) {
            if (branch.getName().equals(branchName)) {
                return true;
            }
        }
        return false;
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}

class LongRunningCommit extends Builder {

    private File remoteGitDir;

    LongRunningCommit(File remoteGitDir) {
        this.remoteGitDir = remoteGitDir;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        try {
            return _perform(build, launcher, listener);
        } catch (Exception x) {
            throw new IOException(x);
        }
    }

    private boolean _perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception {
        TestGitRepo workspaceGit = new TestGitRepo("workspace", new File(build.getWorkspace().getRemote()), listener);
        TestGitRepo remoteGit = new TestGitRepo("remote", this.remoteGitDir, listener);

        // simulate an external commit and push to the remote during the build of our project.
        ObjectId headRev = remoteGit.git.revParse("HEAD");
        remoteGit.commit("commitFile1", remoteGit.johnDoe, "Added a file commitFile1");
        remoteGit.git.checkout(headRev.getName()); // allow to push to this repo later

        // commit onto the initial commit (creates a head with our changes later).
        workspaceGit.commit("commitFile2", remoteGit.johnDoe, "Added a file commitFile2");

        return true;
    }
}
