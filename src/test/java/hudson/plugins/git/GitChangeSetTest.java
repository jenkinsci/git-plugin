package hudson.plugins.git;

import hudson.Functions;
import hudson.model.User;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.tasks.Mailer;
import hudson.tasks.Mailer.UserProperty;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.jvnet.hudson.test.HudsonTestCase;

import junit.framework.Assert;

public class GitChangeSetTest extends HudsonTestCase {

    @Override
    protected void tearDown() throws Exception {
        try { //Avoid test failures due to failed cleanup tasks
            super.tearDown();
        } catch (Exception e) {
            if (e instanceof IOException && Functions.isWindows()) {
                return;
            }
            e.printStackTrace();
        }
    }
    
    public GitChangeSetTest(String testName) {
        super(testName);
    }

    private GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("Some header junk we should ignore...");
        lines.add("header line 2");
        lines.add("commit 123abc456def");
        lines.add("tree 789ghi012jkl");
        lines.add("parent 345mno678pqr");
        lines.add("author John Author <jauthor@nospam.com> 1234567 -0600");
        lines.add("committer John Committer <jcommitter@nospam.com> 1234567 -0600");
        lines.add("");
        lines.add("    Commit title.");
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
    
    public void testLegacyChangeSet() {
        GitChangeSet changeSet = genChangeSet(false, true);
        assertChangeSet(changeSet);
    }
    
    public void testChangeSet() {
    	GitChangeSet changeSet = genChangeSet(false, false);
    	assertChangeSet(changeSet);
    }

	private void assertChangeSet(GitChangeSet changeSet) {
		Assert.assertEquals("123abc456def", changeSet.getId());
        Assert.assertEquals("Commit title.", changeSet.getMsg());
        Assert.assertEquals("Commit title.\nCommit extended description.\n", changeSet.getComment());
        HashSet<String> expectedAffectedPaths = new HashSet<String>(7);
        expectedAffectedPaths.add("src/test/add.file");
        expectedAffectedPaths.add("src/test/deleted.file");
        expectedAffectedPaths.add("src/test/modified.file");
        expectedAffectedPaths.add("src/test/renamedFrom.file");
        expectedAffectedPaths.add("src/test/renamedTo.file");
        expectedAffectedPaths.add("src/test/copyOf.file");
        Assert.assertEquals(expectedAffectedPaths, changeSet.getAffectedPaths());

        Collection<Path> actualPaths = changeSet.getPaths();
        Assert.assertEquals(6, actualPaths.size());
        for (Path path : actualPaths) {
            if ("src/test/add.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.ADD, path.getEditType());
                Assert.assertNull(path.getSrc());
                Assert.assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
            } else if ("src/test/deleted.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.DELETE, path.getEditType());
                Assert.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                Assert.assertNull(path.getDst());
            } else if ("src/test/modified.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.EDIT, path.getEditType());
                Assert.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                Assert.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/renamedFrom.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.DELETE, path.getEditType());
                Assert.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                Assert.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/renamedTo.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.ADD, path.getEditType());
                Assert.assertEquals("123abc456def789abc012def345abc678def901a", path.getSrc());
                Assert.assertEquals("bc234def567abc890def123abc456def789abc01", path.getDst());
            } else if ("src/test/copyOf.file".equals(path.getPath())) {
                Assert.assertEquals(EditType.ADD, path.getEditType());
                Assert.assertEquals("bc234def567abc890def123abc456def789abc01", path.getSrc());
                Assert.assertEquals("123abc456def789abc012def345abc678def901a", path.getDst());
            } else {
                Assert.fail("Unrecognized path.");
            }
        }
	}

    public void testAuthorOrCommitter() {
        GitChangeSet committerCS = genChangeSet(false, false);

        Assert.assertEquals("John Committer", committerCS.getAuthorName());

        GitChangeSet authorCS = genChangeSet(true, false);

        Assert.assertEquals("John Author", authorCS.getAuthorName());
    }
    
    public void testFindOrCreateUser() {
    	GitChangeSet committerCS = genChangeSet(false, false);
    	String csAuthor = "John Author";
		String csAuthorEmail = "jauthor@nospam.com";
		boolean createAccountBasedOnEmail = true;
		
		User user = committerCS.findOrCreateUser(csAuthor, csAuthorEmail, createAccountBasedOnEmail);
		Assert.assertNotNull(user);
		
		UserProperty property = user.getProperty(Mailer.UserProperty.class);
		Assert.assertNotNull(property);
		
		String address = property.getAddress();
		Assert.assertNotNull(address);
		Assert.assertEquals(csAuthorEmail, address);
    }

    private GitChangeSet genChangeSetForSwedCase(boolean authorOrCommitter) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("commit 1567861636cd854f4dd6fa40bf94c0c657681dd5");
        lines.add("tree 66236cf9a1ac0c589172b450ed01f019a5697c49");
        lines.add("parent e74a24e995305bd67a180f0ebc57927e2b8783ce");
        lines.add("author mistera <mister.ahlander@ericsson.com> 1363879004 +0100");
        lines.add("committer Mister Åhlander <mister.ahlander@ericsson.com> 1364199539 -0400");
        lines.add("");
        lines.add("    [task] Updated version.");
        lines.add("    ");
        lines.add("    Including earlier updates.");
        lines.add("    ");
        lines.add("    Changes in this version:");
        lines.add("    - Changed to take the gerrit url from gerrit query command.");
        lines.add("    - Aligned reason information with our new commit hooks");
        lines.add("    ");
        lines.add("    Change-Id: Ife96d2abed5b066d9620034bec5f04cf74b8c66d");
        lines.add("    Reviewed-on: https://gerrit.e.se/12345");
        lines.add("    Tested-by: Jenkins <jenkins@no-mail.com>");
        lines.add("    Reviewed-by: Mister Another <mister.another@ericsson.com>");
        lines.add("");
        //above lines all on purpose vs specific troublesome case @ericsson.
        return new GitChangeSet(lines, authorOrCommitter);
    }

    public void testAuthorOrCommitterSwedCase() {
        GitChangeSet committerCS = genChangeSetForSwedCase(false);

        Assert.assertEquals("Mister Åhlander", committerCS.getAuthorName());//swedish char on purpose

        GitChangeSet authorCS = genChangeSetForSwedCase(true);

        Assert.assertEquals("mistera", authorCS.getAuthorName());
    }
}
