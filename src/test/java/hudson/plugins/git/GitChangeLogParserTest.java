package hudson.plugins.git;

import java.io.File;
import java.io.FileWriter;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Unit tests of {@link GitChangeLogParser}
 */
public class GitChangeLogParserTest extends HudsonTestCase {

    /**
     * Test duplicate changes filtered from parsed change set list.
     * 
     * @throws Exception
     */
    public void testDuplicatesFiltered() throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(true);
        File log = File.createTempFile(getClass().getName(), ".tmp");
        FileWriter writer = new FileWriter(log);
        writer.write("commit 123abc456def\n");
        writer.write("    first message\n");
        writer.write("commit 123abc456def\n");
        writer.write("    second message");
        writer.close();
        GitChangeSetList list = parser.parse(null, log);
        assertNotNull(list);
        assertNotNull(list.getLogs());
        assertEquals(1, list.getLogs().size());
        GitChangeSet first = list.getLogs().get(0);
        assertNotNull(first);
        assertEquals("123abc456def", first.getId());
        assertEquals("first message", first.getMsg());
        assertTrue("Temp file delete failed for " + log, log.delete());
    }
}
