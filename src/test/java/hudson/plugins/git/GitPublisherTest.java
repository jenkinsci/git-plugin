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
import hudson.plugins.git.UserMergeOptions;
import hudson.scm.NullSCM;
import hudson.tasks.BuildStepDescriptor;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.jvnet.hudson.test.Bug;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for {@link GitPublisher}
 * 
 * @author Kohsuke Kawaguchi
 */
public class GitPublisherTest extends AbstractGitTestCase {
    @Bug(5005)
    public void testMatrixBuild() throws Exception {
        final AtomicInteger run = new AtomicInteger(); // count the number of times the perform is called

        commit("a", johnDoe, "commit #1");

        MatrixProject mp = createMatrixProject("xyz");
        mp.setAxes(new AxisList(new Axis("VAR","a","b")));
        mp.setScm(new GitSCM(workDir.getAbsolutePath()));
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

        MatrixBuild b = assertBuildStatusSuccess(mp.scheduleBuild2(0).get());
        System.out.println(b.getLog());

        assertTrue(existsTag("foo"));

        assertTrue(containsTagMessage("foo", "message"));

        // twice for MatrixRun, which is to be ignored, then once for matrix completion
        assertEquals(3,run.get());
    }

    public void testMergeAndPush() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);

        project.getPublishersList().add(new GitPublisher(
                Collections.<TagToPush>emptyList(),
                Collections.singletonList(new BranchToPush("origin", "integration")),
                Collections.<NoteToPush>emptyList(),
                true, true, false));

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        String sha1 = getHeadRevision(build1, "integration");
        assertEquals(sha1, testRepo.git.revParse(Constants.HEAD).name());

    }
    
    @Bug(24082)
    public void testForcePush() throws Exception {
    	FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
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

        commit("commitFile", johnDoe, "Initial Commit");

        testRepo.git.branch("otherbranch");
        testRepo.git.checkout("otherbranch");
        commit("otherCommitFile", johnDoe, "commit lost on force push");
        
        testRepo.git.checkout("master");
        commit("commitFile2", johnDoe, "commit to be pushed");
        
        ObjectId expectedCommit = testRepo.git.revParse("master");
        
        build(project, Result.SUCCESS, "commitFile");

        assertEquals(expectedCommit, testRepo.git.revParse("otherbranch"));
    }

    /**
     * Fix push to remote when skipTag is enabled
     */
    @Bug(17769)
    public void testMergeAndPushWithSkipTagEnabled() throws Exception {
      FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, new ArrayList<GitSCMExtension>());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null)));
        scm.getExtensions().add(new LocalBranch("integration"));
        project.setScm(scm);


      project.getPublishersList().add(new GitPublisher(
          Collections.<TagToPush>emptyList(),
          Collections.singletonList(new BranchToPush("origin", "integration")),
          Collections.<NoteToPush>emptyList(),
          true, true, false));

      // create initial commit and then run the build against it:
      commit("commitFileBase", johnDoe, "Initial Commit");
      testRepo.git.branch("integration");
      build(project, Result.SUCCESS, "commitFileBase");

      testRepo.git.checkout(null, "topic1");
      final String commitFile1 = "commitFile1";
      commit(commitFile1, johnDoe, "Commit number 1");
      final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
      assertTrue(build1.getWorkspace().child(commitFile1).exists());

      String sha1 = getHeadRevision(build1, "integration");
      assertEquals(sha1, testRepo.git.revParse(Constants.HEAD).name());

    }

    private boolean existsTag(String tag) throws InterruptedException {
        Set<String> tags = git.getTagNames("*");
        System.out.println(tags);
        return tags.contains(tag);
    }

    private boolean containsTagMessage(String tag, String str) throws InterruptedException {
        String msg = git.getTagMessage(tag);
        System.out.println(msg);
        return msg.contains(str);
    }
}
