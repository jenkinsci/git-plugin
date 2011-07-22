package hudson.plugins.git;

import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

public class UserMergeOptions implements Serializable {

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
}
