package hudson.plugins.git.extensions.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import hudson.plugins.git.extensions.GitSCMExtension;

import java.io.IOException;
import java.util.Set;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Test;

/**
 * @author Ronny Händel
 */
public class CloneOptionShallowDefaultTagsTest extends GitSCMExtensionTest {

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
        final String noReference = null;
        final Integer noTimeout = null;
        return new CloneOption(shallowClone, noReference, noTimeout);
    }

    @Test
    public void evenShallowCloningFetchesTagsByDefault() throws Exception {
        final String tagName = "v0.0.1";

        repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
        repo.tag(tagName, "a tag that should be fetched by default");

        assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());

        build(project, Result.SUCCESS);

        assertEquals("tag " + tagName + " should have been cloned from remote", 1, tagsInProjectWorkspaceWithName(tagName).size());
    }

    private Set<String> tagsInProjectWorkspaceWithName(String tagPattern) throws IOException, InterruptedException {
        GitClient git = Git.with(listener, null).in(project.getWorkspace()).getClient();
        return git.getTagNames(tagPattern);
    }
}
