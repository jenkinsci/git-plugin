package hudson.plugins.git;

import hudson.model.User;
import hudson.tasks.Mailer;
import hudson.tasks.Mailer.UserProperty;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

public class GitChangeSetTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testFindOrCreateUser() {
        final GitChangeSet committerCS = GitChangeSetUtil.genChangeSet(false, false);
        final String email = "jauthor@nospam.com";
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = false;

        User user = committerCS.findOrCreateUser(GitChangeSetUtil.AUTHOR_NAME, email, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertNotNull(user);

        UserProperty property = user.getProperty(Mailer.UserProperty.class);
        assertNotNull(property);

        String address = property.getAddress();
        assertNotNull(address);
        assertEquals(email, address);

        assertEquals(User.getUnknown(), committerCS.findOrCreateUser(null, email, false, useExistingAccountBasedOnEmail));
        assertEquals(User.getUnknown(), committerCS.findOrCreateUser(null, email, true, useExistingAccountBasedOnEmail));
    }

    @Test
    public void testFindOrCreateUserBasedOnExistingUsersEmail() throws IOException {
        final GitChangeSet committerCS = GitChangeSetUtil.genChangeSet(true, false);
        final String existingUserId = "An existing user";
        final String existingUserFullName = "Some FullName";
        final String email = "jcommitter@nospam.com";
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = true;

        assertNull(User.get(email, false));

        User existingUser = User.get(existingUserId, true);
        existingUser.setFullName(existingUserFullName);
        existingUser.addProperty(new Mailer.UserProperty(email));

        User user = committerCS.findOrCreateUser(GitChangeSetUtil.COMMITTER_NAME, email, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertNotNull(user);
        assertEquals(user.getId(), existingUserId);
        assertEquals(user.getFullName(), existingUserFullName);

        UserProperty property = user.getProperty(Mailer.UserProperty.class);
        assertNotNull(property);

        String address = property.getAddress();
        assertNotNull(address);
        assertEquals(email, address);

        assertEquals(User.getUnknown(), committerCS.findOrCreateUser(null, email, false, useExistingAccountBasedOnEmail));
        assertEquals(User.getUnknown(), committerCS.findOrCreateUser(null, email, true, useExistingAccountBasedOnEmail));
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
