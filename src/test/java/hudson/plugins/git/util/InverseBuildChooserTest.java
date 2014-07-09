package hudson.plugins.git.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;

/**
 * @author Shy Aberman
 */
public class InverseBuildChooserTest extends TestCase {
    public void testMatchBranch() throws Exception {
        ArrayList<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("*/master"));
        branches.add(new BranchSpec("*/example"));

        GitSCM gitSCM = createGitSCMForTest( branches );
        gitSCM.setBuildChooser( new InverseBuildChooser() );

        InverseBuildChooser buildChooser = (InverseBuildChooser) gitSCM.getBuildChooser();

        assertFalse( buildChooser.isMatchingBranch( "origin/master" ) );
        assertTrue( buildChooser.isMatchingBranch( "origin/awesome" ) );
        assertFalse( buildChooser.isMatchingBranch( "origin/example" ) );
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
