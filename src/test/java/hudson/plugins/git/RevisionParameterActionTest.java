/*
 * The MIT License
 *
 * Copyright (c) 2012, Chris Johnson
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

import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.plugins.git.util.BuildData;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.Collections;
import static org.junit.Assert.*;
import org.junit.Test;

import org.eclipse.jgit.transport.URIish;

/**
 * Tests for {@link RevisionParameterAction}
 *
 * @author Chris Johnson
 */
public class RevisionParameterActionTest extends AbstractGitProject {

    @Test
    public void testProvidingRevision() throws Exception {

        FreeStyleProject p1 = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commitNewFile(commitFile1);
        FreeStyleBuild b1 = build(p1, Result.SUCCESS, commitFile1);

        Revision r1 = b1.getAction(BuildData.class).getLastBuiltRevision();

        // create a second commit
        final String commitFile2 = "commitFile2";
        commitNewFile(commitFile2);

        // create second build and set revision parameter using r1
        FreeStyleBuild b2 = p1.scheduleBuild2(0, new Cause.UserIdCause(),
        Collections.singletonList(new RevisionParameterAction(r1))).get();

        // Check revision built for b2 matches the r1 revision
        assertEquals(b2.getAction(BuildData.class)
                        .getLastBuiltRevision().getSha1String(), r1.getSha1String());
        assertEquals(b2.getAction(BuildData.class)
                        .getLastBuiltRevision().getBranches().iterator().next()
                        .getName(), r1.getBranches().iterator().next().getName());

        // create a third build
        FreeStyleBuild b3 = build(p1, Result.SUCCESS, commitFile2);

        // Check revision built for b3 does not match r1 revision
        assertFalse(b3.getAction(BuildData.class)
                        .getLastBuiltRevision().getSha1String().equals(r1.getSha1String()));		
    }

    @Test
    public void testBranchFetchingIfNoBranchSpecified() throws Exception {
        commitNewFile("test");
        testGitClient.branch("aaa");
        String commit = testGitClient.getBranches().iterator().next().getSHA1String();
        RevisionParameterAction action = new RevisionParameterAction(commit, new URIish("origin"));
        Collection<Branch> branches = action.toRevision(testGitClient).getBranches();
        assertEquals(branches.size(), 2);
        Branch branchesArray[] = branches.toArray(new Branch[branches.size()]);
        assertEquals(branchesArray[0].getName(), "aaa");
        assertEquals(branchesArray[1].getName(), "master");
    }

    @Test
    public void testBranchFetchingIfBranchSpecified() throws Exception {
        commitNewFile("test");
        testGitClient.branch("aaa");
        String commit = testGitClient.getBranches().iterator().next().getSHA1String();
        RevisionParameterAction action = new RevisionParameterAction(commit, new URIish("origin"), "master");
        Collection<Branch> branches = action.toRevision(testGitClient).getBranches();
        assertEquals(branches.size(), 1);
        assertEquals(branches.iterator().next().getName(), "master");
    }
}

