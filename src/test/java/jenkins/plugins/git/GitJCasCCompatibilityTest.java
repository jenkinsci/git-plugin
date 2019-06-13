package jenkins.plugins.git;

import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class GitJCasCCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {

    }

    @Override
    protected String stringInLogExpected() {
        return "hudson.plugins.git.UserRemoteConfig.url = https://git.acmecorp/myGitLib.git";
    }
}
