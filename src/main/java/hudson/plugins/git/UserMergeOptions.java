package hudson.plugins.git;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;

/**
 * User-provided configuration that dictates which branch in which repository we'll be
 * merging (to the commit being built.)
 *
 */
public class UserMergeOptions extends AbstractDescribableImpl<UserMergeOptions>  implements Serializable {

    private String mergeRemote;
    private String mergeTarget;
    private String mergeSource;
    private String mergeStrategy;
    private MergeCommand.GitPluginFastForwardMode fastForwardMode;
    /**
     * @deprecated use the new constructor that allows to set the fast forward mode.
     */
    @Deprecated
    public UserMergeOptions(String mergeRemote, String mergeTarget, String mergeSource, String mergeStrategy) {
        this(mergeRemote, mergeTarget, mergeSource, mergeStrategy, MergeCommand.GitPluginFastForwardMode.FF);
    }
    @DataBoundConstructor
    public UserMergeOptions(String mergeRemote, String mergeTarget, String mergeSource, String mergeStrategy,
            MergeCommand.GitPluginFastForwardMode fastForwardMode) {
        this.mergeRemote = mergeRemote;
        this.mergeTarget = mergeTarget;
        this.mergeSource = mergeSource;
        this.mergeStrategy = mergeStrategy;
        this.fastForwardMode = fastForwardMode;
    }

    public UserMergeOptions(PreBuildMergeOptions pbm) {
        this(pbm.getRemoteBranchName(), pbm.getMergeTarget(), pbm.getMergeSource(), pbm.getMergeStrategy().toString(), pbm.getFastForwardMode());
    }

    /**
     * Ref in the repository that becomes the input of the merge. The publisher will not push to this branch.
     * Normally a branch name like 'master'.
     */
    public String getMergeSource() {
        return mergeSource;
    }

    /**
     * Repository name, such as 'origin' that designates which repository the branch lives in.
     */
    public String getMergeRemote() {
        return mergeRemote;
    }

    /**
     * Ref in the repository that becomes the input of the merge. The publisher will push to this branch.
     * Normally a branch name like 'master'.
     */
    public String getMergeTarget() {
        return mergeTarget;
    }

    public String getRef() {
        final StringBuilder refBuilder = new StringBuilder(mergeRemote).append("/");
        if (StringUtils.isNotBlank(mergeTarget)) {
            refBuilder.append(mergeTarget);
        } else {
            refBuilder.append(mergeSource);
        }
        return refBuilder.toString();
    }

    public MergeCommand.Strategy getMergeStrategy() {
        for (MergeCommand.Strategy strategy: MergeCommand.Strategy.values())
            if (strategy.toString().equals(mergeStrategy))
                return strategy;
        return MergeCommand.Strategy.DEFAULT;
    }

    public MergeCommand.GitPluginFastForwardMode getFastForwardMode() {
        for (MergeCommand.GitPluginFastForwardMode ffMode : MergeCommand.GitPluginFastForwardMode.values())
            if (ffMode.equals(fastForwardMode))
                return ffMode;
        return MergeCommand.GitPluginFastForwardMode.FF;
    }

    @Override
    public String toString() {
        return "UserMergeOptions{" +
                "mergeRemote='" + mergeRemote + '\'' +
                ", mergeTarget='" + mergeTarget + '\'' +
                ", mergeSource='" + mergeSource + '\'' +
                ", mergeStrategy='" + mergeStrategy + '\'' +
                ", fastForwardMode='" + fastForwardMode + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof UserMergeOptions) {
            UserMergeOptions that = (UserMergeOptions) other;
            if ((mergeRemote != null && mergeRemote.equals(that.mergeRemote))
                    || (mergeRemote == null && that.mergeRemote == null)) {
                if ((mergeTarget != null && mergeTarget.equals(that.mergeTarget))
                        || (mergeTarget == null && that.mergeTarget == null)) {
                    if ((mergeSource != null && mergeSource.equals(that.mergeSource))
                            || (mergeSource == null && that.mergeSource == null)) {
                        if ((mergeStrategy != null && mergeStrategy.equals(that.mergeStrategy))
                                || (mergeStrategy == null && that.mergeStrategy == null)) {
                            if ((fastForwardMode != null && fastForwardMode.equals(that.fastForwardMode))
                                    || (fastForwardMode == null && that.fastForwardMode == null)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = mergeRemote != null ? mergeRemote.hashCode() : 0;
        result = 31 * result + (mergeTarget != null ? mergeTarget.hashCode() : 0);
        result = 31 * result + (mergeSource != null ? mergeSource.hashCode() : 0);
        result = 31 * result + (mergeStrategy != null ? mergeStrategy.hashCode() : 0);
        result = 31 * result + (fastForwardMode != null ? fastForwardMode.hashCode() : 0);
        return result;
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

        public FormValidation doCheckBranchToMerge(@QueryParameter String mergeTarget, @QueryParameter String mergeSource) {
            if (StringUtils.isBlank(mergeTarget) && StringUtils.isBlank(mergeSource)) {
                return FormValidation.error("Either Branch to merge to or Branch to merge from is required.");
            }
            if (StringUtils.isNotBlank(mergeTarget) && StringUtils.isNotBlank(mergeSource)) {
                return FormValidation.error("One of Branch to merge to or Branch to merge from can be defined. Not both.");
            }
            return FormValidation.ok();
        }
    }
}
