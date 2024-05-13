package jenkins.plugins.git.junit.jupiter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jenkins.plugins.git.GitSampleRepoRule;

@WithGitSampleRepo
class GitSampleRepoExtensionClassTest {

    @Test
    void gitSampleRepoIsInjected(GitSampleRepoRule rule) throws Exception {
        Assertions.assertNotNull(rule);
        // somehow testing initialization
        var root = rule.getRoot();
        Assertions.assertNotNull(root);
        rule.init();
        Assertions.assertNotNull(rule.head());
    }
}
