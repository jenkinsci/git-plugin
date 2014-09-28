package hudson.plugins.git;

import hudson.Functions;
import hudson.model.Run;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Unit tests of {@link GitChangeLogParser}
 */
public class GitChangeLogParserTest extends HudsonTestCase {

    @Override
    protected void tearDown() throws Exception
    {
        try { //Avoid test failures due to failed cleanup tasks
            super.tearDown();
        }
        catch (Exception e) {
            if (e instanceof IOException && Functions.isWindows()) {
                return;
            }
            e.printStackTrace();
        }
    }

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
        GitChangeSetList list = parser.parse((Run) null, null, log);
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
