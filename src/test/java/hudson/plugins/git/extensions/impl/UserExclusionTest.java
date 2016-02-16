package hudson.plugins.git.extensions.impl;

import java.util.Arrays;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Kanstantsin Shautsou
 */
public class UserExclusionTest extends GitSCMExtensionTest{

	FreeStyleProject project;
	TestGitRepo repo;

	@Override
	public void before() throws Exception {
		repo = new TestGitRepo("repo", tmp.newFolder(), listener);
	}

	@Override
	protected GitSCMExtension getExtension() {
		return new UserExclusion("Jane Doe");
	}

	@Test
	public void test() throws Exception {
		project = setupBasicProject(repo);

		repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");

		assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, "excluded user commit");

		assertFalse("scm polling should ignore excluded committer user", project.poll(listener).hasChanges());

		// should be enough, but let's test more

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
	}

	@Test
	public void testInConcertWithAuthorInChangelog() throws Exception {
		project = setupBasicProject(repo, Arrays.<GitSCMExtension>asList(new AuthorInChangelog()));

		repo.commit("repo-init", repo.johnDoe, repo.janeDoe, "repo0 initial commit");

		assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, repo.johnDoe, "excluded user commit");

		assertFalse("scm polling should ignore excluded author user", project.poll(listener).hasChanges());

		// should be enough, but let's test more

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
	}

}
