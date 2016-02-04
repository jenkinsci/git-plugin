package hudson.plugins.git.extensions.impl;

import hudson.model.Run;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;

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
 * Git SCM skip checkout on Matrix configuration child jobs
 *
 */
public class ExcludeMatrixChilds extends GitSCMExtension {
    @DataBoundConstructor
    public ExcludeMatrixChilds() {
    }
    
    @Override
    public boolean doCheckout(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        
        boolean doCheckout = true;
        
        // Checkout should not be done for Matrix child jobs
        if (build instanceof MatrixRun) {
			listener.getLogger().println("[ExcludeMatrixChilds] Build is a Matrix child job, skipping checkout...");
			doCheckout = false;
		} else {
			listener.getLogger().println("[ExcludeMatrixChilds] Build is a Matrix parent job, executing as normal...");
		}
		
		return doCheckout;
    }
    
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Exclude Matrix Childs";
        }
    }
}

