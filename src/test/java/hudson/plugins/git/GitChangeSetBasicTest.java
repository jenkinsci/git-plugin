package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.Test;

public class GitChangeSetBasicTest {

    private GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat) {
        return GitChangeSetUtil.genChangeSet(authorOrCommitter, useLegacyFormat, true);
    }

    private GitChangeSet genChangeSet(boolean authorOrCommitter, boolean useLegacyFormat, boolean hasParent) {
        return GitChangeSetUtil.genChangeSet(authorOrCommitter, useLegacyFormat, hasParent);
    }

    @Test
    public void testLegacyChangeSet() {
        GitChangeSetUtil.assertChangeSet(genChangeSet(false, true));
    }

    @Test
    public void testChangeSet() {
        GitChangeSetUtil.assertChangeSet(genChangeSet(false, false));
    }

    @Test
    public void testChangeSetNoParent() {
        GitChangeSet changeSet = genChangeSet(false, false, false);
        GitChangeSetUtil.assertChangeSet(changeSet);
        assertNull(changeSet.getParentCommit());
    }

    @Test
    public void testCommitter() {
        assertEquals(GitChangeSetUtil.COMMITTER_NAME, genChangeSet(false, false).getAuthorName());
    }

    @Test
    public void testAuthor() {
        assertEquals(GitChangeSetUtil.AUTHOR_NAME, genChangeSet(true, false).getAuthorName());
    }

    @Test
    public void testGetDate() {
        assertEquals("1970-01-15T06:56:08-0600", genChangeSet(true, false).getDate());
    }

    @Test
    public void testGetTimestamp() {
        assertEquals(1256168000L, genChangeSet(true, false).getTimestamp());
    }

    @Test
    public void testInvalidDate() {
        final String badDateString = "2015-03-03x09:22:42 -0700";
        GitChangeSet c = new GitChangeSet(Arrays.asList("author John Doe <john.doe@jenkins-ci.org> " + badDateString), true);
        assertEquals(badDateString, c.getDate());
        assertEquals(-1L, c.getTimestamp());
    }

    @Test
    public void testIsoDate() {

        GitChangeSet c = new GitChangeSet(Arrays.asList("author John Doe <john.doe@jenkins-ci.org> 2015-03-03T09:22:42-0700"), true);
        assertEquals("2015-03-03T09:22:42-0700", c.getDate());
        assertEquals(1425399762000L, c.getTimestamp());

        c = new GitChangeSet(Arrays.asList("author John Doe <john.doe@jenkins-ci.org> 2015-03-03T09:22:42-07:00"), true);
        assertEquals("2015-03-03T09:22:42-07:00", c.getDate());
        assertEquals(1425399762000L, c.getTimestamp());

        c = new GitChangeSet(Arrays.asList("author John Doe <john.doe@jenkins-ci.org> 2015-03-03T16:22:42Z"), true);
        assertEquals("2015-03-03T16:22:42Z", c.getDate());
        assertEquals(1425399762000L, c.getTimestamp());

        c = new GitChangeSet(Arrays.asList("author John Doe <john.doe@jenkins-ci.org> 1425399762"), true);
        assertEquals("2015-03-03T16:22:42Z", c.getDate());
        assertEquals(1425399762000L, c.getTimestamp());

        c = new GitChangeSet(Arrays.asList("author John Doe <john.doe@jenkins-ci.org> 1425374562 -0700"), true);
        assertEquals("2015-03-03T09:22:42-0700", c.getDate());
        assertEquals(1425399762000L, c.getTimestamp());
    }

    private GitChangeSet genChangeSetForSwedCase(boolean authorOrCommitter) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("commit 1567861636cd854f4dd6fa40bf94c0c657681dd5");
        lines.add("tree 66236cf9a1ac0c589172b450ed01f019a5697c49");
        lines.add("parent e74a24e995305bd67a180f0ebc57927e2b8783ce");
        lines.add("author misterÅ <mister.ahlander@ericsson.com> 1363879004 +0100");
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

    @Test
    public void testSwedishCommitterName() {
        assertEquals("Mister Åhlander", genChangeSetForSwedCase(false).getAuthorName());
    }

    @Test
    public void testSwedishAuthorName() {
        assertEquals("misterÅ", genChangeSetForSwedCase(true).getAuthorName());
    }

    @Test
    public void testSwedishDate() {
        assertEquals("2013-03-21T15:16:44+0100", genChangeSetForSwedCase(true).getDate());
    }

    @Test
    public void testSwedishTimestamp() {
        assertEquals(1363875404000L, genChangeSetForSwedCase(true).getTimestamp());
    }
}
