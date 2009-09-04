package hudson.plugins.git;

import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import junit.framework.Assert;
import junit.framework.TestCase;

public class GitChangeSetTest extends TestCase {
    
    public GitChangeSetTest(String testName) {
        super(testName);
    }

    public void testChangeSet() {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("Some header junk we should ignore...");
        lines.add("header line 2");
        lines.add("commit 123abc456def");
        lines.add("tree 789ghi012jkl");
        lines.add("parent 345mno678pqr");
        lines.add("author John Author <jauthor@nospam.com> 1234567 -0600");
        lines.add("commiter John Committer <jcommitter@nospam.com> 1234567 -0600");
        lines.add("");
        lines.add("    Commit title.");
        lines.add("    Commit extended description.");
        lines.add("");
        lines.add("A\tsrc/test/add.file");
        lines.add("M\tsrc/test/modified.file");
        lines.add("D\tsrc/test/deleted.file");
        GitChangeSet changeSet = new GitChangeSet(lines);

        Assert.assertEquals("123abc456def", changeSet.getId());
        Assert.assertEquals("Commit title.", changeSet.getMsg());
        System.out.println("ACTUAL: " + changeSet.getComment());
        Assert.assertEquals("Commit title.\nCommit extended description.\n", changeSet.getComment());
        HashSet<String> expectedAffectedPaths = new HashSet<String>(7);
        expectedAffectedPaths.add("src/test/add.file");
        expectedAffectedPaths.add("src/test/modified.file");
        expectedAffectedPaths.add("src/test/deleted.file");
        Assert.assertEquals(expectedAffectedPaths, changeSet.getAffectedPaths());

        Collection<Path> actualPaths = changeSet.getPaths();
        for (Path path : actualPaths) {
            if ("src/test/add.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.ADD, path.getEditType());
            } else if ("src/test/modified.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.EDIT, path.getEditType());
            } else if ("src/test/deleted.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.DELETE, path.getEditType());
            } else {
                Assert.fail("Unrecognized path.");
            }
        }
    }

}
