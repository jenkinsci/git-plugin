/*
 * The MIT License
 *
 * Copyright 2015 Mark Waite.
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

import java.util.ArrayList;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * JENKINS-30073 reports that the timestamp returns -1 for the typical timestamp
 * reported by the +%ci format to git log and git whatchanged. This test
 * duplicates the bug.
 *
 * @author Mark Waite
 */
public class GitChangeSetTimestampTest {

    private GitChangeSet changeSet = null;

    @Before
    public void createChangeSet() {
        changeSet = genChangeSetForJenkins30073(true);
    }

    @Test
    public void testChangeSetDate() {
        assertEquals("2015-10-06 19:29:47 +0300", changeSet.getDate());
    }

    @Test
    @Issue("JENKINS-30073")
    public void testChangeSetTimeStamp() {
        assertEquals(1444148987000L, changeSet.getTimestamp());
    }

    private GitChangeSet genChangeSetForJenkins30073(boolean authorOrCommitter) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("commit 302548f75c3eb6fa1db83634e4061d0ded416e5a");
        lines.add("tree e1bd430d3f45b7aae54a3061b7895ee1858ec1f8");
        lines.add("parent c74f084d8f9bc9e52f0b3fe9175ad27c39947a73");
        lines.add("author Viacheslav Kopchenin <vkopchenin@odin.com> 2015-10-06 19:29:47 +0300");
        lines.add("committer Viacheslav Kopchenin <vkopchenin@odin.com> 2015-10-06 19:29:47 +0300");
        lines.add("");
        lines.add("    pom.xml");
        lines.add("    ");
        lines.add("    :100644 100644 bb32d78c69a7bf79849217bc02b1ba2c870a5a66 343a844ad90466d8e829896c1827ca7511d0d1ef M	modules/platform/pom.xml");
        lines.add("");
        return new GitChangeSet(lines, authorOrCommitter);
    }
}
