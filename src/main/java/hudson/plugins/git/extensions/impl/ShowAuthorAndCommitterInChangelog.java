package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * When enabled, the changelog view displays both the Git author and committer
 * with explicit labels, instead of showing only one identity.
 *
 * @since TODO
 */
public class ShowAuthorAndCommitterInChangelog extends FakeGitSCMExtension {

    @DataBoundConstructor
    public ShowAuthorAndCommitterInChangelog() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return o instanceof ShowAuthorAndCommitterInChangelog;
    }

    @Override
    public int hashCode() {
        return ShowAuthorAndCommitterInChangelog.class.hashCode();
    }

    @Override
    public String toString() {
        return "ShowAuthorAndCommitterInChangelog{}";
    }

    @Symbol("showAuthorAndCommitterInChangelog")
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return "Show both author and committer in changelog";
        }
    }
}
