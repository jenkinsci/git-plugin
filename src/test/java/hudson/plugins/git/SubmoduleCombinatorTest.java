package hudson.plugins.git;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class SubmoduleCombinatorTest {

    private SubmoduleCombinator combinator = null;

    public SubmoduleCombinatorTest() {
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
        TaskListener listener = StreamTaskListener.fromStderr();
        GitClient gitClient = Git.with(listener, new EnvVars()).in(new File(".")).getClient();
        Collection<SubmoduleConfig> cfg = null;
        combinator = new SubmoduleCombinator(gitClient, listener, cfg);
    }

    @Test
    public void testDifferenceNulls() {
        Map<IndexEntry, Revision> item = new HashMap<IndexEntry, Revision>();
        List<IndexEntry> entries = new ArrayList<IndexEntry>();
        assertEquals(0, combinator.difference(item, entries));
    }

    @Test
    public void testDifferenceDifferentSize() {
        Map<IndexEntry, Revision> item = new HashMap<IndexEntry, Revision>();
        List<IndexEntry> entries = new ArrayList<IndexEntry>();
        assertTrue(entries.add(new IndexEntry("mode", "type", "object", "file")));
        assertEquals(-1, combinator.difference(item, entries));
    }

    @Test
    public void testDifferenceNoDifference() {
        Map<IndexEntry, Revision> items = new HashMap<IndexEntry, Revision>();
        List<IndexEntry> entries = new ArrayList<IndexEntry>();
        ObjectId sha1 = ObjectId.fromString("1c2a9e6194e6ede0805cda4c9ccc7e373e835414");
        IndexEntry indexEntry1 = new IndexEntry("mode-1", "type-1", sha1.getName(), "file-1");
        assertTrue("Failed to add indexEntry1 to entries", entries.add(indexEntry1));
        Revision revision = new Revision(sha1);
        assertNull("items[indexEntry1] had existing value", items.put(indexEntry1, revision));
        assertEquals("entries and items[entries] don't match", 0, combinator.difference(items, entries));
    }

    @Test
    public void testDifferenceOneDifference() {
        Map<IndexEntry, Revision> items = new HashMap<IndexEntry, Revision>();
        List<IndexEntry> entries = new ArrayList<IndexEntry>();
        ObjectId sha1 = ObjectId.fromString("1c2a9e6194e6ede0805cda4c9ccc7e373e835414");
        String fileName = "fileName";
        IndexEntry indexEntry1 = new IndexEntry("mode-1", "type-1", sha1.getName(), fileName);
        assertTrue("Failed to add indexEntry1 to entries", entries.add(indexEntry1));
        ObjectId sha2 = ObjectId.fromString("54094393c170c94d330b1ae52101922092b0abd2");
        Revision revision = new Revision(sha2);
        IndexEntry indexEntry2 = new IndexEntry("mode-2", "type-2", sha2.getName(), fileName);
        assertNull("items[indexEntry2] had existing value", items.put(indexEntry2, revision));
        assertEquals("entries and items[entries] wrong diff count", 1, combinator.difference(items, entries));
    }
}
