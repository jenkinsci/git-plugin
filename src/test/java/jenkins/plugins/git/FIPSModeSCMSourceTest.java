package jenkins.plugins.git;

import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import jenkins.security.FIPS140;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RealJenkinsRule;

import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class FIPSModeSCMSourceTest {

    @Rule public RealJenkinsRule rule = new RealJenkinsRule().omitPlugins("eddsa-api", "trilead-api", "git-tag-message")
            .javaOptions("-Djenkins.security.FIPS140.COMPLIANCE=true");

    @Rule
    public LoggerRule logger = new LoggerRule();

    @BeforeClass
    public static void checkJenkinsVersion() {
        /* TODO: Remove when failing tests are fixed */
        /* JenkinsRule throws an exception before any test method is executed */
        /* Guess the version number from the Maven command line property */
        /* Default version number copied from pom.xml jenkins.version */
        VersionNumber jenkinsFailsTests = new VersionNumber("2.461");
        VersionNumber jenkinsVersion = new VersionNumber(System.getProperty("jenkins.version", "2.440.3"));
        /** Skip tests to avoid delaying plugin BOM and Spring Security 6.x Upgrade */
        boolean skipTests = false;
        if (jenkinsVersion.isNewerThanOrEqualTo(jenkinsFailsTests)) {
            skipTests = true;
        }
        Assume.assumeFalse(skipTests);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void remotesAreNotFetchedTest() throws Throwable {

        rule.then( r -> {
            GitSCMSource source = new GitSCMSource("http://insecure-repo");
            // Credentials are null, so we should have no FIPS error
            logger.record(AbstractGitSCMSource.class, Level.SEVERE);
            logger.capture(10);
            TaskListener listener = StreamTaskListener.fromStderr();
            assertThrows("expected exception as repo doesn't exist", GitException.class, () -> source.fetch(listener));
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
        });
    }
}
