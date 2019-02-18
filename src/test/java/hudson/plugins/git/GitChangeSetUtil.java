package hudson.plugins.git;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.scm.EditType;
import hudson.util.StreamTaskListener;

import org.eclipse.jgit.lib.ObjectId;

import java.io.File;
import java.io.IOException;
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
    static final String AUTHOR_EMAIL = "jauthor@nospam.com";
    static final String COMMITTER_NAME = "John Committer";
    static final String COMMITTER_DATE = "1234566 -0600";
    static final String COMMITTER_DATE_FORMATTED = "1970-01-15T06:56:06-0600";
    static final String COMMIT_TITLE = "Commit title.";
    static final String COMMENT = COMMIT_TITLE + "\n";
    static final String COMMITTER_EMAIL = "jcommitter@nospam.com";

    static GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat) {
        return genChangeSet(authorOrCommitter, useLegacyFormat, true);
    }

    public static GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat, boolean hasParent) {
        return genChangeSet(authorOrCommitter, useLegacyFormat, hasParent, COMMIT_TITLE);
    }

    public static GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat, boolean hasParent, String commitTitle) {
       return genChangeSet(authorOrCommitter, useLegacyFormat, hasParent, commitTitle, false);
    }

    public static GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat, boolean hasParent, String commitTitle, boolean truncate) {
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
        lines.add("author " + AUTHOR_NAME + " <" + AUTHOR_EMAIL + "> " + AUTHOR_DATE);
        lines.add("committer " + COMMITTER_NAME + " <" + COMMITTER_EMAIL + "> " + COMMITTER_DATE);
        lines.add("");
        lines.add("    " + commitTitle);
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
        return new GitChangeSet(lines, authorOrCommitter, truncate);
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
            if (null != path.getPath()) switch (path.getPath()) {
                case "src/test/add.file":
                    TestCase.assertEquals(EditType.ADD, path.getEditType());
                    TestCase.assertNull(path.getSrc());
                    TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
                    break;
                case "src/test/deleted.file":
                    TestCase.assertEquals(EditType.DELETE, path.getEditType());
                    TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                    TestCase.assertNull(path.getDst());
                    break;
                case "src/test/modified.file":
                    TestCase.assertEquals(EditType.EDIT, path.getEditType());
                    TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                    TestCase.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
                    break;
                case "src/test/renamedFrom.file":
                    TestCase.assertEquals(EditType.DELETE, path.getEditType());
                    TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                    TestCase.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
                    break;
                case "src/test/renamedTo.file":
                    TestCase.assertEquals(EditType.ADD, path.getEditType());
                    TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                    TestCase.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
                    break;
                case "src/test/copyOf.file":
                    TestCase.assertEquals(EditType.ADD, path.getEditType());
                    TestCase.assertEquals("bc234def567abc890def123abc456def789abc01", path.getSrc());
                    TestCase.assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
                    break;
                default:
                    TestCase.fail("Unrecognized path.");
                    break;
            }
        }
    }

    public static GitChangeSet genChangeSet(ObjectId sha1, String gitImplementation, boolean authorOrCommitter) throws IOException, InterruptedException {
        EnvVars envVars = new EnvVars();
        TaskListener listener = StreamTaskListener.fromStdout();
        GitClient git = Git.with(listener, envVars).in(new FilePath(new File("."))).using(gitImplementation).getClient();
        return new GitChangeSet(git.showRevision(sha1), authorOrCommitter);
    }
}
