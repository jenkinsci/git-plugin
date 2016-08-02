package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;

import java.util.ArrayList;
import java.util.List;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

import static org.junit.Assert.*;
import org.junit.Before;

public class AbstractGitSCMSourceTrivialTest {

    private AbstractGitSCMSource gitSCMSource = null;

    private final String expectedCredentialsId = "expected-credentials-id";

    private final String expectedIncludes = "*master release* fe?ture substring";
    private final String expectedExcludes = "release bugfix*";

    private final String expectedRemote = "origin";

    private final String expectedRefSpec = "+refs/heads/*:refs/remotes/origin/*";
    private final List<RefSpec> expectedRefSpecs = new ArrayList<RefSpec>();

    @Before
    public void setUp() throws Exception {
        if (expectedRefSpecs.isEmpty()) {
            expectedRefSpecs.add(new RefSpec(expectedRefSpec));
        }
        gitSCMSource = new AbstractGitSCMSourceImpl();
    }

    @Test
    public void testGetCredentialsId() {
        assertEquals(expectedCredentialsId, gitSCMSource.getCredentialsId());
    }

    @Test
    public void testGetRemote() {
        assertEquals(expectedRemote, gitSCMSource.getRemote());
    }

    @Test
    public void testGetIncludes() {
        assertEquals(expectedIncludes, gitSCMSource.getIncludes());
    }

    @Test
    public void testGetExcludes() {
        assertEquals(expectedExcludes, gitSCMSource.getExcludes());
    }

    @Test
    public void testGetRemoteName() {
        assertEquals(expectedRemote, gitSCMSource.getRemoteName());
    }

    @Test
    public void testGetRefSpecs() {
        assertEquals(expectedRefSpecs, gitSCMSource.getRefSpecs());
    }

    @Test
    public void testIsExcluded() {
        assertFalse(gitSCMSource.isExcluded("master"));
        assertFalse(gitSCMSource.isExcluded("remote/master"));
        assertFalse(gitSCMSource.isExcluded("release/X.Y"));
        assertFalse(gitSCMSource.isExcluded("releaseX.Y"));
        assertFalse(gitSCMSource.isExcluded("fe?ture"));
        assertFalse(gitSCMSource.isExcluded("substring"));

        // Excluded because they don't match the inclusion strings
        assertTrue(gitSCMSource.isExcluded("feature")); // '?' is not a wildcard
        assertTrue(gitSCMSource.isExcluded("test"));
        assertTrue(gitSCMSource.isExcluded("foo/substring"));
        assertTrue(gitSCMSource.isExcluded("substring/end"));
        assertTrue(gitSCMSource.isExcluded("substring1"));
        assertTrue(gitSCMSource.isExcluded("remote/substring2"));
        assertTrue(gitSCMSource.isExcluded("origin/substring"));

        // Excluded because they match an exclusion string
        assertTrue(gitSCMSource.isExcluded("release"));
        assertTrue(gitSCMSource.isExcluded("bugfix"));
        assertTrue(gitSCMSource.isExcluded("bugfix/test"));
    }

    @Test
    public void testGetRemoteConfigs() {
        List<UserRemoteConfig> remoteConfigs = gitSCMSource.getRemoteConfigs();
        assertEquals(expectedRemote, remoteConfigs.get(0).getName());
        assertEquals(expectedRefSpec, remoteConfigs.get(0).getRefspec());
        assertEquals("Wrong number of entries in remoteConfigs", 1, remoteConfigs.size());
    }

    @Test
    public void testBuild() {
        final String expectedBranchName = "origin/master";
        SCMHead head = new SCMHead(expectedBranchName);
        SCMRevision revision = new SCMRevisionImpl(head);
        GitSCM gitSCM = (GitSCM) gitSCMSource.build(head, revision);

        List<UserRemoteConfig> remoteConfigs = gitSCM.getUserRemoteConfigs();
        assertEquals(expectedRemote, remoteConfigs.get(0).getName());
        assertEquals(expectedRefSpec, remoteConfigs.get(0).getRefspec());
        assertEquals("Wrong number of entries in remoteConfigs", 1, remoteConfigs.size());

        List<BranchSpec> branches = gitSCM.getBranches();
        assertEquals(expectedBranchName, branches.get(0).getName());
        assertEquals("Wrong number of branches", 1, branches.size());
    }

    public class AbstractGitSCMSourceImpl extends AbstractGitSCMSource {

        public AbstractGitSCMSourceImpl() {
            super("AbstractGitSCMSourceImpl-id");
        }

        public String getCredentialsId() {
            return expectedCredentialsId;
        }

        public String getRemote() {
            return expectedRemote;
        }

        public String getIncludes() {
            return expectedIncludes;
        }

        public String getExcludes() {
            return expectedExcludes;
        }

        public List<RefSpec> getRefSpecs() {
            return expectedRefSpecs;
        }
    }

    private class SCMRevisionImpl extends SCMRevision {

        protected SCMRevisionImpl(@NonNull SCMHead scmh) {
            super(scmh);
        }

        @Override
        public boolean equals(Object o) {
            throw new UnsupportedOperationException("Intentionally unimplemented");
        }

        @Override
        public int hashCode() {
            throw new UnsupportedOperationException("Intentionally unimplemented");
        }
    }

}
