package hudson.plugins.git;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jgit.transport.URIish;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitStatusSimpleTest {

    private GitStatus gitStatus;

    @BeforeEach
    void beforeEach() {
        this.gitStatus = new GitStatus();
    }

    @Test
    void testGetDisplayName() {
        assertEquals("Git", this.gitStatus.getDisplayName());
    }

    @Test
    void testGetIconFileName() {
        assertNull(this.gitStatus.getIconFileName());
    }

    @Test
    void testGetUrlName() {
        assertEquals("git", this.gitStatus.getUrlName());
    }

    @Test
    void testAllowNotifyCommitParametersDisabled() {
        assertFalse(GitStatus.ALLOW_NOTIFY_COMMIT_PARAMETERS, "SECURITY-275: ignore arbitrary notifyCommit parameters");
    }

    @Test
    void testSafeParametersEmpty() {
        assertEquals("", GitStatus.SAFE_PARAMETERS, "SECURITY-275: Safe notifyCommit parameters");
    }

    @Test
    void testLooselyMatches() throws Exception {
        String[] equivalentRepoURLs = new String[] {
            "https://example.com/jenkinsci/git-plugin",
            "https://example.com/jenkinsci/git-plugin/",
            "https://example.com/jenkinsci/git-plugin.git",
            "https://example.com/jenkinsci/git-plugin.git/",
            "https://someone@example.com/jenkinsci/git-plugin.git",
            "https://someone:somepassword@example.com/jenkinsci/git-plugin/",
            "git://example.com/jenkinsci/git-plugin",
            "git://example.com/jenkinsci/git-plugin/",
            "git://example.com/jenkinsci/git-plugin.git",
            "git://example.com/jenkinsci/git-plugin.git/",
            "ssh://git@example.com/jenkinsci/git-plugin",
            "ssh://example.com/jenkinsci/git-plugin.git",
            "git@example.com:jenkinsci/git-plugin/",
            "git@example.com:jenkinsci/git-plugin.git",
            "git@example.com:jenkinsci/git-plugin.git/"
        };
        List<URIish> uris = new ArrayList<>();
        for (String testURL : equivalentRepoURLs) {
            uris.add(new URIish(testURL));
        }

        /* Extra slashes on end of URL probably should be considered equivalent,
         * but current implementation does not consider them as loose matches
         */
        URIish badURLTrailingSlashes = new URIish(equivalentRepoURLs[0] + "///");
        /* Different hostname should always fail match check */
        URIish badURLHostname = new URIish(equivalentRepoURLs[0].replace("example.com", "bitbucket.org"));

        for (URIish lhs : uris) {
            assertFalse(
                    GitStatus.looselyMatches(lhs, badURLTrailingSlashes),
                    lhs + " matches trailing slashes " + badURLTrailingSlashes);
            assertFalse(GitStatus.looselyMatches(lhs, badURLHostname), lhs + " matches bad hostname " + badURLHostname);
            for (URIish rhs : uris) {
                assertTrue(GitStatus.looselyMatches(lhs, rhs), lhs + " and " + rhs + " didn't match");
            }
        }
    }
}
