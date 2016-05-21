package hudson.plugins.git;

import hudson.scm.EditType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import junit.framework.TestCase;

/** Utility class to support GitChangeSet testing. */
public class GitChangeSetUtil {

    static final String ID = "123abc456def";
    static final String PARENT = "345mno678pqr";
    static final String AUTHOR_NAME = "John Author";
    static final String AUTHOR_DATE = "1234568 -0600";
    static final String AUTHOR_DATE_FORMATTED = "1970-01-15T06:56:08-0600";
    static final String COMMITTER_NAME = "John Committer";
    static final String COMMITTER_DATE = "1234566 -0600";
    static final String COMMITTER_DATE_FORMATTED = "1970-01-15T06:56:06-0600";
    static final String COMMIT_TITLE = "Commit title.";
    static final String COMMENT = COMMIT_TITLE + "\n";

    static GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat) {
        return genChangeSet(authorOrCommitter, useLegacyFormat, true);
    }

    static GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat, boolean hasParent) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Some header junk we should ignore...");
        lines.add("header line 2");
        lines.add("commit " + ID);
        lines.add("tree 789ghi012jkl");
        if (hasParent) {
            lines.add("parent " + PARENT);
        } else {
            lines.add("parent ");
        }
        lines.add("author " + AUTHOR_NAME + " <jauthor@nospam.com> " + AUTHOR_DATE);
        lines.add("committer " + COMMITTER_NAME + " <jcommitter@nospam.com> " + COMMITTER_DATE);
        lines.add("");
        lines.add("    " + COMMIT_TITLE);
        lines.add("    Commit extended description.");
        lines.add("");
        if (useLegacyFormat) {
            lines.add("123abc456def");
            lines.add(" create mode 100644 some/file1");
            lines.add(" delete mode 100644 other/file2");
        }
        lines.add(":000000 123456 0000000000000000000000000000000000000000 123abc456def789abc012def345abc678def901a A\tsrc/test/add.file");
        lines.add(":123456 000000 123abc456def789abc012def345abc678def901a 0000000000000000000000000000000000000000 D\tsrc/test/deleted.file");
        lines.add(":123456 789012 123abc456def789abc012def345abc678def901a bc234def567abc890def123abc456def789abc01 M\tsrc/test/modified.file");
        lines.add(":123456 789012 123abc456def789abc012def345abc678def901a bc234def567abc890def123abc456def789abc01 R012\tsrc/test/renamedFrom.file\tsrc/test/renamedTo.file");
        lines.add(":000000 123456 bc234def567abc890def123abc456def789abc01 123abc456def789abc012def345abc678def901a C100\tsrc/test/original.file\tsrc/test/copyOf.file");
        return new GitChangeSet(lines, authorOrCommitter);
    }

    static void assertChangeSet(GitChangeSet changeSet) {
        TestCase.assertEquals("123abc456def", changeSet.getId());
        TestCase.assertEquals("Commit title.", changeSet.getMsg());
        TestCase.assertEquals("Commit title.\nCommit extended description.\n", changeSet.getComment());
        TestCase.assertEquals("Commit title.\nCommit extended description.\n".replace("\n", "<br>"), changeSet.getCommentAnnotated());
        HashSet<String> expectedAffectedPaths = new HashSet<>(7);
        expectedAffectedPaths.add("src/test/add.file");
        expectedAffectedPaths.add("src/test/deleted.file");
        expectedAffectedPaths.add("src/test/modified.file");
        expectedAffectedPaths.add("src/test/renamedFrom.file");
        expectedAffectedPaths.add("src/test/renamedTo.file");
        expectedAffectedPaths.add("src/test/copyOf.file");
        TestCase.assertEquals(expectedAffectedPaths, changeSet.getAffectedPaths());
        Collection<GitChangeSet.Path> actualPaths = changeSet.getPaths();
        TestCase.assertEquals(6, actualPaths.size());
        for (GitChangeSet.Path path : actualPaths) {
            if ("src/test/add.file".equals(path.getPath())) {
                TestCase.assertEquals(EditType.ADD, path.getEditType());
                TestCase.assertNull(path.getSrc());
                TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
            } else if ("src/test/deleted.file".equals(path.getPath())) {
                TestCase.assertEquals(EditType.DELETE, path.getEditType());
                TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                TestCase.assertNull(path.getDst());
            } else if ("src/test/modified.file".equals(path.getPath())) {
                TestCase.assertEquals(EditType.EDIT, path.getEditType());
                TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                TestCase.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/renamedFrom.file".equals(path.getPath())) {
                TestCase.assertEquals(EditType.DELETE, path.getEditType());
                TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                TestCase.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/renamedTo.file".equals(path.getPath())) {
                TestCase.assertEquals(EditType.ADD, path.getEditType());
                TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                TestCase.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/copyOf.file".equals(path.getPath())) {
                TestCase.assertEquals(EditType.ADD, path.getEditType());
                TestCase.assertEquals("bc234def567abc890def123abc456def789abc01", path.getSrc());
                TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
            } else {
                TestCase.fail("Unrecognized path.");
            }
        }
    }

}
