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
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.plugins.git.GitPublisher.BranchToPush;
import hudson.plugins.git.GitPublisher.TagToPush;
import hudson.scm.NullSCM;
import hudson.tasks.BuildStepDescriptor;

import java.util.Collections;

import org.jvnet.hudson.test.Bug;

/**
 * Tests for {@link GitPublisher}
 * 
 * @author Kohsuke Kawaguchi
 */
public class GitPublisherTest extends AbstractGitTestCase {
    @Bug(5005)
    public void testMatrixBuild() throws Exception {
        final int[] run =new  int[1]; // count the number of times the perform is called

        commit("a", johnDoe, "commit #1");

        MatrixProject mp = createMatrixProject("xyz");
        mp.setAxes(new AxisList(new Axis("VAR","a","b")));
        mp.setScm(new GitSCM(workDir.getAbsolutePath()));
        mp.getPublishersList().add(new GitPublisher(
                Collections.singletonList(new TagToPush("origin","foo",true)),
                Collections.<BranchToPush>emptyList(), true, true) {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                run[0]++;
                try {
                    return super.perform(build, launcher, listener);
                } finally {
                    // until the 3rd one (which is the last one), we shouldn't create a tag
                    if (run[0]<3)
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

        // twice for MatrixRun, which is to be ignored, then once for matrix completion
        assertEquals(3,run[0]);
    }

    private boolean existsTag(String tag) {
        String tags = git.launchCommand("tag");
        System.out.println(tags);
        return tags.contains(tag);
    }
}
