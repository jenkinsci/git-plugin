package hudson.plugins.git;

import hudson.scm.EditType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitChangeSetSimpleTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private GitChangeSet changeSet = null;
    private final boolean useAuthorName;
    private final boolean useLegacyFormat;

    public GitChangeSetSimpleTest(String useAuthorName, String useLegacyFormat) {
        this.useAuthorName = Boolean.valueOf(useAuthorName);
        this.useLegacyFormat = Boolean.valueOf(useLegacyFormat);
    }

    @Parameterized.Parameters(name = "{0},{1}")
    public static Collection permuteAuthorNameAndLegacyLayout() {
        List<Object[]> values = new ArrayList<>();
        String[] allowed = {"true", "false"};
        for (String authorName : allowed) {
            for (String legacyFormat : allowed) {
                Object[] combination = {authorName, legacyFormat};
                values.add(combination);
            }
        }
        return values;
    }

    @Before
    public void createSimpleChangeSet() {
        changeSet = GitChangeSetUtil.genChangeSet(useAuthorName, useLegacyFormat);
    }

    @Test
    public void testChangeSetDetails() {
        assertEquals(GitChangeSetUtil.ID, changeSet.getId());
        assertEquals(GitChangeSetUtil.COMMIT_TITLE, changeSet.getMsg());
        assertEquals("Commit title.\nCommit extended description.\n", changeSet.getComment());
        HashSet<String> expectedAffectedPaths = new HashSet<>(7);
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
            if (null != path.getPath()) switch (path.getPath()) {
                case "src/test/add.file":
                    assertEquals(EditType.ADD, path.getEditType());
                    assertNull(path.getSrc());
                    assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
                    break;
                case "src/test/deleted.file":
                    assertEquals(EditType.DELETE, path.getEditType());
                    assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                    assertNull(path.getDst());
                    break;
                case "src/test/modified.file":
                    assertEquals(EditType.EDIT, path.getEditType());
                    assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                    assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
                    break;
                case "src/test/renamedFrom.file":
                    assertEquals(EditType.DELETE, path.getEditType());
                    assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                    assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
                    break;
                case "src/test/renamedTo.file":
                    assertEquals(EditType.ADD, path.getEditType());
                    assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                    assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
                    break;
                case "src/test/copyOf.file":
                    assertEquals(EditType.ADD, path.getEditType());
                    assertEquals("bc234def567abc890def123abc456def789abc01", path.getSrc());
                    assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
                    break;
                default:
                    fail("Unrecognized path.");
                    break;
            }
        }
    }

    @Test
    public void testGetCommitId() {
        assertEquals(GitChangeSetUtil.ID, changeSet.getCommitId());
    }

    @Test
    public void testSetParent() {
        changeSet.setParent(null);
        assertNull(changeSet.getParent());
    }

    @Test
    public void testGetParentCommit() {
        assertEquals(GitChangeSetUtil.PARENT, changeSet.getParentCommit());
    }

    @Test
    public void testGetAffectedFiles() {
        assertEquals(6, changeSet.getAffectedFiles().size());
    }

    @Test
    public void testGetAuthorName() {
        assertEquals(useAuthorName ? GitChangeSetUtil.AUTHOR_NAME : GitChangeSetUtil.COMMITTER_NAME, changeSet.getAuthorName());
    }

    @Test
    public void testGetDate() {
        assertEquals(useAuthorName ? GitChangeSetUtil.AUTHOR_DATE_FORMATTED : GitChangeSetUtil.COMMITTER_DATE_FORMATTED, changeSet.getDate());
    }

    @Test
    public void testGetMsg() {
        assertEquals(GitChangeSetUtil.COMMIT_TITLE, changeSet.getMsg());
    }

    @Test
    public void testGetId() {
        assertEquals(GitChangeSetUtil.ID, changeSet.getId());
    }

    @Test
    public void testGetRevision() {
        assertEquals(GitChangeSetUtil.ID, changeSet.getRevision());
    }

    @Test
    public void testGetComment() {
        String changeComment = changeSet.getComment();
        assertTrue("Comment '" + changeComment + "' does not start with '" + GitChangeSetUtil.COMMENT + "'", changeComment.startsWith(GitChangeSetUtil.COMMENT));
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

    @Test
    public void testChangeSetExceptionMessage() {
        final String expectedLineContent = "commit ";
        ArrayList<String> lines = new ArrayList<>();
        lines.add(expectedLineContent);
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Commit has no ID[" + expectedLineContent + "]");
        GitChangeSet badChangeSet = new GitChangeSet(lines, true);
    }
}
