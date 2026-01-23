package hudson.plugins.git.extensions.impl;

import hudson.model.*;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Kanstantsin Shautsou
 */
class MessageExclusionTest extends GitSCMExtensionTest {

    private FreeStyleProject project;
    private TestGitRepo repo;

	@Override
	protected GitSCMExtension getExtension() {
		return new MessageExclusion("(?s).*\\[maven-release-plugin\\].*");
	}

	@Override
    protected void before() throws Exception {
		repo = new TestGitRepo("repo", newFolder(tmp, "junit"), listener);
		project = setupBasicProject(repo);
	}

    @Test
    void test() throws Exception {
		repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");

		assertTrue(project.poll(listener).hasChanges(), "scm polling should detect a change after initial commit");

		build(project, Result.SUCCESS);

		assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

		repo.commit("repo-init", repo.janeDoe, " [maven-release-plugin] excluded message commit");

		assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect excluded message");

		repo.commit("repo-init", repo.janeDoe, "first line in excluded commit\nsecond\nthird [maven-release-plugin]\n");

		assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect multiline message");

		// should be enough, but let's test more

		build(project, Result.SUCCESS);

		assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");
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
