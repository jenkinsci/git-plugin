package jenkins.plugins.git;

import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.util.StreamTaskListener;
import jenkins.security.FIPS140;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.io.IOException;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class FIPSModeSCMSourceTest {

    @ClassRule
    public static final FlagRule<String> FIPS_FLAG =
            FlagRule.systemProperty(FIPS140.class.getName() + ".COMPLIANCE", "true");

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule();

    @Test
    @SuppressWarnings("deprecation")
    public void remotesAreNotFetchedTest() throws IOException, InterruptedException {
        GitSCMSource source = new GitSCMSource("http://insecure-repo");
        // Credentials are null, so we should have no FIPS error
        logger.record(AbstractGitSCMSource.class, Level.SEVERE);
        logger.capture(10);
        TaskListener listener = StreamTaskListener.fromStderr();
        assertThrows("expected exception as repo doesn't exist", GitException.class, () ->source.fetch(listener));
        assertThat("We should no see the error in the logs", logger.getMessages().size(), is(0));

        // Using creds we should be getting an exception
        Throwable exception = assertThrows("We're not saving creds", IllegalArgumentException.class, () -> source.setCredentialsId("cred-id"));
        assertThat(exception.getMessage(), containsString("FIPS requires a secure channel"));
        assertThat("credentials are not saved", source.getCredentialsId(), nullValue());

        // Using old constructor (restricted since 3.4.0) to simulate credentials are being set with unsecure connection
        // This would be equivalent to a user manually adding credentials to config.xml
        GitSCMSource anotherSource = new GitSCMSource("fake", "http://insecure", "credentialsId", "", "", true);
        exception = assertThrows("fetch was interrupted so no credential was leaked", IllegalArgumentException.class, () -> anotherSource.fetch(listener));
        assertThat("We should have a severe log indicating the error", logger.getMessages().size(), is(1));
        assertThat("Exception indicates problem", exception.getMessage(), containsString("FIPS requires a secure channel"));
    }
}
