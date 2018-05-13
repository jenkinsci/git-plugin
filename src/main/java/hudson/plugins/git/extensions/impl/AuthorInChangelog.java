package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.ChangeLogSet.Entry;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Use author, not committer as the {@link Entry#getAuthor()} of the commit.
 *
 * @author Kohsuke Kawaguchi
 */
public class AuthorInChangelog extends FakeGitSCMExtension {

    @DataBoundConstructor
    public AuthorInChangelog() {
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
        return o instanceof AuthorInChangelog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return AuthorInChangelog.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "AuthorInChangelog{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Use commit author in changelog";
        }
    }
}
