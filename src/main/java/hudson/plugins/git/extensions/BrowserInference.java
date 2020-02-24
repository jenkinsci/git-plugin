package hudson.plugins.git.extensions;

import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import org.apache.tools.ant.ExtensionPoint;

public abstract class BrowserInference extends ExtensionPoint {

    private GitRepositoryBrowser browser;

    public BrowserInference(){
        browser = null;
    }

    public BrowserInference(GitRepositoryBrowser browser){
        this.browser = browser;
    }

    String inferRepositoryBrowser(){
            return "no-browser";
    }
}
