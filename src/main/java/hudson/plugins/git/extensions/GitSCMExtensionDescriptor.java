package hudson.plugins.git.extensions;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import hudson.plugins.git.GitSCM;
import jenkins.model.Jenkins;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class GitSCMExtensionDescriptor extends Descriptor<GitSCMExtension> {
    public boolean isApplicable(Class<? extends GitSCM> type) {
        return true;
    }

    public static DescriptorExtensionList<GitSCMExtension,GitSCMExtensionDescriptor> all() {
        return Jenkins.get().getDescriptorList(GitSCMExtension.class);
    }
}
