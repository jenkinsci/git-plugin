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

    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Use commit author in changelog";
        }
    }
}
