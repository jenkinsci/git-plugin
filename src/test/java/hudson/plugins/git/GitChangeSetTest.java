package hudson.plugins.git;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import hudson.model.User;
import hudson.tasks.Mailer;
import hudson.tasks.Mailer.UserProperty;
import java.io.IOException;
import java.util.Collections;
import java.util.Random;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.springframework.security.authentication.DisabledException;

public class GitChangeSetTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private final Random random = new Random();

    @Test
    public void testFindOrCreateUser() {
        final GitChangeSet committerCS = GitChangeSetUtil.genChangeSet(false, false);
        final String email = "jauthor@nospam.com";
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = false;

        User user = committerCS.findOrCreateUser(
                GitChangeSetUtil.AUTHOR_NAME, email, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertNotNull(user);

        UserProperty property = user.getProperty(Mailer.UserProperty.class);
        assertNotNull(property);

        String address = property.getAddress();
        assertNotNull(address);
        assertEquals(email, address);

        assertEquals(
                User.getUnknown(), committerCS.findOrCreateUser(null, email, false, useExistingAccountBasedOnEmail));
        assertEquals(
                User.getUnknown(), committerCS.findOrCreateUser(null, email, true, useExistingAccountBasedOnEmail));
    }

    @Test
    public void testFindOrCreateUserNullAuthorEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = random.nextBoolean();
        final String csAuthor = "ada";
        final String csAuthorEmail = null;
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user, is(User.getUnknown()));
    }

    @Test
    public void testFindOrCreateUserEmptyAuthorEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = random.nextBoolean();
        final String csAuthor = "babbage";
        final String csAuthorEmail = "";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user, is(User.getUnknown()));
    }

    @Test
    public void testFindOrCreateUserEmptyAuthorEmailDoNotCreateAccountBasedOnEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = false;
        final boolean useExistingAccountBasedOnEmail = random.nextBoolean();
        final String csAuthor = "babbage-do-not-create";
        final String csAuthorEmail = "";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user, is(User.getUnknown()));
    }

    @Test
    public void testFindOrCreateUserEmptyAuthorDoNotCreateAccountBasedOnEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = false;
        final boolean useExistingAccountBasedOnEmail = random.nextBoolean();
        final String csAuthor = "";
        final String csAuthorEmail = "babbage-empty-author@example.com";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user, is(User.getUnknown()));
    }

    @Test
    public void testFindOrCreateUserBadAuthorEmailDoNotCreateAccountBasedOnEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = false;
        final boolean useExistingAccountBasedOnEmail = random.nextBoolean();
        final String csAuthor = "babbage-do-not-create";
        final String csAuthorEmail = "@";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user, is(User.getUnknown()));
    }

    @Test
    public void testFindOrCreateUserOKAuthorEmailDoNotCreateAccountBasedOnEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = false;
        final boolean useExistingAccountBasedOnEmail = random.nextBoolean();
        final String csAuthor = "babbage-will-be-created";
        final String csAuthorEmail = csAuthor + "@";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user.getFullName(), is(csAuthor));
    }

    @Test
    public void testFindOrCreateUserBlankAuthorEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = false;
        final String csAuthor = "candide";
        final String csAuthorEmail = " ";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user.getFullName(), is(csAuthor));
    }

    @Test
    public void testFindOrCreateUserBlankAuthorEmailUseExistingAccountBasedOnEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = true;
        final String csAuthor = "cosimo";
        final String csAuthorEmail = " ";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user.getFullName(), is(csAuthor));
    }

    @Test
    public void testFindOrCreateUserValidAuthorEmailUseExistingAccountBasedOnEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = true;
        final String csAuthor = "dante";
        final String csAuthorEmail = "alighieri@example.com";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user.getFullName(), is(csAuthor));
    }

    @Test
    public void testFindOrCreateUserUseExistingAuthorEmailUseExistingAccountBasedOnEmail() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = true;
        final String csAuthor = "ecco";
        final String csAuthorEmail = "umberto@example.com";
        User user = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(user.getFullName(), is(csAuthor));
        /* Confirm that second search returns user created by first search */
        User existing = changeset.findOrCreateUser(
                csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertThat(existing, is(user));
    }

    @Test
    public void testFindOrCreateUserHandlesAuthenticationException() {
        final GitChangeSet changeset = GitChangeSetUtil.genChangeSet(random.nextBoolean(), random.nextBoolean());
        // TODO this only test one code path, there are several code paths using User.get()
        final boolean createAccountBasedOnEmail = false;
        final boolean useExistingAccountBasedOnEmail = true;
        final String csAuthor = "disabled_user";
        final String csAuthorEmail = "disabled_user@example.com";

        try (MockedStatic<User> user = mockStatic(User.class)) {
            user.when(() -> User.get("disabled_user", createAccountBasedOnEmail, Collections.emptyMap()))
                    .thenThrow(new DisabledException("The user \"disabled_user\" is administratively disabled"));

            User actual = changeset.findOrCreateUser(
                    csAuthor, csAuthorEmail, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
            assertEquals(User.getUnknown(), actual);

            user.verify(() -> User.get("disabled_user", createAccountBasedOnEmail, Collections.emptyMap()), times(1));
            user.verify(User::getUnknown, times(2));
        }
    }

    @Test
    @Deprecated // Test deprecated User.get()
    public void testFindOrCreateUserBasedOnExistingUsersEmail() throws IOException {
        final GitChangeSet committerCS = GitChangeSetUtil.genChangeSet(true, false);
        final String existingUserId = "An existing user";
        final String existingUserFullName = "Some FullName";
        final String email = "jcommitter@nospam.com";
        final boolean createAccountBasedOnEmail = true;
        final boolean useExistingAccountBasedOnEmail = true;

        assertNull(User.get(email, false));

        User existingUser = User.get(existingUserId, true);
        assertThat(existingUser, is(not(nullValue())));
        existingUser.setFullName(existingUserFullName);
        existingUser.addProperty(new Mailer.UserProperty(email));

        User user = committerCS.findOrCreateUser(
                GitChangeSetUtil.COMMITTER_NAME, email, createAccountBasedOnEmail, useExistingAccountBasedOnEmail);
        assertNotNull(user);
        assertEquals(user.getId(), existingUserId);
        assertEquals(user.getFullName(), existingUserFullName);

        UserProperty property = user.getProperty(Mailer.UserProperty.class);
        assertNotNull(property);

        String address = property.getAddress();
        assertNotNull(address);
        assertEquals(email, address);

        assertEquals(
                User.getUnknown(), committerCS.findOrCreateUser(null, email, false, useExistingAccountBasedOnEmail));
        assertEquals(
                User.getUnknown(), committerCS.findOrCreateUser(null, email, true, useExistingAccountBasedOnEmail));
    }

    @Test
    @Deprecated // Testing deprecated User.get
    public void findOrCreateByFullName() throws Exception {
        GitChangeSet cs = GitChangeSetUtil.genChangeSet(false, false);
        User user = User.get("john");
        user.setFullName(GitChangeSetUtil.COMMITTER_NAME);
        user.addProperty(new Mailer.UserProperty(GitChangeSetUtil.COMMITTER_EMAIL));
        assertEquals(user, cs.getAuthor());
    }
}
