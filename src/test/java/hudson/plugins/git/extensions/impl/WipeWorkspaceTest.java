package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Test;
import org.jvnet.hudson.test.WithoutJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

public class WipeWorkspaceTest extends GitSCMExtensionTest {

    TestGitRepo repo;
    GitClient git;

    @Override
    public void before() throws Exception {
        // do nothing
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
        repo = new TestGitRepo("repo", tmp.newFolder(), listener);
        git = Git.with(listener, new EnvVars()).in(repo.gitDir).getClient();

        FreeStyleProject projectWithMaster = setupBasicProject(repo);
        git.commit("First commit");
        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);

        List<String> buildLog = build.getLog(175);
        assertThat(buildLog, hasItem("Wiping out workspace first."));
    }

    @Test
    @WithoutJenkins
    public void equalsContract() {
        EqualsVerifier.forClass(WipeWorkspace.class)
                .usingGetClass()
                .verify();
    }
}
