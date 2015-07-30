package hudson.plugins.git;

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GitChangeSetEmptyTest {

    private GitChangeSet changeSet = null;

    public GitChangeSetEmptyTest() {
    }

    @Before
    public void createEmptyChangeSet() {
        changeSet = new GitChangeSet(new ArrayList<String>(), false);
    }

    @Test
    public void testGetDate() {
        assertNull(changeSet.getDate());
    }

    @Test
    public void testGetCommitId() {
        assertNull(changeSet.getCommitId());
    }

    @Test
    public void testSetParent() {
        changeSet.setParent(null);
        assertNull(changeSet.getParent());
    }

    @Test
    public void testGetParentCommit() {
        assertNull(changeSet.getParentCommit());
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
        assertNull(changeSet.getAuthorName());
    }

    @Test
    public void testGetMsg() {
        assertNull(changeSet.getMsg());
    }

    @Test
    public void testGetId() {
        assertNull(changeSet.getId());
    }

    @Test
    public void testGetRevision() {
        assertNull(changeSet.getRevision());
    }

    @Test
    public void testGetComment() {
        assertNull(changeSet.getComment());
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
        assertEquals(changeSet, changeSet);
        assertNotEquals(changeSet, GitChangeSetUtil.genChangeSet(true, true));
    }

}
