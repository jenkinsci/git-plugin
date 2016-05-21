package hudson.plugins.git;

import java.io.File;
import java.io.FileWriter;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests of {@link GitChangeLogParser}
 */
public class GitChangeLogParserTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    /**
     * Test duplicate changes filtered from parsed change set list.
     *
     * @throws Exception
     */
    @Test
    public void testDuplicatesFiltered() throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(true);
        File log = tmpFolder.newFile();
        try (FileWriter writer = new FileWriter(log)) {
            writer.write("commit 123abc456def\n");
            writer.write("    first message\n");
            writer.write("commit 123abc456def\n");
            writer.write("    second message");
        }
        GitChangeSetList list = parser.parse(null, null, log);
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
