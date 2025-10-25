package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;

import java.io.File;
import java.io.IOException;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jvnet.hudson.test.WithoutJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

class WipeWorkspaceTest extends GitSCMExtensionTest {

    private TestGitRepo repo;
    private GitClient git;

    @Override
    protected void before() throws Exception {
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
    void testWipeWorkspace() throws Exception {
        repo = new TestGitRepo("repo", newFolder(tmp, "junit"), listener);
        git = Git.with(listener, new EnvVars()).in(repo.gitDir).getClient();

        FreeStyleProject projectWithMaster = setupBasicProject(repo);
        git.commit("First commit");
        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);

        List<String> buildLog = build.getLog(175);
        assertThat(buildLog, hasItem("Wiping out workspace first."));
    }

    @Test
    @WithoutJenkins
    void equalsContract() {
        EqualsVerifier.forClass(WipeWorkspace.class)
                .usingGetClass()
                .verify();
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
