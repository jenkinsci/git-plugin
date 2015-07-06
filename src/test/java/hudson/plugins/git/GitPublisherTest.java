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

import hudson.FilePath;
import hudson.Functions;
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
import hudson.util.StreamTaskListener;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jvnet.hudson.test.Issue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.GitClient;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link GitPublisher}
 * 
 * @author Kohsuke Kawaguchi
 */
public class GitPublisherTest extends AbstractGitProject {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Issue("JENKINS-5005")
    @Test
    public void testMatrixBuild() throws Exception {
        final AtomicInteger run = new AtomicInteger(); // count the number of times the perform is called

        commitNewFile("a");

        MatrixProject mp = jenkins.createMatrixProject("xyz");
        mp.setAxes(new AxisList(new Axis("VAR","a","b")));
        mp.setScm(new GitSCM(testGitDir.getAbsolutePath()));
        mp.getPublishersList().add(new GitPublisher(
                Collections.singletonList(new TagToPush("origin","foo","message",true, false)),
                Collections.<BranchToPush>emptyList(),
                Collections.<NoteToPush>emptyList(),
                true, true, false) {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                run.incrementAndGet();
                try {
                    return super.perform(build, launcher, listener);
                } finally {
                    // until the 3rd one (which is the last one), we shouldn't create a tag
                    if (run.get()<3)
                        assertFalse(existsTag("foo"));
                }
            }

            @Override
            public BuildStepDescriptor getDescriptor() {
                return (BuildStepDescriptor)Hudson.getInstance().getDescriptorOrDie(GitPublisher.class); // fake
            }

            private Object writeReplace() { return new NullSCM(); }
        });

        MatrixBuild b = jenkins.assertBuildStatusSuccess(mp.scheduleBuild2(0).get());

        assertTrue(existsTag("foo"));

        assertTrue(containsTagMessage("foo", "message"));

        // twice for MatrixRun, which is to be ignored, then once for matrix completion
        assertEquals(3,run.get());
    }

    @Test
    public void testMergeAndPush() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new GitPublisher(
                Collections.<TagToPush>emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.<NoteToPush>emptyList(),
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
    public void testMergeAndPushFF() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, MergeCommand.GitPluginFastForwardMode.FF)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new GitPublisher(
                Collections.<TagToPush>emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.<NoteToPush>emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commitNewFile("commitFileBase");
        testGitClient.branch("integration");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, "commitFileBase");
        assertTrue(build1.getWorkspace().child("commitFileBase").exists());
        String shaIntegration = getHeadRevision(build1, "integration");
        assertEquals("the integration branch should be at HEAD", shaIntegration, testGitClient.revParse(Constants.HEAD).name());

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
        assertEquals("the integration branch and branch1 should line up",shaIntegration, shaBranch1);
        assertEquals("the integration branch should be at HEAD",shaIntegration, shaHead);
        // integration should have master as the parent commit
        List<ObjectId> revList = testGitClient.revList("integration^1");
        ObjectId integrationParent = revList.get(0);
        assertEquals("Fast-forward merge should have had master as a parent",master,integrationParent);

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
        assertEquals("Integration should have branch1 as a parent",revList.get(0).name(),shaBranch1);
        revList = testGitClient.revList("integration^2");
        assertEquals("Integration should have branch2 as a parent",revList.get(0).name(),shaBranch2);
    }

    @Issue("JENKINS-12402")
    @Test
    public void testMergeAndPushNoFF() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, MergeCommand.GitPluginFastForwardMode.NO_FF)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new GitPublisher(
                Collections.<TagToPush>emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.<NoteToPush>emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commitNewFile("commitFileBase");
        testGitClient.branch("integration");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, "commitFileBase");
        assertTrue(build1.getWorkspace().child("commitFileBase").exists());
        String shaIntegration = getHeadRevision(build1, "integration");
        assertEquals("integration branch should be at HEAD", shaIntegration, testGitClient.revParse(Constants.HEAD).name());

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
        assertEquals("Integration should have master as a parent",revList.get(0),master);
        revList = testGitClient.revList("integration^2");
        assertEquals("Integration should have branch1 as a parent",revList.get(0).name(),shaBranch1);

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
        assertTrue("commitFile1 should exist in the workspace",build1.getWorkspace().child(commitFile1).exists());
        assertTrue("commitFile2 should exist in the workspace",build1.getWorkspace().child(commitFile2).exists());
        // the integration branch should have branch1 and branch2 as parents
        revList = testGitClient.revList("integration^1");
        assertEquals("Integration should have the first merge commit as a parent",revList.get(0),mergeCommit);
        revList = testGitClient.revList("integration^2");
        assertEquals("Integration should have branch2 as a parent",revList.get(0).name(),shaBranch2);
    }

    @Issue("JENKINS-12402")
    @Test
    public void testMergeAndPushFFOnly() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, MergeCommand.GitPluginFastForwardMode.FF_ONLY)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new GitPublisher(
                Collections.<TagToPush>emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.<NoteToPush>emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commitNewFile("commitFileBase");
        testGitClient.branch("integration");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, "commitFileBase");
        assertTrue(build1.getWorkspace().child("commitFileBase").exists());
        String shaIntegration = getHeadRevision(build1, "integration");
        assertEquals("integration should be at HEAD", shaIntegration, testGitClient.revParse(Constants.HEAD).name());

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
        assertTrue("commitFile1 should exist in the worksapce",build2.getWorkspace().child("commitFile1").exists());
        shaIntegration = getHeadRevision(build2, "integration");
        String shaHead = testGitClient.revParse(Constants.HEAD).name();
        assertEquals("integration and branch1 should line up",shaIntegration, shaBranch1);
        assertEquals("integration and head should line up",shaIntegration, shaHead);
        // integration should have master as the parent commit
        List<ObjectId> revList = testGitClient.revList("integration^1");
        ObjectId integrationParent = revList.get(0);
        assertEquals("Fast-forward merge should have had master as a parent",master,integrationParent);

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
        assertFalse("commitFile1 should not exist in the worksapce",build2.getWorkspace().child("commitFile1").exists());
        assertTrue("commitFile2 should exist in the worksapce",build2.getWorkspace().child("commitFile2").exists());
        revList = testGitClient.revList("branch2^1");
        assertEquals("branch2 should have master as a parent",revList.get(0),master);
        try {
          revList = testGitClient.revList("branch2^2");
          assertTrue("branch2 should have no other parent than master",false);
        } catch (java.lang.NullPointerException err) {
          // expected
        }
    }

    @Issue("JENKINS-24786")
    @Test
    public void testPushEnvVarsInRemoteConfig() throws Exception{
    	FreeStyleProject project = setupSimpleProject("master");

        // create second (bare) test repository as target
        TaskListener listener = StreamTaskListener.fromStderr();
        TestGitRepo testTargetRepo = new TestGitRepo("target", tmpFolder.newFolder("push_env_vars"), listener);
    	testTargetRepo.git.init_().workspace(testTargetRepo.gitDir.getAbsolutePath()).bare(true).execute();
        testTargetRepo.commit("lostTargetFile", new PersonIdent("John Doe", "john@example.com"), "Initial Target Commit");

        // add second test repository as remote repository with environment variables
        List<UserRemoteConfig> remoteRepositories = remoteConfigs();
    	remoteRepositories.add(new UserRemoteConfig("$TARGET_URL", "$TARGET_NAME", "+refs/heads/$TARGET_BRANCH:refs/remotes/$TARGET_NAME/$TARGET_BRANCH", null));

        GitSCM scm = new GitSCM(
                remoteRepositories,
                Collections.singletonList(new BranchSpec("origin/master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);

        // add parameters for remote repository configuration
        project.addProperty(new ParametersDefinitionProperty(
        		new StringParameterDefinition("TARGET_URL", testTargetRepo.gitDir.getAbsolutePath()),
        		new StringParameterDefinition("TARGET_NAME", "target"),
        		new StringParameterDefinition("TARGET_BRANCH", "master")));

        String tag_name = "test-tag";
        String note_content = "Test Note";

        project.getPublishersList().add(new GitPublisher(
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
    public void testForcePush() throws Exception {
    	FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);

        project.getPublishersList().add(new GitPublisher(
                Collections.<TagToPush>emptyList(),
                Collections.singletonList(new BranchToPush("origin", "otherbranch")),
                Collections.<NoteToPush>emptyList(),
                true, true, true));

        commitNewFile("commitFile");

        testGitClient.branch("otherbranch");
        testGitClient.checkout("otherbranch");
        commitNewFile("otherCommitFile");

        testGitClient.checkout("master");
        commitNewFile("commitFile2");

        ObjectId expectedCommit = testGitClient.revParse("master");

        build(project, Result.SUCCESS, "commitFile");

        assertEquals(expectedCommit, testGitClient.revParse("otherbranch"));
    }

    /**
     * Fix push to remote when skipTag is enabled
     */
    @Issue("JENKINS-17769")
    @Test
    public void testMergeAndPushWithSkipTagEnabled() throws Exception {
      FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, new ArrayList<GitSCMExtension>());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);


      project.getPublishersList().add(new GitPublisher(
          Collections.<TagToPush>emptyList(),
          Collections.singletonList(new BranchToPush("origin", "integration")),
          Collections.<NoteToPush>emptyList(),
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

    @Issue("JENKINS-24786")
    @Test
    public void testMergeAndPushWithCharacteristicEnvVar() throws Exception {
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
        assertFalse("Env " + envName + " not set", envValue.equals("NOT-SET"));

        checkEnvVar(project, envName, envValue);
    }

    @Issue("JENKINS-24786")
    @Test
    public void testMergeAndPushWithSystemEnvVar() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        String envName = Functions.isWindows() ? "COMPUTERNAME" : "LOGNAME";
        String envValue = System.getenv().get(envName);
        assertNotNull("Env " + envName + " not set", envValue);
        assertFalse("Env " + envName + " empty", envValue.isEmpty());

        checkEnvVar(project, envName, envValue);
    }

    private void checkEnvVar(FreeStyleProject project, String envName, String envValue) throws Exception {

        String envReference = "${" + envName + "}";

        List<GitSCMExtension> scmExtensions = new ArrayList<GitSCMExtension>();
        scmExtensions.add(new PreBuildMerge(new UserMergeOptions("origin", envReference, null, null)));
        scmExtensions.add(new LocalBranch(envReference));
        GitSCM scm = new GitSCM(
                remoteConfigs(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, scmExtensions);
        project.setScm(scm);

        String tagNameReference = envReference + "-tag"; // ${BRANCH_NAME}-tag
        String tagNameValue = envValue + "-tag";         // master-tag
        String tagMessageReference = envReference + " tag message";
        String noteReference = "note for " + envReference;
        String noteValue = "note for " + envValue;
        GitPublisher publisher = new GitPublisher(
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
        assertFalse("Test repo has " + envValue + " branch", hasBranch(envValue));
        testGitClient.branch(envValue);
        assertTrue("Test repo missing " + envValue + " branch", hasBranch(envValue));
        assertFalse(tagNameValue + " in " + testGitClient, testGitClient.tagExists(tagNameValue));

        // Build the branch
        final FreeStyleBuild build0 = build(project, Result.SUCCESS, "commitFileBase");

        String build0HeadBranch = getHeadRevision(build0, envValue);
        assertEquals(build0HeadBranch, initialCommit.getName());
        assertTrue(tagNameValue + " not in " + testGitClient, testGitClient.tagExists(tagNameValue));
        assertTrue(tagNameValue + " not in build", build0.getWorkspace().child(".git/refs/tags/" + tagNameValue).exists());

        // Create a topic branch in the source repository and commit to topic branch
        String topicBranch = envValue + "-topic1";
        assertFalse("Test repo has " + topicBranch + " branch", hasBranch(topicBranch));
        testGitClient.checkout(null, topicBranch);
        assertTrue("Test repo has no " + topicBranch + " branch", hasBranch(topicBranch));
        final String commitFile1 = "commitFile1";
        commitNewFile(commitFile1);
        ObjectId topicCommit = testGitClient.getHeadRev(testGitDir.getAbsolutePath(), topicBranch);
        assertTrue(testGitClient.isCommitInRepo(topicCommit));

        // Run a build, should be on the topic branch, tagged, and noted
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        FilePath myWorkspace = build1.getWorkspace();
        assertTrue(myWorkspace.child(commitFile1).exists());
        assertTrue("Tag " + tagNameValue + " not in build", myWorkspace.child(".git/refs/tags/" + tagNameValue).exists());

        String build1Head = getHeadRevision(build1, envValue);
        assertEquals(build1Head, testGitClient.revParse(Constants.HEAD).name());
        assertEquals("Wrong head commit in build1", topicCommit.getName(), build1Head);

    }

    private boolean existsTag(String tag) throws InterruptedException {
        return existsTagInRepo(testGitClient, tag);
    }

    private boolean existsTagInRepo(GitClient gitClient, String tag) throws InterruptedException {
        Set<String> tags = gitClient.getTagNames("*");
        return tags.contains(tag);
    }

    private boolean containsTagMessage(String tag, String str) throws InterruptedException {
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
}
