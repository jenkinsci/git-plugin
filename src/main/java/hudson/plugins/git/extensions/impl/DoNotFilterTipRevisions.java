package hudson.plugins.git.extensions.impl;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

public class DoNotFilterTipRevisions extends GitSCMExtension {

    @DataBoundConstructor
    public DoNotFilterTipRevisions() { }
    
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Don't filter branches that are an ancestor of another branch";
        }
    }
}
