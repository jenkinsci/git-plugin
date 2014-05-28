package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * Force a re-clone.
 *
 * @author Kohsuke Kawaguchi
 */
public class WipeWorkspace extends GitSCMExtension {
    @DataBoundConstructor
    public WipeWorkspace() {
    }

    @Override
    public void beforeCheckout(GitSCM scm, Run<?, ?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Wiping out workspace first.");
        git.getWorkTree().deleteContents();
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Wipe out repository & force clone";
        }
    }
}
