package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * When used with {@link MultiSCM}, this differentiates a different instance.
 *
 * @author Kohsuke Kawaguchi
 */
public class ScmName extends FakeGitSCMExtension {
    private final String name;

    @DataBoundConstructor
    public ScmName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Custom SCM name";
        }
    }
}
