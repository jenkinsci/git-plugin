package hudson.plugins.git.extensions.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintStream;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class CloneOptionDepthTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private GitSCM scm;
    private Run<?, ?> build;
    private GitClient git;
    private TaskListener listener;

    private final int configuredDepth;
    private final int usedDepth;

    public CloneOptionDepthTest(int configuredDepth, int usedDepth) {
        this.configuredDepth = configuredDepth;
        this.usedDepth = usedDepth;
    }

    @Parameterized.Parameters(name = "depth: configured={0}, used={1}")
    public static Object[][] depthCombinations() {
        return new Object[][] { { 0, 1 }, { 1, 1 }, { 2, 2 } };
    }

    @Before
    public void mockDependencies() throws Exception {
        scm = mock(GitSCM.class);
        build = mock(Run.class);
        git = mock(GitClient.class);
        listener = mock(TaskListener.class);

        BuildData buildData = mock(BuildData.class);
        buildData.lastBuild = mock(Build.class);
        when(build.getEnvironment(listener)).thenReturn(mock(EnvVars.class));
        when(scm.getBuildData(build)).thenReturn(buildData);
    }

    @Issue("JENKINS-53050")
    @Test
    public void decorateCloneCommandShouldUseValidShallowDepth() throws Exception {
        CloneCommand cloneCommand = mock(CloneCommand.class, Mockito.RETURNS_SELF);

        PrintStream logger = mock(PrintStream.class);
        when(listener.getLogger()).thenReturn(logger);

        CloneOption cloneOption = new CloneOption(true, false, null, null);
        cloneOption.setDepth(configuredDepth);

        cloneOption.decorateCloneCommand(scm, build, git, listener, cloneCommand);

        verify(cloneCommand).shallow(true);
        verify(cloneCommand).depth(usedDepth);
        verify(logger).println("Using shallow clone with depth " + usedDepth);
    }

    @Issue("JENKINS-53050")
    @Test
    public void decorateFetchCommandShouldUseValidShallowDepth() throws Exception {
        FetchCommand fetchCommand = mock(FetchCommand.class, Mockito.RETURNS_SELF);

        PrintStream logger = mock(PrintStream.class);
        when(listener.getLogger()).thenReturn(logger);

        CloneOption cloneOption = new CloneOption(true, false, null, null);
        cloneOption.setDepth(configuredDepth);

        cloneOption.decorateFetchCommand(scm, git, listener, fetchCommand);

        verify(fetchCommand).shallow(true);
        verify(fetchCommand).depth(usedDepth);
        verify(logger).println("Using shallow fetch with depth " + usedDepth);
    }
}
