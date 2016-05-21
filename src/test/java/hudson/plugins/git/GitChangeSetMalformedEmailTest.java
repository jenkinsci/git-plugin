package hudson.plugins.git;

import java.util.ArrayList;

import hudson.model.User;

import org.junit.Test;
import org.junit.Rule;
import static org.junit.Assert.*;

import org.jvnet.hudson.test.JenkinsRule;

public class GitChangeSetMalformedEmailTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private final String badEmail = "@";

    private GitChangeSet genChangeSetWithBadEmail(boolean authorOrCommitter) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("commit 1567861636cd854f4dd6fa40bf94c0c657681dd5");
        lines.add("tree 66236cf9a1ac0c589172b450ed01f019a5697c49");
        lines.add("parent e74a24e995305bd67a180f0ebc57927e2b8783ce");
        lines.add("author Bad Author <" + badEmail + "> 1363879004 +0100"); // Bad e-mail address
        lines.add("committer Bad Committer <" + badEmail + "> 1364199539 -0400"); // Bad e-mail address
        lines.add("");
        lines.add("    Committer and author have bad e-mail addresses.");
        lines.add("    ");
        lines.add("    Changes in this version:");
        lines.add("    - Committer has bad e-mail address.");
        lines.add("    - Author has bad e-mail address.");
        lines.add("    ");
        lines.add("");
        return new GitChangeSet(lines, authorOrCommitter);
    }

    @Test
    public void testFindOrCreateUserBadEmailAuthor() {
        assertEquals(User.getUnknown(), genChangeSetWithBadEmail(true).findOrCreateUser("Bad Author", badEmail, false));
    }

    @Test
    public void testFindOrCreateUserBadEmailCommitter() {
        assertEquals(User.getUnknown(), genChangeSetWithBadEmail(false).findOrCreateUser("Bad Committer", badEmail, false));
    }
}
