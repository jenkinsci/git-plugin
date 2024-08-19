package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Messages;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * Instead of checking out to the top of the workspace, check out somewhere else.
 *
 * @author Marc Guenther
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class RelativeTargetDirectory extends GitSCMExtension {
    private String relativeTargetDir;

    @DataBoundConstructor
    public RelativeTargetDirectory(String relativeTargetDir) {
        this.relativeTargetDir = relativeTargetDir;
    }

    public String getRelativeTargetDir() {
        return relativeTargetDir;
    }

    @Override
    @CheckForNull
    public String getDeprecationAlternative() {
        // This extension is not intended to be used in Pipeline
        return "Use 'dir()' or 'ws()' to checkout into a different Pipeline workspace directory.";
    }

    @Override
    public FilePath getWorkingDirectory(GitSCM scm, Job<?, ?> context, FilePath workspace, EnvVars environment, TaskListener listener) throws IOException, InterruptedException, GitException {
        if (relativeTargetDir == null || relativeTargetDir.length() == 0 || relativeTargetDir.equals(".")) {
            return workspace;
        }
        return workspace.child(environment.expand(relativeTargetDir));
    }

    @Extension
    // No @Symbol annotation because relative target directory is done in Pipeline with the `dir` step
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.check_out_to_a_sub_directory();
        }
    }
}
