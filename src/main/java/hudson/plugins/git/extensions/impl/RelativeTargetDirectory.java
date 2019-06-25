package hudson.plugins.git.extensions.impl;

import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

/**
 * Instead of checking out to the top of the workspace, check out somewhere else.
 *
 * @author Marc Guenther
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class RelativeTargetDirectory extends GitSCMExtension {
    private String relativeTargetDir;
    private boolean basename = false;

    @DataBoundConstructor
    public RelativeTargetDirectory(String relativeTargetDir, boolean basename) {
        this.relativeTargetDir = relativeTargetDir;
        this.basename = basename;
    }

    public RelativeTargetDirectory(String relativeTargetDir) {
        this.relativeTargetDir = relativeTargetDir;
    }

    public String getRelativeTargetDir() {
        return relativeTargetDir;
    }

    public boolean isBasename() {
        return basename;
    }

    @Override
    public FilePath getWorkingDirectory(GitSCM scm, Job<?, ?> context, FilePath workspace, EnvVars environment, TaskListener listener)
            throws IOException, InterruptedException, GitException {
        if (relativeTargetDir == null || relativeTargetDir.length() == 0 || relativeTargetDir.equals(".")) {
            return workspace;
        }
        String expand = environment.expand(relativeTargetDir);
        if (basename) {
            expand = FilenameUtils.getBaseName(expand);
        }
        return workspace.child(expand);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Check out to a sub-directory";
        }
    }
}
