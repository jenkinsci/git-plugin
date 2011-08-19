package hudson.plugins.git;

import java.io.Serializable;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class UserMergeOptions extends AbstractDescribableImpl<UserMergeOptions>  implements Serializable {

    private String mergeRemote;
    private String mergeTarget;

    @DataBoundConstructor
    public UserMergeOptions(String mergeRemote, String mergeTarget) {
        this.mergeRemote = mergeRemote;
        this.mergeTarget = mergeTarget;
    }

    public String getMergeRemote() {
        return mergeRemote;
    }

    public String getMergeTarget() {
        return mergeTarget;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<UserMergeOptions> {
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
