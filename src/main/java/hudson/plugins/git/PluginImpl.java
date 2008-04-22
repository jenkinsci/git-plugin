package hudson.plugins.git;

import hudson.Plugin;
import hudson.plugins.git.browser.GitWeb;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCMS;
import hudson.tasks.BuildStep;

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
        BuildStep.PUBLISHERS.add(GitPublisher.DESCRIPTOR);
     }
}
