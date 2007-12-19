package hudson.plugins.git;

import hudson.Plugin;
import hudson.plugins.git.browser.GitWeb;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCMS;

/**
 * Plugin entry point.
 *
 * @author Nigel Magnay
 * @plugin
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        SCMS.SCMS.add(GitSCM.DescriptorImpl.DESCRIPTOR);
        RepositoryBrowsers.LIST.add(GitWeb.DESCRIPTOR);
     }
}
