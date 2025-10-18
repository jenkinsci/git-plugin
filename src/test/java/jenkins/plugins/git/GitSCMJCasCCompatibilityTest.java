package jenkins.plugins.git;

import hudson.plugins.git.GitSCM;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitSCMJCasCCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        GitSCM.DescriptorImpl gitSCM = (GitSCM.DescriptorImpl) restartableJenkinsRule.j.jenkins.getScm(GitSCM.class.getSimpleName());
        assertEquals("user_name", gitSCM.getGlobalConfigName());
        assertEquals("me@mail.com", gitSCM.getGlobalConfigEmail());
        assertEquals("Global URL RegEx not configured correctly",
                "(.*github.*?[/:](?<org>[^/]+)/(?<repo>[^/]+?)(?:\\.git)?$)",
                gitSCM.getGlobalUrlRegEx());
        assertTrue("Allow second fetch setting not honored", gitSCM.isAllowSecondFetch());
        assertTrue("Show entire commit summary setting not honored", gitSCM.isShowEntireCommitSummaryInChanges());
        assertTrue("Hide credentials setting not honored", gitSCM.isHideCredentials());
        assertFalse("Use existing account setting not honored", gitSCM.isUseExistingAccountWithSameEmail());
        assertTrue("Create account based on email setting not honored", gitSCM.isCreateAccountBasedOnEmail());
        assertTrue("Add git tag action setting not honored", gitSCM.isAddGitTagAction());
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
