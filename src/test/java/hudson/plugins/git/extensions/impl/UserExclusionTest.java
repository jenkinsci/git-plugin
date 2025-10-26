package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
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
class UserExclusionTest extends GitSCMExtensionTest{

	private FreeStyleProject project;
	private TestGitRepo repo;

	@Override
	protected void before() throws Exception {
		repo = new TestGitRepo("repo", newFolder(tmp, "junit"), listener);
		project = setupBasicProject(repo);
	}

	@Override
	protected GitSCMExtension getExtension() {
		return new UserExclusion("Jane Doe");
	}

    @Test
    void test() throws Exception {

		repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");

		assertTrue(project.poll(listener).hasChanges(), "scm polling should detect a change after initial commit");

		build(project, Result.SUCCESS);

		assertFalse(project.poll(listener).hasChanges(), "scm polling should not detect any more changes after build");

		repo.commit("repo-init", repo.janeDoe, "excluded user commit");

		assertFalse(project.poll(listener).hasChanges(), "scm polling should ignore excluded user");

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
