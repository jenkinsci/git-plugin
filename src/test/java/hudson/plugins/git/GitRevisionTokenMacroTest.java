/*
 * The MIT License
 *
 * Copyright 2019 Mark Waite.
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

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.Before;
import org.mockito.Mockito;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class GitRevisionTokenMacroTest {

    private GitRevisionTokenMacro tokenMacro;

    public GitRevisionTokenMacroTest() {
    }

    @Before
    public void createTokenMacro() {
        tokenMacro = new GitRevisionTokenMacro();
    }

    @Test
    public void testAcceptsMacroName() {
        assertTrue(tokenMacro.acceptsMacroName("GIT_REVISION"));
    }

    @Test
    public void testAcceptsMacroNameFalse() {
        assertFalse(tokenMacro.acceptsMacroName("NOT_A_GIT_REVISION"));
    }

    @Test(expected = NullPointerException.class)
    public void testEvaluate() throws Exception {
        // Real test in GitSCMTest#testBasicRemotePoll
        tokenMacro.evaluate(null, TaskListener.NULL, "GIT_REVISION");
    }

    @Test
    public void testEvaluateMockBuildNull() throws Exception {
        // Real test in GitSCMTest#testBasicRemotePoll
        AbstractBuild build = Mockito.mock(AbstractBuild.class);
        Mockito.when(build.getAction(BuildData.class)).thenReturn(null);
        assertThat(tokenMacro.evaluate(build, TaskListener.NULL, "GIT_REVISION"), is(""));
    }

    @Test
    public void testEvaluateMockBuildDataNull() throws Exception {
        // Real test in GitSCMTest#testBasicRemotePoll
        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.getLastBuiltRevision()).thenReturn(null);
        AbstractBuild build = Mockito.mock(AbstractBuild.class);
        Mockito.when(build.getAction(BuildData.class)).thenReturn(buildData);
        assertThat(tokenMacro.evaluate(build, TaskListener.NULL, "GIT_REVISION"), is(""));
    }

    @Test
    public void testEvaluateMockBuildData() throws Exception {
        // Real test in GitSCMTest#testBasicRemotePoll
        Revision revision = new Revision(ObjectId.fromString("42ab63c2d69c012122d9b373450404244cc58e81"));
        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
        AbstractBuild build = Mockito.mock(AbstractBuild.class);
        Mockito.when(build.getAction(BuildData.class)).thenReturn(buildData);
        assertThat(tokenMacro.evaluate(build, TaskListener.NULL, "GIT_REVISION"), is(revision.getSha1String()));
    }

    @Test
    public void testEvaluateMockBuildDataLength() throws Exception {
        // Real test in GitSCMTest#testBasicRemotePoll
        Revision revision = new Revision(ObjectId.fromString("42ab63c2d69c012122d9b373450404244cc58e81"));
        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
        AbstractBuild build = Mockito.mock(AbstractBuild.class);
        Mockito.when(build.getAction(BuildData.class)).thenReturn(buildData);
        tokenMacro.length = 8;
        assertThat(tokenMacro.evaluate(build, TaskListener.NULL, "GIT_REVISION"), is(revision.getSha1String().substring(0, 8)));
    }
}
