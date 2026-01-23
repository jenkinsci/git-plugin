package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;

import java.util.ArrayList;
import java.util.List;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.BeforeEach;

import org.eclipse.jgit.transport.RefSpec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

class AbstractGitSCMSourceTrivialTest {

    private AbstractGitSCMSource gitSCMSource = null;

    private final String expectedCredentialsId = "expected-credentials-id";

    private final String expectedIncludes = "*master release* fe?ture substring";
    private final String expectedExcludes = "release bugfix*";

    private final String expectedRemote = "origin";

    private final String expectedRefSpec = "+refs/heads/*:refs/remotes/origin/*";
    private final List<RefSpec> expectedRefSpecs = new ArrayList<>();

    @BeforeEach
    void beforeEach() throws Exception {
        if (expectedRefSpecs.isEmpty()) {
            expectedRefSpecs.add(new RefSpec(expectedRefSpec));
        }
        gitSCMSource = new AbstractGitSCMSourceImpl();
    }

    @Test
    @Deprecated
    void basicTestIsExcluded() {
        AbstractGitSCMSource abstractGitSCMSource = mock(AbstractGitSCMSource.class);

        when(abstractGitSCMSource.getIncludes()).thenReturn("*master release* fe?ture");
        when(abstractGitSCMSource.getExcludes()).thenReturn("release bugfix*");
        when(abstractGitSCMSource.isExcluded(Mockito.anyString())).thenCallRealMethod();

        assertFalse(abstractGitSCMSource.isExcluded("master"));
        assertFalse(abstractGitSCMSource.isExcluded("remote/master"));
        assertFalse(abstractGitSCMSource.isExcluded("release/X.Y"));
        assertFalse(abstractGitSCMSource.isExcluded("releaseX.Y"));
        assertFalse(abstractGitSCMSource.isExcluded("fe?ture"));
        assertTrue(abstractGitSCMSource.isExcluded("feature"));
        assertTrue(abstractGitSCMSource.isExcluded("release"));
        assertTrue(abstractGitSCMSource.isExcluded("bugfix"));
        assertTrue(abstractGitSCMSource.isExcluded("bugfix/test"));
        assertTrue(abstractGitSCMSource.isExcluded("test"));

        when(abstractGitSCMSource.getIncludes()).thenReturn("master feature/*");
        when(abstractGitSCMSource.getExcludes()).thenReturn("feature/*/private");
        assertFalse(abstractGitSCMSource.isExcluded("master"));
        assertTrue(abstractGitSCMSource.isExcluded("devel"));
        assertFalse(abstractGitSCMSource.isExcluded("feature/spiffy"));
        assertTrue(abstractGitSCMSource.isExcluded("feature/spiffy/private"));
    }

    @Test
    void testGetCredentialsId() {
        assertEquals(expectedCredentialsId, gitSCMSource.getCredentialsId());
    }

    @Test
    void testGetRemote() {
        assertEquals(expectedRemote, gitSCMSource.getRemote());
    }

    @Test
    @Deprecated
    void testGetIncludes() {
        assertEquals(expectedIncludes, gitSCMSource.getIncludes());
    }

    @Test
    @Deprecated
    void testGetExcludes() {
        assertEquals(expectedExcludes, gitSCMSource.getExcludes());
    }

    @Test
    @Deprecated
    void testGetRemoteName() {
        assertEquals(expectedRemote, gitSCMSource.getRemoteName());
    }

    @Test
    @Deprecated
    void testGetRefSpecs() {
        assertEquals(expectedRefSpecs, gitSCMSource.getRefSpecs());
    }

    @Test
    @Deprecated
    void testIsExcluded() {
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
    @Deprecated
    void testGetRemoteConfigs() {
        List<UserRemoteConfig> remoteConfigs = gitSCMSource.getRemoteConfigs();
        assertEquals(expectedRemote, remoteConfigs.get(0).getName());
        assertEquals(expectedRefSpec, remoteConfigs.get(0).getRefspec());
        assertEquals(1, remoteConfigs.size(), "Wrong number of entries in remoteConfigs");
    }

    @Test
    void testBuild() {
        final String expectedBranchName = "origin/master";
        SCMHead head = new SCMHead(expectedBranchName);
        SCMRevision revision = new SCMRevisionImpl(head);
        GitSCM gitSCM = (GitSCM) gitSCMSource.build(head, revision);

        List<UserRemoteConfig> remoteConfigs = gitSCM.getUserRemoteConfigs();
        assertEquals(expectedRemote, remoteConfigs.get(0).getName());
        assertEquals(expectedRefSpec, remoteConfigs.get(0).getRefspec());
        assertEquals(1, remoteConfigs.size(), "Wrong number of entries in remoteConfigs");

        List<BranchSpec> branches = gitSCM.getBranches();
        assertEquals(expectedBranchName, branches.get(0).getName());
        assertEquals(1, branches.size(), "Wrong number of branches");
    }

    @Test
    void equalsContractSCMRevisionImpl() {
        EqualsVerifier.forClass(AbstractGitSCMSource.SCMRevisionImpl.class)
                .usingGetClass()
                .verify();
    }

    public class AbstractGitSCMSourceImpl extends AbstractGitSCMSource {

        public AbstractGitSCMSourceImpl() {
            setId("AbstractGitSCMSourceImpl-id");
        }

        public String getCredentialsId() {
            return expectedCredentialsId;
        }

        public String getRemote() {
            return expectedRemote;
        }

        @Override
        @Deprecated
        public String getIncludes() {
            return expectedIncludes;
        }

        @Override
        @Deprecated
        public String getExcludes() {
            return expectedExcludes;
        }

        @Override
        @Deprecated
        public List<RefSpec> getRefSpecs() {
            return expectedRefSpecs;
        }
    }

    private static class SCMRevisionImpl extends SCMRevision {

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
