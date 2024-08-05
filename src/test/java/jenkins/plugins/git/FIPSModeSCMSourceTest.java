package jenkins.plugins.git;

import hudson.logging.LogRecorder;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.util.StreamTaskListener;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RealJenkinsRule;

import java.util.List;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class FIPSModeSCMSourceTest {

    @Rule public RealJenkinsRule rule = new RealJenkinsRule().omitPlugins("eddsa-api", "trilead-api", "git-tag-message")
            .javaOptions("-Djenkins.security.FIPS140.COMPLIANCE=true")
            .withLogger(AbstractGitSCMSource.class, Level.SEVERE);

    @Test
    @SuppressWarnings("deprecation")
    public void remotesAreNotFetchedTest() throws Throwable {
        rule.then( r -> {
            GitSCMSource source = new GitSCMSource("http://insecure-repo");
            TaskListener listener = StreamTaskListener.fromStderr();
            assertThrows("expected exception as repo doesn't exist", GitException.class, () -> source.fetch(listener));

            LogRecorder logRecorder = new LogRecorder(AbstractGitSCMSource.class.getName());
            LogRecorder.Target target = new LogRecorder.Target(AbstractGitSCMSource.class.getName(), Level.SEVERE);
            logRecorder.setLoggers(List.of(target));
            r.jenkins.getLog().getRecorders().add(logRecorder);
            assertThat("We should no see the error in the logs", logRecorder.getLogRecords().size(), is(0));

            // Using creds we should be getting an exception
            Throwable exception = assertThrows("We're not saving creds", IllegalArgumentException.class, () -> source.setCredentialsId("cred-id"));
            assertThat(exception.getMessage(), containsString("FIPS requires a secure channel"));
            assertThat("credentials are not saved", source.getCredentialsId(), nullValue());

            // Using old constructor (restricted since 3.4.0) to simulate credentials are being set with unsecure connection
            // This would be equivalent to a user manually adding credentials to config.xml
            GitSCMSource anotherSource = new GitSCMSource("fake", "http://insecure", "credentialsId", "", "", true);
            exception = assertThrows("fetch was interrupted so no credential was leaked", IllegalArgumentException.class, () -> anotherSource.fetch(listener));
            assertThat("We should have a severe log indicating the error", logRecorder.getLogRecords().size(), is(1));
            assertThat("Exception indicates problem", exception.getMessage(), containsString("FIPS requires a secure channel"));
        });
    }
}
