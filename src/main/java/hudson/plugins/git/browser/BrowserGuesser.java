package hudson.plugins.git.browser;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

public abstract class BrowserGuesser implements ExtensionPoint {

    public abstract GitRepositoryBrowser guessBrowser(String url);

    public static ExtensionList<BrowserGuesser> all() {
        return Jenkins.get().getExtensionList(BrowserGuesser.class);
    }

    public static GitRepositoryBrowser getBrowser(String url){
        ExtensionList<BrowserGuesser> browserExtensions = BrowserGuesser.all();

        for (BrowserGuesser ext : browserExtensions) {
            GitRepositoryBrowser browser = ext.guessBrowser(url);
            if (browser != null)
                return browser;
        }
        return null;
    }
}
