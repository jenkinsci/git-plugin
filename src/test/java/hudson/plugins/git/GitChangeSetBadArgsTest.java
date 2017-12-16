package hudson.plugins.git;

import java.util.ArrayList;

import hudson.model.User;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;

import org.jvnet.hudson.test.JenkinsRule;

public class GitChangeSetBadArgsTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private GitChangeSet createChangeSet(boolean authorOrCommitter, String name, String email) {
        String dataSource = authorOrCommitter ? "Author" : "Committer";
        ArrayList<String> lines = new ArrayList<>();
        lines.add("commit 1567861636cd854f4dd6fa40bf94c0c657681dd5");
        lines.add("tree 66236cf9a1ac0c589172b450ed01f019a5697c49");
        lines.add("parent e74a24e995305bd67a180f0ebc57927e2b8783ce");
        if (authorOrCommitter) {
            lines.add("author " + name + " <" + email + "> 1363879004 +0100");
            lines.add("committer Good Committer <good.committer@example.com> 1364199539 -0400");
        } else {
            lines.add("author Good Author <good.author@example.com> 1363879004 +0100");
            lines.add("committer " + name + " <" + email + "> 1364199539 -0400");
        }
        lines.add("");
        lines.add("    " + dataSource + " has e-mail address '" + email + "' and name '" + name + "'.");
        lines.add("    ");
        lines.add("    Changes in this version:");
        lines.add("    - " + dataSource + " mutated e-mail address and name.");
        lines.add("    ");
        lines.add("");
        return new GitChangeSet(lines, authorOrCommitter);
    }

    private GitChangeSet createAuthorChangeSet(String authorName, String authorEmail) {
        return createChangeSet(true, authorName, authorEmail);
    }

    private GitChangeSet createCommitterChangeSet(String committerName, String committerEmail) {
        return createChangeSet(false, committerName, committerEmail);
    }

    private static final String DEGENERATE_EMAIL_ADDRESS = "@";

    @Test
    public void testFindOrCreateUserAuthorBadEmail() {
        String authorName = "Bad Author Test 1";
        GitChangeSet changeSet = createAuthorChangeSet(authorName, DEGENERATE_EMAIL_ADDRESS);
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(authorName, DEGENERATE_EMAIL_ADDRESS, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(null, DEGENERATE_EMAIL_ADDRESS, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser("", DEGENERATE_EMAIL_ADDRESS, false));
    }

    @Test
    public void testFindOrCreateUserCommitterBadEmail() {
        String committerName = "Bad Committer Test 2";
        GitChangeSet changeSet = createCommitterChangeSet(committerName, DEGENERATE_EMAIL_ADDRESS);
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(committerName, DEGENERATE_EMAIL_ADDRESS, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(null, DEGENERATE_EMAIL_ADDRESS, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser("", DEGENERATE_EMAIL_ADDRESS, false));
    }

    @Test
    public void testFindOrCreateUserEmptyAuthor() {
        String emptyAuthorName = "";
        String incompleteAuthorEmail = "@test3.example.com";
        GitChangeSet changeSet = createAuthorChangeSet(emptyAuthorName, incompleteAuthorEmail);
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(emptyAuthorName, incompleteAuthorEmail, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(null, incompleteAuthorEmail, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser("", incompleteAuthorEmail, false));
    }

    @Test
    public void testFindOrCreateEmptyCommitter() {
        String emptyCommitterName = "";
        String incompleteCommitterEmail = "@test4.example.com";
        GitChangeSet changeSet = createCommitterChangeSet(emptyCommitterName, incompleteCommitterEmail);
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(emptyCommitterName, incompleteCommitterEmail, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(null, incompleteCommitterEmail, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser("", incompleteCommitterEmail, false));
    }

    @Test
    public void testFindOrCreateUserEmptyAuthorEmail() {
        String authorName = "Author Test 5";
        String emptyAuthorEmail = "";
        GitChangeSet changeSet = createAuthorChangeSet(authorName, emptyAuthorEmail);
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(authorName, emptyAuthorEmail, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(authorName, emptyAuthorEmail, true));
    }

    @Test
    public void testFindOrCreateUserNullAuthorEmail() {
        String authorName = "Author Test 6";
        String emptyAuthorEmail = "";
        GitChangeSet changeSet = createAuthorChangeSet(authorName, emptyAuthorEmail);
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(authorName, null, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(authorName, null, true));
    }

    @Test
    public void testFindOrCreateUserEmptyCommitterEmail() {
        String committerName = "Committer Test 7";
        String emptyCommitterEmail = "";
        GitChangeSet changeSet = createCommitterChangeSet(committerName, emptyCommitterEmail);
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(committerName, emptyCommitterEmail, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(committerName, emptyCommitterEmail, true));
    }

    @Test
    public void testFindOrCreateUserNullCommitterEmail() {
        String committerName = "Committer Test 8";
        String emptyCommitterEmail = "";
        GitChangeSet changeSet = createCommitterChangeSet(committerName, emptyCommitterEmail);
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(committerName, null, false));
        assertEquals(User.getUnknown(), changeSet.findOrCreateUser(committerName, null, true));
    }
}
