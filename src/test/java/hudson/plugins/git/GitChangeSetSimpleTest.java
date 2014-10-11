package hudson.plugins.git;

import hudson.scm.EditType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitChangeSetSimpleTest {

    private GitChangeSet changeSet = null;
    private final String id = "123abc456def";
    private final String parent = "345mno678pqr";
    private final String authorName = "John Author";
    private final String committerName = "John Committer";
    private final String commitTitle = "Commit title.";
    private final String comment = commitTitle + "\n";

    protected final boolean useAuthorName;
    protected boolean useLegacyFormat = false;

    public GitChangeSetSimpleTest(String useAuthorName, String useLegacyFormat) {
        this.useAuthorName = Boolean.valueOf(useAuthorName);
        this.useLegacyFormat = Boolean.valueOf(useLegacyFormat);
    }

    @Parameterized.Parameters(name = "{0},{1}")
    public static Collection permuteAuthorNameAndLegacyLayout() {
        List<Object[]> values = new ArrayList<Object[]>();
        String[] allowed = {"true", "false"};
        for (String authorName : allowed) {
            for (String legacyLayout : allowed) {
                Object[] combination = {authorName, legacyLayout};
                values.add(combination);
            }
        }
        return values;
    }

    @Before
    public void createSimpleChangeSet() {
        List<String> gitChangeLog = new ArrayList<String>();
        gitChangeLog.add("Some header junk we should ignore...");
        gitChangeLog.add("header line 2");
        gitChangeLog.add("commit " + id);
        gitChangeLog.add("tree 789ghi012jkl");
        gitChangeLog.add("parent " + parent);
        gitChangeLog.add("author " + authorName + " <jauthor@nospam.com> 1234568 -0600");
        gitChangeLog.add("committer " + committerName + " <jcommitter@nospam.com> 1234566 -0600");
        gitChangeLog.add("");
        gitChangeLog.add("    " + commitTitle);
        gitChangeLog.add("    Commit extended description.");
        gitChangeLog.add("");
        if (useLegacyFormat) {
            gitChangeLog.add("123abc456def");
            gitChangeLog.add(" create mode 100644 some/file1");
            gitChangeLog.add(" delete mode 100644 other/file2");
        }
        gitChangeLog.add(":000000 123456 0000000000000000000000000000000000000000 123abc456def789abc012def345abc678def901a A\tsrc/test/add.file");
        gitChangeLog.add(":123456 000000 123abc456def789abc012def345abc678def901a 0000000000000000000000000000000000000000 D\tsrc/test/deleted.file");
        gitChangeLog.add(":123456 789012 123abc456def789abc012def345abc678def901a bc234def567abc890def123abc456def789abc01 M\tsrc/test/modified.file");
        gitChangeLog.add(":123456 789012 123abc456def789abc012def345abc678def901a bc234def567abc890def123abc456def789abc01 R012\tsrc/test/renamedFrom.file\tsrc/test/renamedTo.file");
        gitChangeLog.add(":000000 123456 bc234def567abc890def123abc456def789abc01 123abc456def789abc012def345abc678def901a C100\tsrc/test/original.file\tsrc/test/copyOf.file");
        changeSet = new GitChangeSet(gitChangeLog, useAuthorName);
    }

    @Test
    public void testChangeSetDetails() {
        assertEquals("123abc456def", changeSet.getId());
        assertEquals("Commit title.", changeSet.getMsg());
        assertEquals("Commit title.\nCommit extended description.\n", changeSet.getComment());
        HashSet<String> expectedAffectedPaths = new HashSet<String>(7);
        expectedAffectedPaths.add("src/test/add.file");
        expectedAffectedPaths.add("src/test/deleted.file");
        expectedAffectedPaths.add("src/test/modified.file");
        expectedAffectedPaths.add("src/test/renamedFrom.file");
        expectedAffectedPaths.add("src/test/renamedTo.file");
        expectedAffectedPaths.add("src/test/copyOf.file");
        assertEquals(expectedAffectedPaths, changeSet.getAffectedPaths());

        Collection<GitChangeSet.Path> actualPaths = changeSet.getPaths();
        assertEquals(6, actualPaths.size());
        for (GitChangeSet.Path path : actualPaths) {
            if ("src/test/add.file".equals(path.getPath())) {
                assertEquals(EditType.ADD, path.getEditType());
                assertNull(path.getSrc());
                assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
            } else if ("src/test/deleted.file".equals(path.getPath())) {
                assertEquals(EditType.DELETE, path.getEditType());
                assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                assertNull(path.getDst());
            } else if ("src/test/modified.file".equals(path.getPath())) {
                assertEquals(EditType.EDIT, path.getEditType());
                assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/renamedFrom.file".equals(path.getPath())) {
                assertEquals(EditType.DELETE, path.getEditType());
                assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/renamedTo.file".equals(path.getPath())) {
                assertEquals(EditType.ADD, path.getEditType());
                assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/copyOf.file".equals(path.getPath())) {
                assertEquals(EditType.ADD, path.getEditType());
                assertEquals("bc234def567abc890def123abc456def789abc01", path.getSrc());
                assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
            } else {
                fail("Unrecognized path.");
            }
        }
    }

    @Test
    public void testGetDate() {
        assertEquals(useAuthorName ? "1970-01-14T23:56:08-0700 -0600" : "1970-01-14T23:56:06-0700 -0600", changeSet.getDate());
    }

    @Test
    public void testGetTimestamp() {
        assertEquals(useAuthorName ? 1234568000L : 1234566000L, changeSet.getTimestamp());
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
    public void testGetAffectedFiles() {
        assertEquals(6, changeSet.getAffectedFiles().size());
    }

    @Test
    public void testGetAuthorName() {
        assertEquals(useAuthorName ? authorName : committerName, changeSet.getAuthorName());
    }

    @Test
    public void testGetMsg() {
        assertEquals(commitTitle, changeSet.getMsg());
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
        String changeComment = changeSet.getComment();
        assertTrue("Comment '" + changeComment + "' does not start with '" + comment + "'", changeComment.startsWith(comment));
    }

    @Test
    public void testGetBranch() {
        assertNull(changeSet.getBranch());
    }

    @Test
    public void testHashCode() {
        assertTrue(changeSet.hashCode() != 0);
    }

    @Test
    public void testEquals() {
        assertTrue(changeSet.equals(changeSet));
        assertFalse(changeSet.equals(new GitChangeSet(new ArrayList<String>(), false)));
    }

}
