package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.util.Collections;
import java.util.List;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.plugins.git.traits.GitToolSCMSourceTrait;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.jenkinsci.plugins.gitclient.Git;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Git.class)
public class AbstractGitSCMSourceRetrieveHeadsTest {

    public static final String EXPECTED_GIT_EXE = "git-custom";

    private AbstractGitSCMSource gitSCMSource;

    @Before
    public void setup() throws Exception {
        // Mock GitTool
        GitTool mockedTool = PowerMockito.mock(GitTool.class, Mockito.RETURNS_DEFAULTS);
        PowerMockito.doReturn(EXPECTED_GIT_EXE).when(mockedTool).getGitExe();

        // Mock git implementation
        Git git = Mockito.mock(Git.class, Mockito.CALLS_REAL_METHODS);
        PowerMockito.doThrow(new GitToolSpecified()).when(git).using(EXPECTED_GIT_EXE);
        PowerMockito.doThrow(new GitToolNotSpecified()).when(git).getClient();
        PowerMockito.doReturn(git).when(git).in(Mockito.any(File.class));
        PowerMockito.doReturn(git).when(git).in(Mockito.any(FilePath.class));

        // mock static factory to return our git mock
        PowerMockito.mockStatic(Git.class, Mockito.CALLS_REAL_METHODS);
        PowerMockito.doReturn(git).when(Git.class, "with", Mockito.any(), Mockito.any());

        // Partial mock our AbstractGitSCMSourceImpl
        gitSCMSource = PowerMockito.spy(new AbstractGitSCMSourceImpl());
        // Always resolve to mocked GitTool
        PowerMockito.doReturn(mockedTool).when(gitSCMSource).resolveGitTool(EXPECTED_GIT_EXE, TaskListener.NULL);
    }

    /*
     * Validate that the correct git installation is used when fetching latest heads.
     * That means {@link Git#using(String)} is called properly.
     */
    @Test(expected = GitToolSpecified.class)
    public void correctGitToolIsUsed() throws Exception {
        try {
            // Should throw exception confirming that Git#using was used correctly
            gitSCMSource.retrieve(new SCMHead("master"), TaskListener.NULL);
        } catch (GitToolNotSpecified e) {
            Assert.fail("Git client was constructed with arbitrary git tool");
        }
    }

    /*
     * Validate that the correct git installation is used when fetching latest heads.
     * That means {@link Git#using(String)} is called properly.
     */
    @Test(expected = GitToolSpecified.class)
    public void correctGitToolIsUsed2() throws Exception {
        try {
            // Should throw exception confirming that Git#using was used correctly
            gitSCMSource.retrieve(null, PowerMockito.mock(SCMHeadObserver.class), null, TaskListener.NULL);
        } catch (GitToolNotSpecified e) {
            Assert.fail("Git client was constructed with arbitrary git tool");
        }
    }

    public static class GitToolSpecified extends RuntimeException {

    }

    public static class GitToolNotSpecified extends RuntimeException {

    }

    public static class AbstractGitSCMSourceImpl extends AbstractGitSCMSource {

        public AbstractGitSCMSourceImpl() {
            setId("AbstractGitSCMSourceImpl-id");
        }

        @NonNull
        @Override
        public List<SCMSourceTrait> getTraits() {
            return Collections.<SCMSourceTrait>singletonList(new GitToolSCMSourceTrait(EXPECTED_GIT_EXE){
                @Override
                public SCMSourceTraitDescriptor getDescriptor() {
                    return new GitBrowserSCMSourceTrait.DescriptorImpl();
                }
            });
        }

        @Override
        public String getCredentialsId() {
            return "";
        }

        @Override
        public String getRemote() {
            return "";
        }

        @Override
        public SCMSourceDescriptor getDescriptor() {
            return new DescriptorImpl();
        }

        public static class DescriptorImpl extends SCMSourceDescriptor {

            @Override
            public String getDisplayName() {
                return null;
            }
        }
    }
}
