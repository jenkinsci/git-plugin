package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
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
        GitChangeSet gitChangeSet = GitChangeSetUtil.genChangeSet(false, true, false, GitChangeSetUtil.COMMIT_TITLE ,false);
        GitChangeSetUtil.assertChangeSet( gitChangeSet );
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
        assertEquals(GitChangeSetUtil.COMMITTER_EMAIL, genChangeSet(false, false).getAuthorEmail());
    }

    @Test
    public void testAuthor() {
        assertEquals(GitChangeSetUtil.AUTHOR_NAME, genChangeSet(true, false).getAuthorName());
        assertEquals(GitChangeSetUtil.AUTHOR_EMAIL, genChangeSet(true, false).getAuthorEmail());
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
        ArrayList<String> lines = new ArrayList<>();
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

    @Test
    public void testChangeLogTruncationWithShortMessage(){
        GitChangeSet changeSet = GitChangeSetUtil.genChangeSet(true, false, true,
                "Lorem ipsum dolor sit amet.",
                false);
        String msg = changeSet.getMsg();
        assertThat("Title is correct ", msg, containsString("Lorem ipsum dolor sit amet.") );
        assertThat("Title length is correct ", msg.length(), lessThanOrEqualTo(GitChangeSet.TRUNCATE_LIMIT));
    }
    @Test
    public void testChangeLogTruncationWithNewLine(){
        GitChangeSet changeSet = GitChangeSetUtil.genChangeSet(true, false, true,
                "Lorem ipsum dolor sit amet, "+System.lineSeparator()+"consectetur adipiscing elit.",
                false);
        String msg = changeSet.getMsg();
        assertThat(msg, is("Lorem ipsum dolor sit amet,"));
        assertThat("Title length is correct ", msg.length(), lessThanOrEqualTo(GitChangeSet.TRUNCATE_LIMIT));
    }

    @Test
    public void testChangeLogRetainSummaryWithoutNewLine(){
        String originalCommitMessage = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus pellentesque ipsum non aliquam interdum. Integer metus orci, vulputate id turpis in, pharetra pretium magna. Fusce sollicitudin vehicula lectus. Nam ut eros purus. Mauris aliquam mi et nunc porta, non consectetur mauris pretium. Fusce a venenatis dolor. Sed commodo, dui ac posuere dignissim, dolor tortor semper eros, varius consequat nulla purus a lacus. Vestibulum egestas, orci vitae pellentesque laoreet, dolor lorem molestie tellus, nec luctus lorem ex quis orci. Phasellus interdum elementum luctus. Nam commodo, turpis in sollicitudin auctor, ipsum lectus finibus erat, in iaculis sapien neque ultrices sapien. In congue diam semper tortor laoreet aliquet. Mauris lacinia quis nunc vel accumsan. Nullam sed nisl eget orci porttitor venenatis. Lorem ipsum dolor sit amet, consectetur adipiscing elit";
        GitChangeSet changeSet = GitChangeSetUtil.genChangeSet(true, false, true,
                originalCommitMessage,
                true);
        assertThat(changeSet.getMsg(), is(originalCommitMessage));
    }

    @Test
    public void testChangeLogDoNotRetainSummaryWithoutNewLine(){
        String msg = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus pellentesque ipsum non aliquam interdum. Integer metus orci, vulputate id turpis in, pharetra pretium magna. Fusce sollicitudin vehicula lectus. Nam ut eros purus. Mauris aliquam mi et nunc porta, non consectetur mauris pretium. Fusce a venenatis dolor. Sed commodo, dui ac posuere dignissim, dolor tortor semper eros, varius consequat nulla purus a lacus. Vestibulum egestas, orci vitae pellentesque laoreet, dolor lorem molestie tellus, nec luctus lorem ex quis orci. Phasellus interdum elementum luctus. Nam commodo, turpis in sollicitudin auctor, ipsum lectus finibus erat, in iaculis sapien neque ultrices sapien. In congue diam semper tortor laoreet aliquet. Mauris lacinia quis nunc vel accumsan. Nullam sed nisl eget orci porttitor venenatis. Lorem ipsum dolor sit amet, consectetur adipiscing elit";
        GitChangeSet changeSet = GitChangeSetUtil.genChangeSet(true, false, true,
                msg,
                false);
        assertThat(changeSet.getMsg(), is("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus"));

    }

    @Test
    public void testChangeLogNoTruncationWithNewLine(){
        GitChangeSet changeSet = GitChangeSetUtil.genChangeSet(true, false, true,
                "Lorem ipsum dolor sit amet, consectetur "+System.lineSeparator()+" adipiscing elit. Phasellus pellentesque ipsum non aliquam interdum. Integer metus orci, vulputate id turpis in, pharetra pretium magna. Fusce sollicitudin vehicula lectus. Nam ut eros purus. Mauris aliquam mi et nunc porta, non consectetur mauris pretium. Fusce a venenatis dolor. Sed commodo, dui ac posuere dignissim, dolor tortor semper eros, varius consequat nulla purus a lacus. Vestibulum egestas, orci vitae pellentesque laoreet, dolor lorem molestie tellus, nec luctus lorem ex quis orci. Phasellus interdum elementum luctus. Nam commodo, turpis in sollicitudin auctor, ipsum lectus finibus erat, in iaculis sapien neque ultrices sapien. In congue diam semper tortor laoreet aliquet. Mauris lacinia quis nunc vel accumsan. Nullam sed nisl eget orci porttitor venenatis. Lorem ipsum dolor sit amet, consectetur adipiscing elit",
                true);
        String msg = changeSet.getMsg();
        assertThat("Title is correct ", msg, is("Lorem ipsum dolor sit amet, consectetur") );

    }

    @Test
    public void stringSplitter(){
        String msg = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus pellentesque ipsum non aliquam interdum. Integer metus orci, vulputate id turpis in, pharetra pretium magna. Fusce sollicitudin vehicula lectus. Nam ut eros purus. Mauris aliquam mi et nunc porta, non consectetur mauris pretium. Fusce a venenatis dolor. Sed commodo, dui ac posuere dignissim, dolor tortor semper eros, varius consequat nulla purus a lacus. Vestibulum egestas, orci vitae pellentesque laoreet, dolor lorem molestie tellus, nec luctus lorem ex quis orci. Phasellus interdum elementum luctus. Nam commodo, turpis in sollicitudin auctor, ipsum lectus finibus erat, in iaculis sapien neque ultrices sapien. In congue diam semper tortor laoreet aliquet. Mauris lacinia quis nunc vel accumsan. Nullam sed nisl eget orci porttitor venenatis. Lorem ipsum dolor sit amet, consectetur adipiscing elit";
        assertThat(GitChangeSet.splitString(msg, 15), is("Lorem ipsum"));
        assertThat(GitChangeSet.splitString(msg, 16), is("Lorem ipsum dolor"));
        assertThat(GitChangeSet.splitString(msg, 17), is("Lorem ipsum dolor"));
        assertThat(GitChangeSet.splitString(msg, 18), is("Lorem ipsum dolor"));
        assertThat(GitChangeSet.splitString(msg, 19), is("Lorem ipsum dolor"));
        assertThat(GitChangeSet.splitString(msg, 20), is("Lorem ipsum dolor sit"));
        assertThat(GitChangeSet.splitString(msg, 21), is("Lorem ipsum dolor sit"));
        assertThat(GitChangeSet.splitString(msg, 22), is("Lorem ipsum dolor sit"));

        msg = "Lorem ipsum dolor sit amet, " + System.lineSeparator() + "consectetur adipiscing elit. Phasellus pellentesque ipsum non aliquam interdum.";
        assertThat(GitChangeSet.splitString(msg, GitChangeSet.TRUNCATE_LIMIT), is("Lorem ipsum dolor sit amet,"));


    }

    @Test
    public void splitingWithBrackets(){
        assertThat(GitChangeSet.splitString("[task] Lorem ipsum dolor sit amet, consectetur adipiscing elit.", 25), is("[task] Lorem ipsum dolor"));

    }

}

