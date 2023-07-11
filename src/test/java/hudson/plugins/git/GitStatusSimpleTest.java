package hudson.plugins.git;

import static org.junit.Assert.*;

import org.eclipse.jgit.transport.URIish;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class GitStatusSimpleTest {

    private GitStatus gitStatus;

    @Before
    public void setUp() throws Exception {
        this.gitStatus = new GitStatus();
    }

    @Test
    public void testGetDisplayName() {
        assertEquals("Git", this.gitStatus.getDisplayName());
    }

    @Test
    public void testGetIconFileName() {
        assertNull(this.gitStatus.getIconFileName());
    }

    @Test
    public void testGetUrlName() {
        assertEquals("git", this.gitStatus.getUrlName());
    }

    @Test
    public void testAllowNotifyCommitParametersDisabled() {
        assertFalse("SECURITY-275: ignore arbitrary notifyCommit parameters", GitStatus.ALLOW_NOTIFY_COMMIT_PARAMETERS);
    }

    @Test
    public void testSafeParametersEmpty() {
        assertEquals("SECURITY-275: Safe notifyCommit parameters", "", GitStatus.SAFE_PARAMETERS);
    }

    @Test
    public void testLooselyMatches() throws Exception {
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
                    lhs + " matches trailing slashes " + badURLTrailingSlashes,
                    GitStatus.looselyMatches(lhs, badURLTrailingSlashes));
            assertFalse(lhs + " matches bad hostname " + badURLHostname, GitStatus.looselyMatches(lhs, badURLHostname));
            for (URIish rhs : uris) {
                assertTrue(lhs + " and " + rhs + " didn't match", GitStatus.looselyMatches(lhs, rhs));
            }
        }
    }
}
