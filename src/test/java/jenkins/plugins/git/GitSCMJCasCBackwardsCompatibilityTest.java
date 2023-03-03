package jenkins.plugins.git;

import hudson.plugins.git.GitSCM;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GitSCMJCasCBackwardsCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        GitSCM.DescriptorImpl gitSCM = (GitSCM.DescriptorImpl) restartableJenkinsRule.j.jenkins.getScm(GitSCM.class.getSimpleName());
        assertTrue("Add git tag action setting not honored", gitSCM.isAddGitTagAction());
        assertTrue("Allow second fetch setting not honored", gitSCM.isAllowSecondFetch());
        assertTrue("Use existing account setting not honored", gitSCM.isUseExistingAccountWithSameEmail());
        assertEquals("test@example.com", gitSCM.getGlobalConfigEmail());
        assertEquals("test_user", gitSCM.getGlobalConfigName());
        assertTrue("Hide credentials setting not honored", gitSCM.isHideCredentials());
        assertTrue("Show entire commit summary setting not honored", gitSCM.isShowEntireCommitSummaryInChanges());
        assertTrue("Create account based on email setting not honored", gitSCM.isCreateAccountBasedOnEmail());
    }

    @Override
    protected String stringInLogExpected() {
        return "globalConfigName = test_user";
    }

    @Override
    protected String configResource() {
        return "gitscm-backwards-compatibility.yaml";
    }
}
