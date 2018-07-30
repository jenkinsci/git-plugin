package hudson.plugins.git;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * User-provided configuration that dictates which branch in which repository we'll be
 * merging (to the commit being built.)
 *
 */
public class UserMergeOptions extends AbstractDescribableImpl<UserMergeOptions>  implements Serializable {

    private String mergeRemote;
    private final String mergeTarget;
    private String mergeStrategy;
    private MergeCommand.GitPluginFastForwardMode fastForwardMode;

    /**
     * @deprecated use the new constructor that allows to set the fast forward mode.
     * @param mergeRemote remote name used for merge
     * @param mergeTarget remote branch to be merged into current branch
     * @param mergeStrategy merge strategy to be used
     */
    @Deprecated
    public UserMergeOptions(String mergeRemote, String mergeTarget, String mergeStrategy) {
        this(mergeRemote, mergeTarget, mergeStrategy, MergeCommand.GitPluginFastForwardMode.FF);
    }

    /**
     * @param mergeRemote remote name used for merge
     * @param mergeTarget remote branch to be merged into current branch
     * @param mergeStrategy merge strategy
     * @param fastForwardMode fast forward mode
     */
    public UserMergeOptions(String mergeRemote, String mergeTarget, String mergeStrategy,
            MergeCommand.GitPluginFastForwardMode fastForwardMode) {
        this.mergeRemote = mergeRemote;
        this.mergeTarget = mergeTarget;
        this.mergeStrategy = mergeStrategy;
        this.fastForwardMode = fastForwardMode;
    }

    @DataBoundConstructor
    public UserMergeOptions(String mergeTarget) {
        this.mergeTarget = mergeTarget;
    }

    /**
     * Construct UserMergeOptions from PreBuildMergeOptions.
     * @param pbm pre-build merge options used to construct UserMergeOptions
     */
    public UserMergeOptions(PreBuildMergeOptions pbm) {
        this(pbm.getRemoteBranchName(), pbm.getMergeTarget(), pbm.getMergeStrategy().toString(), pbm.getFastForwardMode());
    }

    /**
     * Repository name, such as 'origin' that designates which repository the branch lives in.
     * @return repository name
     */
    public String getMergeRemote() {
        return mergeRemote;
    }

    @DataBoundSetter
    public void setMergeRemote(String mergeRemote) {
        this.mergeRemote = Util.fixEmptyAndTrim(mergeRemote);
    }

    /**
     * Ref in the repository that becomes the input of the merge.
     * Normally a branch name like 'master'.
     * @return branch name from which merge will be performed
     */
    public String getMergeTarget() {
        return mergeTarget;
    }

    /**
     * Ref in the repository that becomes the input of the merge, a
     * slash separated concatenation of merge remote and merge target.
     * @return ref from which merge will be performed
     */
    public String getRef() {
        return mergeRemote + "/" + mergeTarget;
    }

    public MergeCommand.Strategy getMergeStrategy() {
        for (MergeCommand.Strategy strategy: MergeCommand.Strategy.values())
            if (strategy.toString().equals(mergeStrategy))
                return strategy;
        return MergeCommand.Strategy.DEFAULT;
    }

    @DataBoundSetter
    public void setMergeStrategy(MergeCommand.Strategy mergeStrategy) {
        this.mergeStrategy = mergeStrategy.toString(); // not .name() as you might expect! TODO in Turkey this will be e.g. recursıve
    }

    public MergeCommand.GitPluginFastForwardMode getFastForwardMode() {
        for (MergeCommand.GitPluginFastForwardMode ffMode : MergeCommand.GitPluginFastForwardMode.values())
            if (ffMode.equals(fastForwardMode))
                return ffMode;
        return MergeCommand.GitPluginFastForwardMode.FF;
    }

    @DataBoundSetter
    public void setFastForwardMode(MergeCommand.GitPluginFastForwardMode fastForwardMode) {
        this.fastForwardMode = fastForwardMode;
    }

    @Override
    public String toString() {
        return "UserMergeOptions{" +
                "mergeRemote='" + mergeRemote + '\'' +
                ", mergeTarget='" + mergeTarget + '\'' +
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

        return false;
    }

    @Override
    public int hashCode() {
        int result = mergeRemote != null ? mergeRemote.hashCode() : 0;
        result = 31 * result + (mergeTarget != null ? mergeTarget.hashCode() : 0);
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

    }
}
