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
 * git-lfs-pull after the checkout.
 *
 * @author Matt Hauck
 */
public class GitLFSPull extends GitSCMExtension {
    @DataBoundConstructor
    public GitLFSPull() {
    }

    @Override
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Enabling Git LFS pull");
        cmd.withLFS();
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Git LFS pull after checkout";
        }
    }
}
