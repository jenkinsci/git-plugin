package hudson.plugins.git.extensions.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;

public class AuthorInChangelogTest extends GitSCMExtensionTest {

	final PersonIdent peterPan = new PersonIdent("Peter Pan", "peter@pan.com");

	FreeStyleProject project;
	TestGitRepo repo;

	@Override
	public void before() throws Exception {
		repo = new TestGitRepo("repo", tmp.newFolder(), listener);
	}

	@Override
	protected GitSCMExtension getExtension() {
		return new AuthorInChangelog();
	}

	@Test
	public void useAuthorForChangeLogUser() throws Exception {
		project = setupBasicProject(repo);

		repo.commit("repo-init", repo.janeDoe, repo.johnDoe, "repo0 initial commit");

		FreeStyleBuild build1 = build(project, Result.SUCCESS);

		assertTrue("initial build should not contain any changes", build1.getChangeSet().isEmptySet());

		repo.commit("A", repo.janeDoe, repo.johnDoe, "#1 change by author jane");
		repo.commit("B", repo.johnDoe, repo.janeDoe, "#2 change by author john");
		repo.commit("C", repo.janeDoe, peterPan, "#3 change by author jane");
		repo.commit("D", peterPan, repo.janeDoe, "#4 change by author peter");

		FreeStyleBuild build2 = build(project, Result.SUCCESS);

		assertEquals("further build should contain four changes", 4, build2.getChangeSet().getItems().length);
		assertTrue("First change should have user jane", checkChangeSetUser(build2.getChangeSet().getItems()[0], repo.janeDoe));
		assertTrue("Second change should have user john", checkChangeSetUser(build2.getChangeSet().getItems()[1], repo.johnDoe));
		assertTrue("Third change should have user jane", checkChangeSetUser(build2.getChangeSet().getItems()[2], repo.janeDoe));
		assertTrue("Fourth change should have user peter", checkChangeSetUser(build2.getChangeSet().getItems()[3], peterPan));

	}

	public boolean checkChangeSetUser(Object changeSetItem, PersonIdent expectedUser) {
		GitChangeSet changeSet = (GitChangeSet) changeSetItem;
		return expectedUser.getName().equals(changeSet.getAuthorName());
	}

}
