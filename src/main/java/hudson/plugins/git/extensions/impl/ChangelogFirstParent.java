package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.ChangeLogSet.Entry;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * When calculating the changelog, if there are merge commits in the changelog,
 * only follow the first parent, not any additional parents to keep the changelog
 * limited to changes on the currently checked out branch only.
 *
 * @author Ben Sluis
 */
public class ChangelogFirstParent extends FakeGitSCMExtension {

    @DataBoundConstructor
    public ChangelogFirstParent() {
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
        return o instanceof ChangelogFirstParent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return ChangelogFirstParent.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "ChangelogFirstParent{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Use --first-parent when determining changelog";
        }
    }
}
