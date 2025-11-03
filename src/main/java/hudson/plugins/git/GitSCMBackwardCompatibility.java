package hudson.plugins.git;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.RelativePath;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.extensions.impl.*;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.scm.SCM;
import hudson.util.DescribableList;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * This is a portion of {@link GitSCM} for the stuff that's used to be in {@link GitSCM}
 * that are deprecated. Moving deprecated stuff from {@link GitSCM} to here allows us
 * to keep {@link GitSCM} cleaner.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"deprecation", "UnusedDeclaration"})
public abstract class GitSCMBackwardCompatibility extends SCM implements Serializable {
    // old fields are left so that old config data can be read in, but
    // they are deprecated. transient so that they won't show up in XML
    // when writing back
    @Deprecated
    transient String source;
    @Deprecated
    transient String branch;

    /**
     * @deprecated
     *      Replaced by {@link GitSCM#buildChooser} instead.
     */
    @Deprecated
    transient String choosingStrategy;

    /**
     * @deprecated
     *      Moved to {@link RelativePath}
     */
    @Deprecated
    private transient String relativeTargetDir;
    /**
     * @deprecated
     *      Moved to {@link PathRestriction}.
     */
    @Deprecated
    private transient String includedRegions;
    /**
     * @deprecated
     *      Moved to {@link PathRestriction}.
     */
    @Deprecated
    private transient String excludedRegions;
    /**
     * @deprecated
     *      Moved to {@link UserExclusion}.
     */
    @Deprecated
    private transient String excludedUsers;

    /**
     * @deprecated
     *      Moved to {@link PerBuildTag}
     */
    @Deprecated
    private transient Boolean skipTag;


    /**
     * @deprecated
     *      Moved to {@link SubmoduleOption}
     */
    @Deprecated
    private transient boolean disableSubmodules;

    /**
     * @deprecated
     *      Moved to {@link SubmoduleOption}
     */
    @Deprecated
    private transient boolean recursiveSubmodules;

    /**
     * @deprecated
     *      Moved to {@link SubmoduleOption}
     */
    @Deprecated
    private transient boolean trackingSubmodules;

    /**
     * @deprecated
     *      Moved to {@link UserIdentity}
     */
    @Deprecated
    private transient String gitConfigName;

    /**
     * @deprecated
     *      Moved to {@link UserIdentity}
     */
    @Deprecated
    private transient String gitConfigEmail;

    /**
     * @deprecated
     *      Moved to {@link PruneStaleBranch}
     */
    @Deprecated
    private transient boolean pruneBranches;

    /**
     * @deprecated
     *      Moved to {@link PreBuildMerge}
     */
    @Deprecated
    private transient UserMergeOptions userMergeOptions;

    /**
     * @deprecated
     *      Moved to {@link PreBuildMerge}. This predates {@link UserMergeOptions}
     */
    @Deprecated
    private transient PreBuildMergeOptions mergeOptions;

    /**
     * @deprecated
     *      Moved to {@link CleanCheckout}
     */
    @Deprecated
    private transient boolean clean;

    /**
     * @deprecated
     *      Moved to {@link WipeWorkspace}
     */
    @Deprecated
    private transient boolean wipeOutWorkspace;

    /**
     * @deprecated
     *      Moved to {@link CloneOption}
     */
    @Deprecated
    private transient boolean useShallowClone;

    /**
     * @deprecated
     *      Moved to {@link CloneOption}
     */
    @Deprecated
    private transient String reference;

    /**
     * @deprecated
     *      Moved to {@link hudson.plugins.git.extensions.impl.DisableRemotePoll}
     */
    @Deprecated
    private transient boolean remotePoll;

    /**
     * @deprecated
     *      Moved to {@link AuthorInChangelog}
     */
    @Deprecated
    private transient boolean authorOrCommitter;

    /**
     * @deprecated
     *      Moved to {@link IgnoreNotifyCommit}
     */
    @Deprecated
    private transient boolean ignoreNotifyCommit;

    /**
     * @deprecated
     *      Moved to {@link ScmName}
     */
    @Deprecated
    private transient String scmName;

    /**
     * @deprecated
     *      Moved to {@link LocalBranch}
     */
    @Deprecated
    private transient String localBranch;

    /**
     * @deprecated
     *      Moved to {@link BuildChooserSetting}
     */
    @Deprecated
    private transient BuildChooser buildChooser;


    @Whitelisted
    abstract DescribableList<GitSCMExtension, GitSCMExtensionDescriptor> getExtensions();

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    void readBackExtensionsFromLegacy() {
        try {
            if (excludedUsers != null && !excludedUsers.isBlank()) {
                addIfMissing(new UserExclusion(excludedUsers));
                excludedUsers = null;
            }
            if ((excludedRegions != null && !excludedRegions.isBlank()) || (includedRegions != null && !includedRegions.isBlank())) {
                addIfMissing(new PathRestriction(includedRegions, excludedRegions));
                excludedRegions = includedRegions = null;
            }
            if (relativeTargetDir != null && !relativeTargetDir.isBlank()) {
                addIfMissing(new RelativeTargetDirectory(relativeTargetDir));
                relativeTargetDir = null;
            }
            if (skipTag!=null && !skipTag) {
                addIfMissing(new PerBuildTag());
                skipTag = null;
            }
            if (disableSubmodules || recursiveSubmodules || trackingSubmodules) {
                addIfMissing(new SubmoduleOption(disableSubmodules, recursiveSubmodules, trackingSubmodules, null, null, false));
            }
            if ((gitConfigName != null && !gitConfigName.isBlank()) || (gitConfigEmail != null && !gitConfigEmail.isBlank())) {
                addIfMissing(new UserIdentity(gitConfigName,gitConfigEmail));
                gitConfigName = gitConfigEmail = null;
            }
            if (pruneBranches) {
                addIfMissing(new PruneStaleBranch());
            }
            if (mergeOptions != null && mergeOptions.doMerge()) {
                // update from version 1
                getExtensions().replace(new PreBuildMerge(new UserMergeOptions(mergeOptions)));
                mergeOptions = null;
            }
            if (userMergeOptions!=null) {
                addIfMissing(new PreBuildMerge(userMergeOptions));
                userMergeOptions = null;
            }
            if (clean) {
                addIfMissing(new CleanCheckout());
            }
            if (wipeOutWorkspace) {
                addIfMissing(new WipeWorkspace());
            }
            if (authorOrCommitter) {
                addIfMissing(new AuthorInChangelog());
            }
            if (ignoreNotifyCommit) {
                addIfMissing(new IgnoreNotifyCommit());
            }
            if (scmName != null && !scmName.isBlank()) {
                addIfMissing(new ScmName(scmName));
            }
            if (localBranch!=null) {
                addIfMissing(new LocalBranch(localBranch));
            }
            if (buildChooser!=null && buildChooser.getClass()!=DefaultBuildChooser.class) {
                addIfMissing(new BuildChooserSetting(buildChooser));
            }
            if ((reference != null && !reference.isBlank()) || useShallowClone) {
                addIfMissing(new CloneOption(useShallowClone, reference,null));
            }
        } catch (IOException e) {
            throw new AssertionError(e); // since our extensions don't have any real Saveable
        }

    }

    private void addIfMissing(GitSCMExtension ext) throws IOException {
        if (getExtensions().get(ext.getClass())==null)
            getExtensions().add(ext);
    }

    @Deprecated
    public String getIncludedRegions() {
        PathRestriction pr = getExtensions().get(PathRestriction.class);
        return pr!=null ? pr.getIncludedRegions() : null;
    }

    @Deprecated
    public String getExcludedRegions() {
        PathRestriction pr = getExtensions().get(PathRestriction.class);
        return pr!=null ? pr.getExcludedRegions() : null;
    }

    @Deprecated
    @SuppressFBWarnings(value="PZLA_PREFER_ZERO_LENGTH_ARRAYS", justification="Not willing to change behavior of deprecated methods")
    public String[] getExcludedRegionsNormalized() {
        PathRestriction pr = getExtensions().get(PathRestriction.class);
        return pr!=null ? pr.getExcludedRegionsNormalized() : null;
    }

    @Deprecated
    @SuppressFBWarnings(value="PZLA_PREFER_ZERO_LENGTH_ARRAYS", justification="Not willing to change behavior of deprecated methods")
    public String[] getIncludedRegionsNormalized() {
        PathRestriction pr = getExtensions().get(PathRestriction.class);
        return pr!=null ? pr.getIncludedRegionsNormalized() : null;
    }


    @Deprecated
    public String getRelativeTargetDir() {
        RelativeTargetDirectory rt = getExtensions().get(RelativeTargetDirectory.class);
        return rt!=null ? rt.getRelativeTargetDir() : null;
    }


    @Deprecated
    public String getExcludedUsers() {
        UserExclusion ue = getExtensions().get(UserExclusion.class);
        return ue!=null ? ue.getExcludedUsers() : null;
    }

    @Deprecated
    public Set<String> getExcludedUsersNormalized() {
        UserExclusion ue = getExtensions().get(UserExclusion.class);
        return ue!=null ? ue.getExcludedUsersNormalized() : null;
    }

    @Deprecated
    public boolean getSkipTag() {
        return getExtensions().get(PerBuildTag.class)!=null;
    }

    @Deprecated
    public boolean getDisableSubmodules() {
        SubmoduleOption sm = getExtensions().get(SubmoduleOption.class);
        return sm != null && sm.isDisableSubmodules();
    }

    @Deprecated
    public boolean getRecursiveSubmodules() {
        SubmoduleOption sm = getExtensions().get(SubmoduleOption.class);
        return sm != null && sm.isRecursiveSubmodules();
    }

    @Deprecated
    public boolean getTrackingSubmodules() {
        SubmoduleOption sm = getExtensions().get(SubmoduleOption.class);
        return sm != null && sm.isTrackingSubmodules();
    }

    @Deprecated
    public String getGitConfigName() {
        UserIdentity ui = getExtensions().get(UserIdentity.class);
        return ui!=null ? ui.getName() : null;
    }

    @Deprecated
    public String getGitConfigEmail() {
        UserIdentity ui = getExtensions().get(UserIdentity.class);
        return ui!=null ? ui.getEmail() : null;
    }

    @Deprecated
    public String getGitConfigNameToUse() {
        String n = getGitConfigName();
        if (n==null)    n = getDescriptor().getGlobalConfigName();
        return n;
    }

    @Deprecated
    public String getGitConfigEmailToUse() {
        String n = getGitConfigEmail();
        if (n==null)    n = getDescriptor().getGlobalConfigEmail();
        return n;
    }

    @Deprecated
    public boolean getPruneBranches() {
        return getExtensions().get(PruneStaleBranch.class)!=null;
    }

    @Deprecated
    public UserMergeOptions getUserMergeOptions() {
        PreBuildMerge m = getExtensions().get(PreBuildMerge.class);
        return m!=null ? m.getOptions() : null;
    }

    /**
     * @deprecated
     *      Moved to {@link CleanCheckout}
     * @return true if clean before checkout extension is enabled
     */
    @Deprecated
    public boolean getClean() {
        return getExtensions().get(CleanCheckout.class)!=null;
    }

    /**
     * @deprecated
     *      Moved to {@link WipeWorkspace}
     * @return true if wipe workspace extension is enabled
     */
    @Deprecated
    public boolean getWipeOutWorkspace() {
        return getExtensions().get(WipeWorkspace.class)!=null;
    }

    /**
     * @deprecated
     *      Moved to {@link CloneOption}
     * @return true if shallow clone extension is enabled and shallow clone is configured
     */
    @Deprecated
    public boolean getUseShallowClone() {
        CloneOption m = getExtensions().get(CloneOption.class);
    	return m!=null && m.isShallow();
    }

    /**
     * @deprecated
     *      Moved to {@link CloneOption}
     * @return reference repository or null if reference repository is not defined
     */
    @Deprecated
    public String getReference() {
        CloneOption m = getExtensions().get(CloneOption.class);
        return m!=null ? m.getReference() : null;
    }

    /**
     * @deprecated
     *      Moved to {@link hudson.plugins.git.extensions.impl.DisableRemotePoll}
     * @return true if remote polling is allowed
     */
    @Deprecated
    public boolean getRemotePoll() {
        return getExtensions().get(DisableRemotePoll.class)==null;
    }

    /**
     * If true, use the commit author as the changeset author, rather
     * than the committer.
     *
     * @deprecated
     *      Moved to {@link AuthorInChangelog}
     * @return true if commit author is used as the changeset author
     */
    @Deprecated
    public boolean getAuthorOrCommitter() {
        return getExtensions().get(AuthorInChangelog.class)!=null;
    }

    /**
     * @deprecated
     *      Moved to {@link IgnoreNotifyCommit}
     * @return true if commit notifications are ignored
     */
    @Deprecated
    public boolean isIgnoreNotifyCommit() {
        return getExtensions().get(IgnoreNotifyCommit.class)!=null;
    }

    /**
     * @deprecated
     *      Moved to {@link ScmName}
     * @return configured SCM name or null if none if not configured
     */
    @Deprecated
    public String getScmName() {
        ScmName sn = getExtensions().get(ScmName.class);
        return sn!=null ? sn.getName() : null;
    }

    /**
     * @deprecated
     *      Moved to {@link LocalBranch}
     * @return name of local branch used for checkout or null if LocalBranch extension is not enabled
     */
    @Deprecated
    public String getLocalBranch() {
        LocalBranch lb = getExtensions().get(LocalBranch.class);
        return lb!=null ? lb.getLocalBranch() : null;
    }


    @Serial
    private static final long serialVersionUID = 1L;
}
