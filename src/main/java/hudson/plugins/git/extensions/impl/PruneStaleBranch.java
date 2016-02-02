package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * Prune stale remote-tracking branches
 *
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class PruneStaleBranch extends GitSCMExtension {
    @DataBoundConstructor
    public PruneStaleBranch() {
    }

    @Override
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Pruning obsolete local branches");
        cmd.prune();
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Prune stale remote-tracking branches";
        }
    }
}
