package hudson.plugins.git.extensions.impl;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.browser.GitLab;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import jenkins.model.Jenkins;

public class BrowserGuesser implements ExtensionPoint {
    public GitRepositoryBrowser guessBrowser(String url){
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
    public static ExtensionList<BrowserGuesser> all() {
        return Jenkins.getInstanceOrNull().getExtensionList(BrowserGuesser.class);
    }
}
