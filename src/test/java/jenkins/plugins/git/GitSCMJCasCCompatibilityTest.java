package jenkins.plugins.git;

import hudson.plugins.git.GitSCM;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.*;

@WithJenkins
class GitSCMJCasCCompatibilityTest extends AbstractRoundTripTest {

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule rule, String s) {
        GitSCM.DescriptorImpl gitSCM = (GitSCM.DescriptorImpl) rule.jenkins.getScm(GitSCM.class.getSimpleName());
        assertEquals("user_name", gitSCM.getGlobalConfigName());
        assertEquals("me@mail.com", gitSCM.getGlobalConfigEmail());
        assertTrue(gitSCM.isAllowSecondFetch(), "Allow second fetch setting not honored");
        assertTrue(gitSCM.isShowEntireCommitSummaryInChanges(), "Show entire commit summary setting not honored");
        assertTrue(gitSCM.isHideCredentials(), "Hide credentials setting not honored");
        assertFalse(gitSCM.isUseExistingAccountWithSameEmail(), "Use existing account setting not honored");
        assertTrue(gitSCM.isCreateAccountBasedOnEmail(), "Create account based on email setting not honored");
        assertTrue(gitSCM.isAddGitTagAction(), "Add git tag action setting not honored");
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
