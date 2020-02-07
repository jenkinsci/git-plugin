package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class CloneOptionHonorRefSpecTest extends AbstractGitTestCase {

    private GitSCM gitSCM;
    private FreeStyleBuild build;
    private String refSpecVar;
    private TaskListener listener;

    public CloneOptionHonorRefSpecTest(String useRefSpecVariable) {
        this.refSpecVar = useRefSpecVariable;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteRefSpecVariable() {
        List<Object[]> values = new ArrayList<>();
        String[] allowed = {"${BUILD_NUMBER}","${BUILD_ID}",
                "${GIT_COMMIT}"};
        for (String refSpecValue : allowed) {
            Object[] combination = {refSpecValue};
            values.add(combination);
        }
        return values;
    }

    @Before
    public void setupProjectAndDependencies() throws Exception{
        listener = mock(TaskListener.class);
        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");

        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "+refs/heads/master:refs/remotes/origin/master" + refSpecVar, null));

        /* Creating a project */
        FreeStyleProject project = setupProject(repos, Collections.singletonList(new BranchSpec("master" + refSpecVar)), null, false, null);
        build = build(project, Result.SUCCESS, commitFile1);
        gitSCM = (GitSCM) project.getScm();
    }

    @Test
    public void decorateCloneCommandWithHonorRefSpec() throws Exception {
        List<RefSpec> refSpecs = new ArrayList<>();
        String envKey = refSpecVar.substring(refSpecVar.indexOf("{")+1, refSpecVar.indexOf("}"));
        String refSpecVariable = build.getEnvironment(listener).get(envKey);
        refSpecs.add(new RefSpec("+refs/heads/master:refs/remotes/origin/master" + refSpecVariable));

        PrintStream logger = mock(PrintStream.class);
        when(listener.getLogger()).thenReturn(logger);

        CloneCommand cloneCommand = mock(CloneCommand.class);

        /* Creating a clone option which would honor refspec for initial clone */
        CloneOption cloneOption = new CloneOption(false, false, null, null);
        cloneOption.setHonorRefspec(true);
        cloneOption.decorateCloneCommand(gitSCM, build, git, listener, cloneCommand);

        verify(cloneCommand).refspecs(refSpecs);
    }
}
