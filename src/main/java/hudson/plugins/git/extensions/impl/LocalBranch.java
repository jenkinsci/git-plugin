package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.Util;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The Git plugin checks code out to a detached head.  Configure
 * LocalBranch to force checkout to a specific local branch.
 * Configure this extension as null or as "**" to signify that
 * the local branch name should be the same as the remote branch
 * name sans the remote repository prefix (origin for example).
 * 
 * @author Kohsuke Kawaguchi
 */
public class LocalBranch extends FakeGitSCMExtension {
    private String localBranch;

    @DataBoundConstructor
    public LocalBranch(String localBranch) {
        this.localBranch = Util.fixEmptyAndTrim(localBranch);
    }

    public String getLocalBranch() {
        return localBranch;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Check out to specific local branch, null to use remote branch name.";
        }
    }
}