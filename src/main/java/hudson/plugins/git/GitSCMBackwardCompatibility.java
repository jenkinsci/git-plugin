package hudson.plugins.git;

import hudson.RelativePath;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.extensions.impl.*;
import hudson.plugins.git.extensions.impl.DisableRemotePoll;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.scm.SCM;
import hudson.util.DescribableList;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.isNotBlank;

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
    transient String choosingStrategy;

    /**
     * @deprecated
     *      Moved to {@link RelativePath}
     */
    private transient String relativeTargetDir;
    /**
     * @deprecated
     *      Moved to {@link PathRestriction}.
     */
    private transient String includedRegions;
    /**
     * @deprecated
     *      Moved to {@link PathRestriction}.
     */
    private transient String excludedRegions;
    /**
     * @deprecated
     *      Moved to {@link UserExclusion}.
     */
    private transient String excludedUsers;

    /**
     * @deprecated
     *      Moved to {@link PerBuildTag}
     */
    private transient Boolean skipTag;


    /**
     * @deprecated
     *      Moved to {@link SubmoduleOption}
     */
    private transient boolean disableSubmodules;

    /**
     * @deprecated
     *      Moved to {@link SubmoduleOption}
     */
    private transient boolean recursiveSubmodules;

    /**
     * @deprecated
     *      Moved to {@link SubmoduleOption}
     */
    private transient boolean trackingSubmodules;

    /**
     * @deprecated
     *      Moved to {@link UserIdentity}
     */
    private transient String gitConfigName;

    /**
     * @deprecated
     *      Moved to {@link UserIdentity}
     */
    private transient String gitConfigEmail;

    /**
     * @deprecated
     *      Moved to {@link PruneStaleBranch}
     */
    private transient boolean pruneBranches;

    /**
     * @deprecated
     *      Moved to {@link PreBuildMerge}
     */
    private transient UserMergeOptions userMergeOptions;

    /**
     * @deprecated
     *      Moved to {@link PreBuildMerge}. This predates {@link UserMergeOptions}
     */
    private transient PreBuildMergeOptions mergeOptions;

    /**
     * @deprecated
     *      Moved to {@link CleanCheckout}
     */
    private transient boolean clean;

    /**
     * @deprecated
     *      Moved to {@link WipeWorkspace}
     */
    private transient boolean wipeOutWorkspace;

    /**
     * @deprecated
     *      Moved to {@link CloneOption}
     */
    private transient boolean useShallowClone;

    /**
     * @deprecated
     *      Moved to {@link CloneOption}
     */
    private transient String reference;

    /**
     * @deprecated
     *      Moved to {@link hudson.plugins.git.extensions.impl.DisableRemotePoll}
     */
    private transient boolean remotePoll;

    /**
     * @deprecated
     *      Moved to {@link AuthorInChangelog}
     */
    private transient boolean authorOrCommitter;

    /**
     * @deprecated
     *      Moved to {@link IgnoreNotifyCommit}
     */
    private transient boolean ignoreNotifyCommit;

    /**
     * @deprecated
     *      Moved to {@link ScmName}
     */
    private transient String scmName;

    /**
     * @deprecated
     *      Moved to {@link LocalBranch}
     */
    private transient String localBranch;

    /**
     * @deprecated
     *      Moved to {@link BuildChooserSetting}
     */
    private transient BuildChooser buildChooser;


    abstract DescribableList<GitSCMExtension, GitSCMExtensionDescriptor> getExtensions();

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    void readBackExtensionsFromLegacy() {
        try {
            if (isNotBlank(excludedUsers)) {
                addIfMissing(new UserExclusion(excludedUsers));
                excludedUsers = null;
            }
            if (isNotBlank(excludedRegions) || isNotBlank(includedRegions)) {
                addIfMissing(new PathRestriction(includedRegions, excludedRegions));
                excludedRegions = includedRegions = null;
            }
            if (isNotBlank(relativeTargetDir)) {
                addIfMissing(new RelativeTargetDirectory(relativeTargetDir));
                relativeTargetDir = null;
            }
            if (skipTag!=null && !skipTag) {
                addIfMissing(new PerBuildTag());
                skipTag = null;
            }
            if (disableSubmodules || recursiveSubmodules || trackingSubmodules) {
                addIfMissing(new SubmoduleOption(disableSubmodules, recursiveSubmodules, trackingSubmodules));
            }
            if (isNotBlank(gitConfigName) || isNotBlank(gitConfigEmail)) {
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
            if (isNotBlank(scmName)) {
                addIfMissing(new ScmName(scmName));
            }
            if (localBranch!=null) {
                addIfMissing(new LocalBranch(localBranch));
            }
            if (buildChooser!=null && buildChooser.getClass()!=DefaultBuildChooser.class) {
                addIfMissing(new BuildChooserSetting(buildChooser));
            }
            if (isNotBlank(reference) || useShallowClone) {
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
    public String[] getExcludedRegionsNormalized() {
        PathRestriction pr = getExtensions().get(PathRestriction.class);
        return pr!=null ? pr.getExcludedRegionsNormalized() : null;
    }

    @Deprecated
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
     */
    public boolean getClean() {
        return getExtensions().get(CleanCheckout.class)!=null;
    }

    /**
     * @deprecated
     *      Moved to {@link WipeWorkspace}
     */
    public boolean getWipeOutWorkspace() {
        return getExtensions().get(WipeWorkspace.class)!=null;
    }

    /**
     * @deprecated
     *      Moved to {@link CloneOption}
     */
    public boolean getUseShallowClone() {
        CloneOption m = getExtensions().get(CloneOption.class);
    	return m!=null && m.isShallow();
    }

    /**
     * @deprecated
     *      Moved to {@link CloneOption}
     */
    public String getReference() {
        CloneOption m = getExtensions().get(CloneOption.class);
        return m!=null ? m.getReference() : null;
    }

    /**
     * @deprecated
     *      Moved to {@link hudson.plugins.git.extensions.impl.DisableRemotePoll}
     */
    public boolean getRemotePoll() {
        return getExtensions().get(DisableRemotePoll.class)==null;
    }

    /**
     * If true, use the commit author as the changeset author, rather
     * than the committer.
     *
     * @deprecated
     *      Moved to {@link AuthorInChangelog}
     */
    public boolean getAuthorOrCommitter() {
        return getExtensions().get(AuthorInChangelog.class)!=null;
    }

    /**
     * @deprecated
     *      Moved to {@link IgnoreNotifyCommit}
     */
    public boolean isIgnoreNotifyCommit() {
        return getExtensions().get(IgnoreNotifyCommit.class)!=null;
    }

    /**
     * @deprecated
     *      Moved to {@link ScmName}
     */
    public String getScmName() {
        ScmName sn = getExtensions().get(ScmName.class);
        return sn!=null ? sn.getName() : null;
    }

    /**
     * @deprecated
     *      Moved to {@link LocalBranch}
     */
    public String getLocalBranch() {
        LocalBranch lb = getExtensions().get(LocalBranch.class);
        return lb!=null ? lb.getLocalBranch() : null;
    }


    private static final long serialVersionUID = 1L;
}
