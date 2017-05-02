package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
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
    @CheckForNull
    private String localBranch;

    @DataBoundConstructor
    public LocalBranch(@CheckForNull String localBranch) {
        this.localBranch = Util.fixEmptyAndTrim(localBranch);
    }

    @CheckForNull
    public String getLocalBranch() {
        return localBranch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LocalBranch that = (LocalBranch) o;

        return localBranch != null ? localBranch.equals(that.localBranch) : that.localBranch == null;
    }

    @Override
    public int hashCode() {
        return LocalBranch.class.hashCode();
    }

    @Override
    public String toString() {
        return "LocalBranch{" +
                (localBranch == null || "**".equals(localBranch) ? "same-as-remote" : "localBranch='"+localBranch+"'")
                + '}';
    }


    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Check out to specific local branch";
        }
    }
}
