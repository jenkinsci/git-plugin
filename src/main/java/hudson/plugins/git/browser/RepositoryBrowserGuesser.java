package hudson.plugins.git.browser;

import org.eclipse.jgit.transport.URIish;

import hudson.plugins.git.browser.GithubWeb.GithubWebRepositoryGuesser;
import hudson.plugins.git.browser.Stash.StashRepositoryGuesser;
import hudson.scm.RepositoryBrowser;

public class RepositoryBrowserGuesser {
	public static RepositoryBrowser<?> guess(URIish uri) {
		String repoUrl;
		if ( (repoUrl = new GithubWebRepositoryGuesser().getRepoUrl(uri)) != null) {
			return new GithubWeb(repoUrl);
		} else if ( (repoUrl = new StashRepositoryGuesser().getRepoUrl(uri)) != null) {
			return new Stash(repoUrl);
		}
		return null;
	}
}
