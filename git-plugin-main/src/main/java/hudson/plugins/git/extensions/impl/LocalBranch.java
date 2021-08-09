package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.Util;
import hudson.plugins.git.Messages;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.util.Objects;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
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
    private final String localBranch;

    @DataBoundConstructor
    public LocalBranch(@CheckForNull String localBranch) {
        this.localBranch = Util.fixEmptyAndTrim(localBranch);
    }

    @CheckForNull
    @Whitelisted
    public String getLocalBranch() {
        return localBranch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LocalBranch that = (LocalBranch) o;

        return Objects.equals(localBranch, that.localBranch);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(localBranch);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "LocalBranch{" +
                (localBranch == null || "**".equals(localBranch) ? "same-as-remote" : "localBranch='"+localBranch+"'")
                + '}';
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.check_out_to_specific_local_branch();
        }
    }
}
