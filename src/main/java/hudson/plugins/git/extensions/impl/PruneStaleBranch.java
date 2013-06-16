package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.eclipse.jgit.transport.RemoteConfig;
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
    public void onCheckoutCompleted(GitSCM scm, AbstractBuild<?, ?> build, Launcher launcher, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Pruning obsolete local branches");
        for (RemoteConfig remoteRepository : scm.getRepositories()) {
            git.prune(remoteRepository);
        }
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Prune stale remote-tracking branches";
        }
    }
}
