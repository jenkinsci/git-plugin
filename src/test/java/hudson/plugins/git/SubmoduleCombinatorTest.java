package hudson.plugins.git;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.junit.jupiter.api.BeforeEach;

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
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@Deprecated
class SubmoduleCombinatorTest {

    private SubmoduleCombinator combinator = null;

    @BeforeEach
    void beforeEach() throws IOException, InterruptedException {
        TaskListener listener = StreamTaskListener.fromStderr();
        GitClient gitClient = Git.with(listener, new EnvVars()).in(new File(".")).getClient();
        Collection<SubmoduleConfig> cfg = null;
        combinator = new SubmoduleCombinator(gitClient, listener, cfg);
    }

    @Test
    void testDifferenceNulls() {
        Map<IndexEntry, Revision> item = new HashMap<>();
        List<IndexEntry> entries = new ArrayList<>();
        assertEquals(0, combinator.difference(item, entries));
    }

    @Test
    void testDifferenceDifferentSize() {
        Map<IndexEntry, Revision> item = new HashMap<>();
        List<IndexEntry> entries = new ArrayList<>();
        assertTrue(entries.add(new IndexEntry("mode", "type", "object", "file")));
        /* Deprecated - no differences even when differences exist */
        assertEquals(0, combinator.difference(item, entries));
    }

    @Test
    void testDifferenceNoDifference() {
        Map<IndexEntry, Revision> items = new HashMap<>();
        List<IndexEntry> entries = new ArrayList<>();
        ObjectId sha1 = ObjectId.fromString("1c2a9e6194e6ede0805cda4c9ccc7e373e835414");
        IndexEntry indexEntry1 = new IndexEntry("mode-1", "type-1", sha1.getName(), "file-1");
        assertTrue(entries.add(indexEntry1), "Failed to add indexEntry1 to entries");
        Revision revision = new Revision(sha1);
        assertNull(items.put(indexEntry1, revision), "items[indexEntry1] had existing value");
        assertEquals(0, combinator.difference(items, entries), "entries and items[entries] don't match");
    }

    @Test
    void testDifferenceOneDifference() {
        Map<IndexEntry, Revision> items = new HashMap<>();
        List<IndexEntry> entries = new ArrayList<>();
        ObjectId sha1 = ObjectId.fromString("1c2a9e6194e6ede0805cda4c9ccc7e373e835414");
        String fileName = "fileName";
        IndexEntry indexEntry1 = new IndexEntry("mode-1", "type-1", sha1.getName(), fileName);
        assertTrue(entries.add(indexEntry1), "Failed to add indexEntry1 to entries");
        ObjectId sha2 = ObjectId.fromString("54094393c170c94d330b1ae52101922092b0abd2");
        Revision revision = new Revision(sha2);
        IndexEntry indexEntry2 = new IndexEntry("mode-2", "type-2", sha2.getName(), fileName);
        assertNull(items.put(indexEntry2, revision), "items[indexEntry2] had existing value");
        /* Deprecated - no differences even when differences exist */
        assertEquals(0, combinator.difference(items, entries), "entries and items[entries] wrong diff count");
    }
}
