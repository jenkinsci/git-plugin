package jenkins.plugins.git;

import hudson.logging.LogRecorder;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

import java.util.List;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FIPSModeSCMSourceTest {

    @RegisterExtension
    private final RealJenkinsExtension extension = new RealJenkinsExtension().omitPlugins("eddsa-api", "trilead-api", "git-tag-message")
            .javaOptions("-Djenkins.security.FIPS140.COMPLIANCE=true")
            .withLogger(AbstractGitSCMSource.class, Level.SEVERE);

    @Test
    @SuppressWarnings("deprecation")
    void remotesAreNotFetchedTest() throws Throwable {
        extension.then(r -> {
            GitSCMSource source = new GitSCMSource("http://insecure-repo");
            TaskListener listener = StreamTaskListener.fromStderr();
            assertThrows(IOException.class, () -> source.fetch(listener), "expected exception as repo doesn't exist");

            LogRecorder logRecorder = new LogRecorder(AbstractGitSCMSource.class.getName());
            LogRecorder.Target target = new LogRecorder.Target(AbstractGitSCMSource.class.getName(), Level.SEVERE);
            logRecorder.setLoggers(List.of(target));
            r.jenkins.getLog().getRecorders().add(logRecorder);
            assertThat("We should no see the error in the logs", logRecorder.getLogRecords().size(), is(0));

            // Using creds we should be getting an exception
            Throwable exception = assertThrows(IllegalArgumentException.class, () -> source.setCredentialsId("cred-id"), "We're not saving creds");
            assertThat(exception.getMessage(), containsString("FIPS requires a secure channel"));
            assertThat("credentials are not saved", source.getCredentialsId(), nullValue());

            // Using old constructor (restricted since 3.4.0) to simulate credentials are being set with unsecure connection
            // This would be equivalent to a user manually adding credentials to config.xml
            GitSCMSource anotherSource = new GitSCMSource("fake", "http://insecure", "credentialsId", "", "", true);
            exception = assertThrows(IllegalArgumentException.class, () -> anotherSource.fetch(listener), "fetch was interrupted so no credential was leaked");
            assertThat("We should have a severe log indicating the error", logRecorder.getLogRecords().size(), is(1));
            assertThat("Exception indicates problem", exception.getMessage(), containsString("FIPS requires a secure channel"));
        });
    }
}
