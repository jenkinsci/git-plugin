package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Per Böhlin
 */
public class PollExclusionTest extends GitSCMExtensionTest{

	FreeStyleProject project;
	TestGitRepo repo;

	@Override
	public void before() throws Exception {
		repo = new TestGitRepo("repo", tmp.newFolder(), listener);
		project = setupBasicProject(repo);
	}

	@Override
	protected GitSCMExtension getExtension() {
		return new PollExclusion();
	}

	@Test
	public void test() throws Exception {

		repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");

		assertTrue("Should always build when there are no builds", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);

		assertFalse("scm polling should never detect changes for the repository", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, "second commit");

		assertFalse("scm polling should never detect changes for the repository", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);

		assertFalse("scm polling should never detect changes for the repository", project.poll(listener).hasChanges());

	}
}
