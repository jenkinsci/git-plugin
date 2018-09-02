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
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.SubmoduleUpdateCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class SubmoduleOptionDepthTest {

    private GitSCM scm;
    private Run<?, ?> build;
    private GitClient git;
    private TaskListener listener;

    private final int configuredDepth;
    private final int usedDepth;

    public SubmoduleOptionDepthTest(int configuredDepth, int usedDepth) {
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
    public void submoduleUpdateShouldUseValidShallowDepth() throws Exception {
        SubmoduleUpdateCommand submoduleUpdate = mock(SubmoduleUpdateCommand.class, Mockito.RETURNS_SELF);
        when(git.hasGitModules()).thenReturn(true);
        when(git.submoduleUpdate()).thenReturn(submoduleUpdate);

        PrintStream logger = mock(PrintStream.class);
        when(listener.getLogger()).thenReturn(logger);

        SubmoduleOption submoduleOption = new SubmoduleOption(false, false, false, null, null, false);
        submoduleOption.setShallow(true);
        submoduleOption.setDepth(configuredDepth);

        submoduleOption.onCheckoutCompleted(scm, build, git, listener);

        verify(submoduleUpdate).shallow(true);
        verify(submoduleUpdate).depth(usedDepth);
        verify(logger).println("Using shallow submodule update with depth " + usedDepth);
    }
}
