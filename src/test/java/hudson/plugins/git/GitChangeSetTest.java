package hudson.plugins.git;

import hudson.model.User;
import hudson.tasks.Mailer;
import hudson.tasks.Mailer.UserProperty;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitChangeSetTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testFindOrCreateUser() {
        final GitChangeSet committerCS = GitChangeSetUtil.genChangeSet(false, false);
        final String email = "jauthor@nospam.com";
        final boolean createAccountBasedOnEmail = true;

        User user = committerCS.findOrCreateUser(GitChangeSetUtil.AUTHOR_NAME, email, createAccountBasedOnEmail);
        assertNotNull(user);

        UserProperty property = user.getProperty(Mailer.UserProperty.class);
        assertNotNull(property);

        String address = property.getAddress();
        assertNotNull(address);
        assertEquals(email, address);

        assertEquals(User.getUnknown(), committerCS.findOrCreateUser(null, email, false));
        assertEquals(User.getUnknown(), committerCS.findOrCreateUser(null, email, true));
    }

    @Test
    public void findOrCreateByFullName() throws Exception {
        GitChangeSet cs = GitChangeSetUtil.genChangeSet(false, false);
        User user = User.get("john");
        user.setFullName(GitChangeSetUtil.COMMITTER_NAME);
        user.addProperty(new Mailer.UserProperty(GitChangeSetUtil.COMMITTER_EMAIL));
        assertEquals(user, cs.getAuthor());
    }

}
