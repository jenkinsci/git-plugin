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
