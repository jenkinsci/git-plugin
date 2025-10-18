package hudson.plugins.git;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitChangeSetEmptyTest {

    private GitChangeSet changeSet = null;

    @BeforeEach
    void beforeEach() {
        changeSet = new GitChangeSet(new ArrayList<>(), false);
    }

    @Test
    void testGetDate() {
        assertNull(changeSet.getDate());
    }

    @Test
    void testGetTimestamp() {
        assertEquals(-1L, changeSet.getTimestamp());
    }

    @Test
    void testGetCommitId() {
        assertNull(changeSet.getCommitId());
    }

    @Test
    void testSetParent() {
        changeSet.setParent(null);
        assertNull(changeSet.getParent());
    }

    @Test
    void testGetParentCommit() {
        assertNull(changeSet.getParentCommit());
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
        assertNull(changeSet.getAuthorName());
    }

    @Test
    void testGetMsg() {
        assertNull(changeSet.getMsg());
    }

    @Test
    void testGetId() {
        assertNull(changeSet.getId());
    }

    @Test
    void testGetRevision() {
        assertNull(changeSet.getRevision());
    }

    @Test
    void testGetComment() {
        assertNull(changeSet.getComment());
    }

    @Test
    void testGetBranch() {
        assertNull(changeSet.getBranch());
    }

    @Test
    void testHashCode() {
        assertTrue(changeSet.hashCode() != 0);
    }

    @Test
    void testEquals() {
        assertEquals(changeSet, changeSet);
        assertNotEquals(changeSet, GitChangeSetUtil.genChangeSet(true, true));
    }

}
