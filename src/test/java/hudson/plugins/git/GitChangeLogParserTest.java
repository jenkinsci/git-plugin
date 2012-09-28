package hudson.plugins.git;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Unit tests of {@link GitChangeLogParser}
 */
public class GitChangeLogParserTest extends HudsonTestCase {

    private File createChangeLogFile(String firstLine, String... restLines) throws Exception {
        File log = File.createTempFile(getClass().getName(), ".tmp");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(log), "utf-8"));
        writer.write(firstLine);
        for (String line : restLines) {
            writer.write("\n" + line);
        }
        writer.close();
        return log;
    }

    /**
     * Test duplicate changes filtered from parsed change set list.
     * 
     * @throws Exception
     */
    public void testDuplicatesFiltered() throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(true);
        File log = createChangeLogFile(
            "commit 123abc456def",
            "    first message",
            "commit 123abc456def",
            "    second message"
        );

        GitChangeSetList list = parser.parse(null, log);
        assertNotNull(list);
        assertNotNull(list.getLogs());
        assertEquals(1, list.getLogs().size());
        GitChangeSet first = list.getLogs().get(0);
        assertNotNull(first);
        assertEquals("123abc456def", first.getId());
        assertEquals("first message", first.getMsg());
    }

    public void testCharacterCorruptionProblem() throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(true);
        File log = createChangeLogFile(
            "commit 123abc456def",
            "    “ú–{Œê"
        );

        GitChangeSetList list = parser.parse(null, log);
        GitChangeSet changeSet = list.getLogs().get(0);
        assertEquals("“ú–{Œê", changeSet.getMsg());
    }
}
