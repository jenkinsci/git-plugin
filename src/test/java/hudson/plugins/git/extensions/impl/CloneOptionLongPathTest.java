package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.io.PrintStream;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

public class CloneOptionLongPathTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private GitSCM scm;
    private Run<?, ?> build;
    private GitClient git;
    private TaskListener listener;

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

    @Issue("JENKINS-47609")
    @Test
    public void decorateCloneCommandShouldUseValidLongPath() throws Exception {
        CloneCommand cloneCommand = mock(CloneCommand.class, Mockito.RETURNS_SELF);

        PrintStream logger = mock(PrintStream.class);
        when(listener.getLogger()).thenReturn(logger);

        boolean enableLongPath = true;
        CloneOption cloneOption = new CloneOption(true, false, enableLongPath,null, null);

        cloneOption.decorateCloneCommand(scm, build, git, listener, cloneCommand);

        verify(cloneCommand).longPath(true);
        verify(logger).println("Setting core.longpaths to true");
    }
}
