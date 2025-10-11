package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

@ParameterizedClass(name = "{0}")
@MethodSource("permuteAuthorNameAndLegacyLayout")
class GitChangeSetEuroTest {

    private final String id = "1567861636cd854f4dd6fa40bf94c0c657681dd5";
    private final String parent = "e74a24e995305bd67a180f0ebc57927e2b8783ce";
    private final String authorName = "Mr. Åhłañder";
    private final String committerName = "Mister Åhländèr";
    private final String msg = "[task] Updated version.";
    private final String comment1 = "Including earlier updates.";
    private final String commentStartText = msg + "\n\n" + comment1 + "\n";

    private GitChangeSet changeSet = null;
    private final boolean useAuthorName;

    public GitChangeSetEuroTest(boolean useAuthorName) {
        this.useAuthorName = useAuthorName;
    }

    static Collection permuteAuthorNameAndLegacyLayout() {
        List<Object[]> values = new ArrayList<>();
        boolean[] allowed = {true, false};
        for (boolean authorName : allowed) {
            Object[] combination = {authorName};
            values.add(combination);
        }
        return values;
    }

    @BeforeEach
    void beforeEach() {
        ArrayList<String> gitChangeLog = new ArrayList<>();
        gitChangeLog.add("commit " + id);
        gitChangeLog.add("tree 66236cf9a1ac0c589172b450ed01f019a5697c49");
        gitChangeLog.add("parent " + parent);
        gitChangeLog.add("author " + authorName + " <mister.ahlander@ericsson.com> 1363879004 +0100");
        gitChangeLog.add("committer " + committerName + " <mister.ahlander@ericsson.com> 1364199539 -0400");
        gitChangeLog.add("");
        gitChangeLog.add("    " + msg);
        gitChangeLog.add("    ");
        gitChangeLog.add("    " + comment1);
        gitChangeLog.add("    ");
        gitChangeLog.add("    Changes in this version:");
        gitChangeLog.add("    - Changed to take the gerrit url from gerrit query command.");
        gitChangeLog.add("    - Aligned reason information with our new commit hooks");
        gitChangeLog.add("    ");
        gitChangeLog.add("    Change-Id: Ife96d2abed5b066d9620034bec5f04cf74b8c66d");
        gitChangeLog.add("    Reviewed-on: https://gerrit.e.se/12345");
        gitChangeLog.add("    Tested-by: Jenkins <jenkins@no-mail.com>");
        gitChangeLog.add("    Reviewed-by: Mister Another <mister.another@ericsson.com>");
        gitChangeLog.add("");
        changeSet = new GitChangeSet(gitChangeLog, useAuthorName, false);
    }

    @Test
    void testGetCommitId() {
        assertEquals(id, changeSet.getCommitId());
    }

    @Test
    void testSetParent() {
        changeSet.setParent(null);
        assertNull(changeSet.getParent());
    }

    @Test
    void testGetParentCommit() {
        assertEquals(parent, changeSet.getParentCommit());
    }

    @Test
    void testGetAffectedPaths() {
        assertTrue(changeSet.getAffectedPaths().isEmpty());
    }

    @Test
    void testGetPaths() {
        assertTrue(changeSet.getPaths().isEmpty());
    }

    @Test
    void testGetAffectedFiles() {
        assertTrue(changeSet.getAffectedFiles().isEmpty());
    }

    @Test
    void testGetAuthorName() {
        assertEquals(useAuthorName ? authorName : committerName, changeSet.getAuthorName());
    }

    @Test
    void testGetMsg() {
        assertEquals(msg, changeSet.getMsg());
    }

    @Test
    void testGetId() {
        assertEquals(id, changeSet.getId());
    }

    @Test
    void testGetRevision() {
        assertEquals(id, changeSet.getRevision());
    }

    @Test
    void testGetComment() {
        assertTrue(changeSet.getComment().startsWith(commentStartText));
    }

    @Test
    void testGetBranch() {
        assertNull(changeSet.getBranch());
    }

    @Test
    void testGetDate() {
        assertEquals(useAuthorName ? "2013-03-21T15:16:44+0100" : "2013-03-25T08:18:59-0400", changeSet.getDate());
    }

    @Test
    void testGetTimestamp() {
        assertEquals(useAuthorName ? 1363875404000L : 1364213939000L, changeSet.getTimestamp());
    }

    @Test
    void testHashCode() {
        assertTrue(changeSet.hashCode() != 0);
    }

    @Test
    void testEquals() {
        assertEquals(changeSet, changeSet);

        assertEquals(GitChangeSetUtil.genChangeSet(false, false), GitChangeSetUtil.genChangeSet(false, false));
        assertEquals(GitChangeSetUtil.genChangeSet(true, false), GitChangeSetUtil.genChangeSet(true, false));
        assertEquals(GitChangeSetUtil.genChangeSet(false, true), GitChangeSetUtil.genChangeSet(false, true));
        assertEquals(GitChangeSetUtil.genChangeSet(true, true), GitChangeSetUtil.genChangeSet(true, true));

        assertNotEquals(changeSet, GitChangeSetUtil.genChangeSet(false, false));
        assertNotEquals(GitChangeSetUtil.genChangeSet(true, false), changeSet);
        assertNotEquals(changeSet, GitChangeSetUtil.genChangeSet(false, true));
        assertNotEquals(GitChangeSetUtil.genChangeSet(true, true), changeSet);
    }
}
