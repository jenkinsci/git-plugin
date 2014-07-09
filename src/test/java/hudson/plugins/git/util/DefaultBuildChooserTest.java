package hudson.plugins.git.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;

/**
 * @author Arnout Engelen
 */
public class DefaultBuildChooserTest extends AbstractGitTestCase {
    public void testChooseGitRevisionToBuildByShaHash() throws Exception {
        git.commit("Commit 1");
        String shaHashCommit1 = git.getBranches().iterator().next().getSHA1String();
        git.commit("Commit 2");
        String shaHashCommit2 = git.getBranches().iterator().next().getSHA1String();
        assertNotSame(shaHashCommit1, shaHashCommit2);

        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, shaHashCommit1, git, null, null, null);

        assertEquals(1, candidateRevisions.size());
        assertEquals(shaHashCommit1, candidateRevisions.iterator().next().getSha1String());
    }

    public void testMatchBranch() throws Exception {
        ArrayList<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("*/master"));
        branches.add(new BranchSpec("*/example"));

        GitSCM gitSCM = createGitSCMForTest( branches );
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) gitSCM.getBuildChooser();

        assertTrue( buildChooser.isMatchingBranch( "origin/master" ) );
        assertFalse( buildChooser.isMatchingBranch( "origin/awesome" ) );
        assertTrue( buildChooser.isMatchingBranch( "origin/example" ) );
    }

    private GitSCM createGitSCMForTest( ArrayList<BranchSpec> branches ) {
        return new GitSCM( createRepoList("foo"), branches, false,
                           Collections.<SubmoduleConfig>emptyList(),
                           null, null, null);
    }

    // Copied from GitSCM.java
    static private List<UserRemoteConfig> createRepoList(String url) {
        List<UserRemoteConfig> repoList = new ArrayList<UserRemoteConfig>();
        repoList.add(new UserRemoteConfig(url, null, null, null));
        return repoList;
    }
}
