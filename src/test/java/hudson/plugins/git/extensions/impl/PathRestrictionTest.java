package hudson.plugins.git.extensions.impl;

/**
 * @author Kanstantsin Shautsou
 */

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathRestrictionTest extends GitSCMExtensionTest {
	protected FreeStyleProject project;
	protected TestGitRepo repo;

	@Override
	protected GitSCMExtension getExtension() {
		return new PathRestriction("included/.*", "excluded/.*");
	}

	@Override
	public void before() throws Exception {
		repo = new TestGitRepo("repo", tmp.newFolder(), listener);
		project = setupBasicProject(repo);
	}

	@Test
	public void test() throws Exception {
		repo.commit("included/file", repo.johnDoe, "repo0 initial commit");

		assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect changes after initial build", project.poll(listener).hasChanges());

		repo.commit("excluded/file1", repo.janeDoe, "excluded/file1 commit");

		assertFalse("scm polling should not detect excluded/file1 commit", project.poll(listener).hasChanges());

		repo.commit("excluded/file2", repo.janeDoe, "excluded/file2 commit");

		assertFalse("scm polling should ignore multiple excluded commits", project.poll(listener).hasChanges());

		// should be enough, but let's test more

		build(project, Result.SUCCESS);

		repo.commit("excluded/file3", repo.janeDoe, "excluded/file3 commit");
		repo.commit("excluded/file4", repo.janeDoe, "excluded/file4 commit");
		repo.commit("excluded/file5", repo.janeDoe, "excluded/file5 commit");

		assertFalse("scm polling should ignore multiple excluded commits at once", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
	}
}
