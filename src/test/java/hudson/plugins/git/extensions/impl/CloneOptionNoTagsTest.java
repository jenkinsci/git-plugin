package hudson.plugins.git.extensions.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import org.junit.jupiter.api.Test;
import hudson.plugins.git.extensions.GitSCMExtension;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * @author Ronny Händel
 */
class CloneOptionNoTagsTest extends GitSCMExtensionTest {

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
        final boolean dontFetchTags = true;
        final String noReference = null;
        final Integer noTimeout = null;
        return new CloneOption(shallowClone, dontFetchTags, noReference, noTimeout);
    }

    @Test
    void cloningShouldNotFetchTags() throws Exception {

        repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
        repo.tag("v0.0.1", "a tag that should never be fetched");

        assertTrue(project.poll(listener).hasChanges(), "scm polling should detect a change after initial commit");

        build(project, Result.SUCCESS);

        assertTrue(allTagsInProjectWorkspace().isEmpty(), "there should no tags have been cloned from remote");
    }

    @Test
    void detectNoChangeAfterCreatingATag() throws Exception {

        repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");

        assertTrue(project.poll(listener).hasChanges(), "scm polling should detect a change after initial commit");

        build(project, Result.SUCCESS);

        repo.tag("v0.0.1", "a tag that should never be fetched");

        assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect a change after creating a tag");

        build(project, Result.SUCCESS);

        assertTrue(allTagsInProjectWorkspace().isEmpty(), "there should no tags have been fetched from remote");
    }

    private Set<String> allTagsInProjectWorkspace() throws Exception {
        GitClient git = Git.with(listener, null).in(project.getSomeWorkspace()).getClient();
        return git.getTagNames("*");
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
