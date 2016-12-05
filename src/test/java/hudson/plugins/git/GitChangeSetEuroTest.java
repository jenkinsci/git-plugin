package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitChangeSetEuroTest {

    private final String id = "1567861636cd854f4dd6fa40bf94c0c657681dd5";
    private final String parent = "e74a24e995305bd67a180f0ebc57927e2b8783ce";
    private final String authorName = "Mr. Åhłañder";
    private final String committerName = "Mister Åhländèr";
    private final String msg = "[task] Updated version.";
    private final String comment1 = "Including earlier updates.";
    private final String commentStartText = msg + "\n\n" + comment1 + "\n";

    private GitChangeSet changeSet = null;
    private final boolean useAuthorName;

    public GitChangeSetEuroTest(String useAuthorName) {
        this.useAuthorName = Boolean.valueOf(useAuthorName);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteAuthorNameAndLegacyLayout() {
        List<Object[]> values = new ArrayList<>();
        String[] allowed = {"true", "false"};
        for (String authorName : allowed) {
            Object[] combination = {authorName};
            values.add(combination);
        }
        return values;
    }

    @Before
    public void createEuroChangeSet() {
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
        changeSet = new GitChangeSet(gitChangeLog, useAuthorName);
    }

    @Test
    public void testGetCommitId() {
        assertEquals(id, changeSet.getCommitId());
    }

    @Test
    public void testSetParent() {
        changeSet.setParent(null);
        assertNull(changeSet.getParent());
    }

    @Test
    public void testGetParentCommit() {
        assertEquals(parent, changeSet.getParentCommit());
    }

    @Test
    public void testGetAffectedPaths() {
        assertTrue(changeSet.getAffectedPaths().isEmpty());
    }

    @Test
    public void testGetPaths() {
        assertTrue(changeSet.getPaths().isEmpty());
    }

    @Test
    public void testGetAffectedFiles() {
        assertTrue(changeSet.getAffectedFiles().isEmpty());
    }

    @Test
    public void testGetAuthorName() {
        assertEquals(useAuthorName ? authorName : committerName, changeSet.getAuthorName());
    }

    @Test
    public void testGetMsg() {
        assertEquals(msg, changeSet.getMsg());
    }

    @Test
    public void testGetId() {
        assertEquals(id, changeSet.getId());
    }

    @Test
    public void testGetRevision() {
        assertEquals(id, changeSet.getRevision());
    }

    @Test
    public void testGetComment() {
        assertTrue(changeSet.getComment().startsWith(commentStartText));
    }

    @Test
    public void testGetBranch() {
        assertNull(changeSet.getBranch());
    }

    @Test
    public void testGetDate() {
        assertEquals(useAuthorName ? "2013-03-21T15:16:44+0100" : "2013-03-25T08:18:59-0400", changeSet.getDate());
    }

    @Test
    public void testGetTimestamp() {
        assertEquals(useAuthorName ? 1363875404000L : 1364213939000L, changeSet.getTimestamp());
    }

    @Test
    public void testHashCode() {
        assertTrue(changeSet.hashCode() != 0);
    }

    @Test
    public void testEquals() {
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
