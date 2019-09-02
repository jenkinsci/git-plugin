package hudson.plugins.git.extensions;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

    @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification="Jenkins.getInstance() is not null")
    public static DescriptorExtensionList<GitSCMExtension,GitSCMExtensionDescriptor> all() {
        return Jenkins.get().getDescriptorList(GitSCMExtension.class);
    }
}
