package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.plugins.git.extensions.impl.AuthorInChangelog;
import org.kohsuke.stapler.DataBoundConstructor;

public class AuthorInChangelogTrait extends GitSCMExtensionTrait<AuthorInChangelog> {
    @DataBoundConstructor
    public AuthorInChangelogTrait(AuthorInChangelog extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        @Override
        public String getDisplayName() {
            return "Use commit author in changelog";
        }
    }
}
