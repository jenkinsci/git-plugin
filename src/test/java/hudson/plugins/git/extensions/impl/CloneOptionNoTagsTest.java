package hudson.plugins.git.extensions.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Set;

import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import hudson.plugins.git.extensions.GitSCMExtension;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Test;

/**
 * @author Ronny Händel
 */
public class CloneOptionNoTagsTest extends GitSCMExtensionTest {

    FreeStyleProject project;
    TestGitRepo repo;

    @Override
    public void before() throws Exception {
        repo = new TestGitRepo("repo", tmp.newFolder(), listener);
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
    public void cloningShouldNotFetchTags() throws Exception {

        repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
        repo.tag("v0.0.1", "a tag that should never be fetched");

        assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());

        build(project, Result.SUCCESS);

        assertTrue("there should no tags have been cloned from remote", allTagsInProjectWorkspace().isEmpty());
    }

    @Test
    public void detectNoChangeAfterCreatingATag() throws Exception {

        repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");

        assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());

        build(project, Result.SUCCESS);

        repo.tag("v0.0.1", "a tag that should never be fetched");

        assertFalse("scm polling should not detect a change after creating a tag", project.poll(listener).hasChanges());

        build(project, Result.SUCCESS);

        assertTrue("there should no tags have been fetched from remote", allTagsInProjectWorkspace().isEmpty());
    }

    private Set<String> allTagsInProjectWorkspace() throws IOException, InterruptedException {
        GitClient git = Git.with(listener, null).in(project.getWorkspace()).getClient();
        return git.getTagNames("*");
    }
}
