package hudson.plugins.git.extensions.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;

/**
 * {@link BranchRestrictionTest} verifies that whitelisting and blacklisting of branches works as expected.
 *
 * @author Marcus Klein
 */
@RunWith(Enclosed.class)
public class BranchRestrictionTest {

	public abstract static class BranchRestrictionExtensionTest extends GitSCMExtensionTest {

        protected FreeStyleProject project;
        protected TestGitRepo repo;

        @Override
        public void before() throws Exception {
    		repo = new TestGitRepo("repo", tmp.newFolder(), listener);
    		project = setupBasicProject(repo, Collections.singletonList(new BranchSpec("*")));
        }
    }

	public static class WhitelistTest extends BranchRestrictionExtensionTest {

		@Override
		protected GitSCMExtension getExtension() {
			return new BranchRestriction("develop", "");
		}

		@Test
		public void testWhitelist() throws Exception {
			repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
			assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());
			build(project, Result.SUCCESS);
			assertFalse("scm polling should not detect any more changes after initial build", project.poll(listener).hasChanges());

			// Commit on master should not trigger a build
			repo.commit("file2", repo.janeDoe, "commit2");
			assertFalse("scm polling should avoid build on master", project.poll(listener).hasChanges());
			// no build on master branch

			repo.git.checkout().ref("master").branch("develop").execute();
			repo.commit("testOnDevelop", repo.johnDoe, "on develop");
			assertTrue("scm polling should detect a change on the develop branch",  project.poll(listener).hasChanges());
			build(project, Result.SUCCESS);

			assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
		}
	}

	public static class BlacklistTest extends BranchRestrictionExtensionTest {

		@Override
		protected GitSCMExtension getExtension() {
			return new BranchRestriction("", "develop");
		}

		@Test
		public void testWhitelist() throws Exception {
			repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");
			assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());
			build(project, Result.SUCCESS);
			assertFalse("scm polling should not detect any more changes after initial build", project.poll(listener).hasChanges());

			// Switch to develop and do a commit
			repo.git.checkout().ref("master").branch("develop").execute();
			repo.commit("testOnDevelop", repo.johnDoe, "on develop");
			assertFalse("scm polling should avoid build on develop", project.poll(listener).hasChanges());

			// Back to master and a new commit
			repo.git.checkout().ref("master").execute();
			repo.commit("file2", repo.janeDoe, "commit2 on master");
			assertTrue("scm polling should detect a change on the master branch",  project.poll(listener).hasChanges());
			build(project, Result.SUCCESS);

			assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
		}
	}
}
