package hudson.plugins.git.extensions.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import org.junit.jupiter.api.Test;
import hudson.plugins.git.extensions.GitSCMExtension;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * @author Ronny Händel
 */
class CloneOptionShallowDefaultTagsTest extends GitSCMExtensionTest {

    FreeStyleProject project;
    TestGitRepo repo;

    @Override
    protected void before() throws Exception {
        repo = new TestGitRepo("repo", newFolder(tmp, "junit"), listener);
        project = setupBasicProject(repo);
    }

    @Override
    protected GitSCMExtension getExtension() {
        final boolean shallowClone = true;
        final String noReference = null;
        final Integer noTimeout = null;
        return new CloneOption(shallowClone, noReference, noTimeout);
    }

    @Test
    void evenShallowCloningFetchesTagsByDefault() throws Exception {
        final String tagName = "v0.0.1";

        repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
        repo.tag(tagName, "a tag that should be fetched by default");

        assertTrue(project.poll(listener).hasChanges(), "scm polling should detect a change after initial commit");

        build(project, Result.SUCCESS);

        assertEquals(1, tagsInProjectWorkspaceWithName(tagName).size(), "tag " + tagName + " should have been cloned from remote");
    }

    private Set<String> tagsInProjectWorkspaceWithName(String tagPattern) throws Exception {
        GitClient git = Git.with(listener, null).in(project.getSomeWorkspace()).getClient();
        return git.getTagNames(tagPattern);
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
