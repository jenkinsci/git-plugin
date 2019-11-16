package jenkins.plugins.git;

import hudson.plugins.git.GitSCM;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GitSCMJCasCCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        GitSCM.DescriptorImpl gitSCM = (GitSCM.DescriptorImpl) restartableJenkinsRule.j.jenkins.getScm(GitSCM.class.getSimpleName());
        assertEquals("user_name", gitSCM.getGlobalConfigName());
        assertEquals("me@mail.com", gitSCM.getGlobalConfigEmail());
        assertTrue(gitSCM.isCreateAccountBasedOnEmail());
    }

    @Override
    protected String stringInLogExpected() {
        return "globalConfigName = user_name";
    }

    @Override
    protected String configResource() {
        return "gitscm-casc.yaml";
    }
}
