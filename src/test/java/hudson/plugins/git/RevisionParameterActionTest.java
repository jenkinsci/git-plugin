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

import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.concurrent.Future;
import java.util.Collections;

/**
 * Tests for {@link RevisionParameterAction}
 * 
 * @author Chris Johnson
 */
public class RevisionParameterActionTest extends HudsonTestCase {
    
    /**
     * Test covering the behaviour after 1.1.26 where passing different revision 
     * actions to a job in the queue creates seperate builds
     */
    public void testCombiningScheduling() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(20, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF")));
        Future b2 = fs.scheduleBuild2(20, null, Collections.singletonList(new RevisionParameterAction("FREED456")));

        // Check that we have the correct futures.
        assertNotNull(b1);
        assertNotNull(b2);
        
        // Check that only one build occured
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),2);
    }
    /** test when existing revision is already in the queue
    */
    public void testCombiningScheduling2() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(20, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF")));
        Future b2 = fs.scheduleBuild2(20, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF")));

        // Check that we have the correct futures.
        assertNotNull(b1);
        assertNull(b2);
        
        // Check that only one build occured
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),1);
    }
    /** test when there is no revision on the item in the queue
    */
    public void testCombiningScheduling3() throws Exception {

        FreeStyleProject fs = createFreeStyleProject("freestyle");

        // scheduleBuild2 returns null if request is combined into an existing item. (no new item added to queue)
        Future b1 = fs.scheduleBuild2(20);
        Future b2 = fs.scheduleBuild2(20, null, Collections.singletonList(new RevisionParameterAction("DEADBEEF")));

        // Check that we have the correct futures.
        assertNotNull(b1);
        assertNotNull(b2);
        
        // Check that only one build occured
        waitUntilNoActivity();
        assertEquals(fs.getBuilds().size(),2);
    }
}
