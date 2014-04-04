package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * git-clean before the checkout.
 *
 * @author David S Wang
 */
public class CleanBeforeCheckout extends GitSCMExtension {
    @DataBoundConstructor
    public CleanBeforeCheckout() {
    }

    @Override
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Cleaning workspace");
        git.clean();
        // TODO: revisit how to hand off to SubmoduleOption
        for (GitSCMExtension ext : scm.getExtensions()) {
            ext.onClean(scm, git);
        }
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Clean before checkout";
        }
    }
}
