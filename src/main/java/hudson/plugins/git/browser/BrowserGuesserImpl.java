package hudson.plugins.git.browser;

import hudson.Extension;

@Extension
public class BrowserGuesserImpl extends BrowserGuesser{
    @Override
    public GitRepositoryBrowser guessBrowser(String url) {
        if (url.startsWith("https://bitbucket.org/")) {
            return new BitbucketWeb(url);
        }
        if (url.startsWith("https://gitlab.com/")) {
            return new GitLab(url);
        }
        if (url.startsWith("https://github.com/")) {
            return new GithubWeb(url);
        }
        return null;
    }
}
