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
    private boolean includeInsteadOfExclude;
    private boolean partialMatch;
    private String messagePattern = "(?s).*\\[maven-release-plugin\\].*";
    
    
	@Override
	protected GitSCMExtension getExtension() {
		return new MessageExclusion(messagePattern, includeInsteadOfExclude, partialMatch);
	}

	@Override
	public void before() throws Exception {
        repo = new TestGitRepo("repo", tmp.newFolder(), listener);
	}

	@Test
	public void testLegacyExclusion() throws Exception {
        project = setupBasicProject(repo);

		repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
        build(project, Result.SUCCESS); // first build is always included

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, " [maven-release-plugin] excluded message commit");

		assertFalse("scm polling should not detect excluded message", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, "first line in excluded commit\nsecond\nthird [maven-release-plugin]\n");

		assertFalse("scm polling should not detect multiline message", project.poll(listener).hasChanges());

		// should be enough, but let's test more

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
	}

    @Test
    public void testPartialMatches() throws Exception {
        partialMatch = true;
        messagePattern = "TOKEN";
        project = setupBasicProject(repo);

        repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
        build(project, Result.SUCCESS); // first build is always included

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        repo.commit("repo-init", repo.janeDoe, " TOKEN excluded message commit");

        assertFalse("scm polling should not detect excluded message", project.poll(listener).hasChanges());

        repo.commit("repo-init", repo.janeDoe, "first line in excluded commit\nsecond\nthird TOKEN\n");

        assertFalse("scm polling should not detect multiline message", project.poll(listener).hasChanges());
    }

    @Test
    public void testLegacyExclusionWithInvert() throws Exception {
        includeInsteadOfExclude = true;
        project = setupBasicProject(repo);

        repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
        build(project, Result.SUCCESS); // first build is never excluded

        repo.commit("repo-init", repo.janeDoe, " [maven-release-plugin] excluded message commit");

        assertTrue("scm polling should detect excluded message", project.poll(listener).hasChanges());

        repo.commit("repo-init", repo.janeDoe, "first line in excluded commit\nsecond\nthird [maven-release-plugin]\n");

        assertTrue("scm polling should detect multiline message", project.poll(listener).hasChanges());
    }
}
