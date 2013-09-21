package hudson.plugins.git;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.Serializable;

/**
 * User-provided configuration that dictates which branch in which repository we'll be
 * merging (to the commit being built.)
 *
 */
public class UserMergeOptions extends AbstractDescribableImpl<UserMergeOptions>  implements Serializable {

    private String mergeRemote;
    private String mergeTarget;
    private String mergeStrategy;

    @DataBoundConstructor
    public UserMergeOptions(String mergeRemote, String mergeTarget, String mergeStrategy) {
        this.mergeRemote = mergeRemote;
        this.mergeTarget = mergeTarget;
        this.mergeStrategy = mergeStrategy;
    }

    public UserMergeOptions(PreBuildMergeOptions pbm) {
        this(pbm.getRemoteBranchName(),pbm.getMergeTarget(),pbm.getMergeStrategy().toString());
    }

    /**
     * Repository name, such as 'origin' that designates which repository the branch lives in.
     */
    public String getMergeRemote() {
        return mergeRemote;
    }

    /**
     * Ref in the repository that becomes the input of the merge.
     * Normally a branch name like 'master'.
     */
    public String getMergeTarget() {
        return mergeTarget;
    }

    public String getRef() {
        return mergeRemote + "/" + mergeTarget;
    }

    public MergeCommand.Strategy getMergeStrategy() {
        for (MergeCommand.Strategy strategy: MergeCommand.Strategy.values())
            if (strategy.toString().equals(mergeStrategy))
                return strategy;
        return MergeCommand.Strategy.DEFAULT;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<UserMergeOptions> {
        @Override
        public String getDisplayName() {
            return "";
        }

        public ListBoxModel doFillMergeStrategyItems() {
            ListBoxModel m = new ListBoxModel();
            for (MergeCommand.Strategy strategy: MergeCommand.Strategy.values())
                m.add(strategy.toString(), strategy.toString());
            return m;
        }
    }
}
