package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class WipeWorkspaceTest extends GitSCMExtensionTest {

    TestGitRepo repo;
    GitClient git;

    @Override
    public void before() throws Exception {
        repo = new TestGitRepo("repo", tmp.newFolder(), listener);
        git = Git.with(listener, new EnvVars()).in(repo.gitDir).getClient();
    }

    @Override
    protected GitSCMExtension getExtension() {
        return new WipeWorkspace();
    }

    /**
     * Test to confirm the behavior of forcing re-clone before checkout by cleaning the workspace first.
     **/
    @Test
    public void testWipeWorkspace() throws Exception {
        FreeStyleProject projectWithMaster = setupBasicProject(repo);
        WipeWorkspace wipeWorkspaceExtension = new WipeWorkspace();
        ((GitSCM)projectWithMaster.getScm()).getExtensions().add(wipeWorkspaceExtension);

        git.commit("First commit");
        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);

        String buildLog = build.getLog();
        assertThat("Workspace not cleaned before checkout",true, is(buildLog.contains("Wiping out workspace first.")));
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(WipeWorkspace.class)
                .usingGetClass()
                .verify();
    }
}
