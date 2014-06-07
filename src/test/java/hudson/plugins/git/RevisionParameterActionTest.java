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

import java.util.concurrent.Future;
import java.util.Collections;

/**
 * Tests for {@link RevisionParameterAction}
 * 
 * @author Chris Johnson
 */
public class RevisionParameterActionTest extends AbstractGitTestCase {
    
    /**
     * Test covering the behaviour after 1.1.26 where passing different revision 
     * actions to a job in the queue creates separate builds
     */
    public void testCombiningScheduling() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF")));
        Future b2 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("FREED456")));

        // Check that we have the correct futures.
        assertNotNull(b1);
        assertNotNull(b2);
        
        // Check that two builds occurred
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),2);
    }
    /** test when existing revision is already in the queue
    */
    public void testCombiningScheduling2() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF")));
        Future b2 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF")));

        // Check that we have the correct futures.
        assertNotNull(b1);
        /* As of 1.521 this is non-null, although the future yields the same build as b1:
        assertNull(b2);
        */
        
        // Check that only one build occurred
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),1);
    }
    /** test when there is no revision on the item in the queue
    */
    public void testCombiningScheduling3() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(3);
        Future b2 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF")));

        // Check that we have the correct futures.
        assertNotNull(b1);
        assertNotNull(b2);
        
        // Check that two builds occurred
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),2);
    }

    /** test when a different revision is already in the queue, and combine requests is required.
    */
    public void testCombiningScheduling4() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF", true)));
        Future b2 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("FFEEFFEE", true)));

        // Check that we have the correct futures.
        assertNotNull(b1);
        //assertNull(b2);

        // Check that only one build occurred
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),1);

        //check that the correct commit id is present in build
        assertEquals(fs.getBuilds().get(0).getAction(RevisionParameterAction.class).commit, "FFEEFFEE");

    }

    /** test when a same revision is already in the queue, and combine requests is required.
    */
    public void testCombiningScheduling5() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF", true)));
        Future b2 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF", true)));

        // Check that we have the correct futures.
        assertNotNull(b1);
        //assertNull(b2);

        // Check that only one build occurred
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),1);

        //check that the correct commit id is present in build
        assertEquals(fs.getBuilds().get(0).getAction(RevisionParameterAction.class).commit, "DEADBEEF");
    }

    /** test when a job already in the queue with no revision(manually started), and combine requests is required.
    */
    public void testCombiningScheduling6() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(3);
        Future b2 = fs.scheduleBuild2(3, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF", true)));

        // Check that we have the correct futures.
        assertNotNull(b1);
        assertNotNull(b2);

        // Check that two builds occurred
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),2);

        //check that the correct commit id is present in 2nd build
        // list is reversed indexed so first item is latest build
        assertEquals(fs.getBuilds().get(0).getAction(RevisionParameterAction.class).commit, "DEADBEEF");
    }
    

	public void testProvidingRevision() throws Exception {

		FreeStyleProject p1 = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        FreeStyleBuild b1 = build(p1, Result.SUCCESS, commitFile1);
        
        Revision r1 = b1.getAction(BuildData.class).getLastBuiltRevision();
        
        // create a second commit
        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");       

		// create second build and set revision parameter using r1
        FreeStyleBuild b2 = p1.scheduleBuild2(0, new Cause.UserCause(),
				Collections.singletonList(new RevisionParameterAction(r1))).get();
        System.out.println(b2.getLog());
        
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
		
		if (System.getProperty("os.name").startsWith("Windows")) {
		  System.gc(); // Prevents exceptions cleaning up temp dirs during tearDown
		}

	}
}

