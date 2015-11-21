package hudson.plugins.git.util;

import hudson.plugins.git.AbstractGitRepository;
import java.util.Collection;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Arnout Engelen
 */
public class DefaultBuildChooserTest extends AbstractGitRepository {
    @Test
    public void testChooseGitRevisionToBuildByShaHash() throws Exception {
        testGitClient.commit("Commit 1");
        String shaHashCommit1 = testGitClient.getBranches().iterator().next().getSHA1String();
        testGitClient.commit("Commit 2");
        String shaHashCommit2 = testGitClient.getBranches().iterator().next().getSHA1String();
        assertNotSame(shaHashCommit1, shaHashCommit2);

        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, shaHashCommit1, true, testGitClient, null, null, null);

        assertEquals(1, candidateRevisions.size());
        assertEquals(shaHashCommit1, candidateRevisions.iterator().next().getSha1String());

        candidateRevisions = buildChooser.getCandidateRevisions(false, "aaa" + shaHashCommit1.substring(3), true, testGitClient, null, null, null);
        assertTrue(candidateRevisions.isEmpty());
    }
    /**
     * RegExp patterns prefixed with : should pass through to DefaultBuildChooser.getAdvancedCandidateRevisions
     * @throws Exception
     */
    @Test
    public void testIsAdvancedSpec() throws Exception {
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        assertFalse(buildChooser.isAdvancedSpec("origin/master"));
        assertTrue(buildChooser.isAdvancedSpec("origin/master-*"));
        assertTrue(buildChooser.isAdvancedSpec("origin**"));
        // regexp use case
        assertTrue(buildChooser.isAdvancedSpec(":origin/master"));
        assertTrue(buildChooser.isAdvancedSpec(":origin/master-\\d{*}"));
    }
}
