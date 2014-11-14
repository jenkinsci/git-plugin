package hudson.plugins.git.extensions.impl;

import hudson.model.*;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Kanstantsin Shautsou
 * based on {@link hudson.plugins.git.MultipleSCMTest}
 */
public class MessageExclusionTest extends GitSCMExtensionTest {
	protected FreeStyleProject project;
	protected TestGitRepo repo;

	@Override
	protected GitSCMExtension getExtension() {
		return new MessageExclusion("(?s).*\\[maven-release-plugin\\].*");
	}

	@Override
	public void before() throws Exception {
		repo = new TestGitRepo("repo", tmp.newFolder(), listener);
		project = setupBasicProject(repo);
	}

	@Test
	public void test() throws Exception {
		repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");

		assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, " [maven-release-plugin] excluded message commit");

		assertFalse("scm polling should not detect excluded message", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, "first line in excluded commit\nsecond\nthird [maven-release-plugin]\n");

		assertFalse("scm polling should not detect multiline message", project.poll(listener).hasChanges());

		// should be enough, but let's test more

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
	}
}
