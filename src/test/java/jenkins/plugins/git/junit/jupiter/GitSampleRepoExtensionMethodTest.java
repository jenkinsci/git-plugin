package jenkins.plugins.git.junit.jupiter;

import org.junit.jupiter.api.Test;

import jenkins.plugins.git.GitSampleRepoRule;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GitSampleRepoExtensionMethodTest {

    @WithGitSampleRepo
    @Test
    void gitSampleRepoIsInjected(GitSampleRepoRule rule) throws Exception {
        assertNotNull(rule);
        // somehow testing initialization
        var root = rule.getRoot();
        assertNotNull(root);
        rule.init();
        assertNotNull(rule.head());
    }

    @WithGitSampleRepo
    @Test
    void multipleGitSampleReposInjected(GitSampleRepoRule rule1, GitSampleRepoRule rule2) throws Exception {
        gitSampleRepoIsInjected(rule1);
        gitSampleRepoIsInjected(rule2);
    }
}
