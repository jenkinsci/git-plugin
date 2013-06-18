package hudson.plugins.git;

import hudson.RelativePath;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.plugins.git.extensions.impl.PathRestriction;
import hudson.plugins.git.extensions.impl.PerBuildTag;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import hudson.plugins.git.extensions.impl.UserExclusion;
import hudson.plugins.git.extensions.impl.UserIdentity;
import hudson.plugins.git.extensions.impl.WipeWorkspace;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.scm.SCM;
import hudson.util.DescribableList;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

/**
 * This is a portion of {@link GitSCM} for the stuff that's used to be in {@link GitSCM}
 * that are deprecated. Moving deprecated stuff from {@link GitSCM} to here allows us
 * to keep {@link GitSCM} cleaner.
 *
 * @author Kohsuke Kawaguchi
 */
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

    abstract DescribableList<GitSCMExtension, GitSCMExtensionDescriptor> getExtensions();

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    void readBackExtensionsFromLegacy() {
        try {
            if (excludedUsers!=null) {
                addIfMissing(new UserExclusion(excludedUsers));
                excludedUsers = null;
            }
            if (excludedRegions!=null || includedRegions!=null) {
                addIfMissing(new PathRestriction(includedRegions, excludedRegions));
                excludedRegions = includedRegions = null;
            }
            if (relativeTargetDir!=null) {
                addIfMissing(new RelativeTargetDirectory(relativeTargetDir));
                relativeTargetDir = null;
            }
            if (skipTag!=null && skipTag) {
                addIfMissing(new PerBuildTag());
                skipTag = null;
            }
            if (disableSubmodules || recursiveSubmodules) {
                addIfMissing(new SubmoduleOption(disableSubmodules, recursiveSubmodules));
            }
            if (gitConfigName!=null || gitConfigEmail!=null) {
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
        return getExtensions().contains(PerBuildTag.class);
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
        return getExtensions().contains(PruneStaleBranch.class);
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
        return getExtensions().contains(CleanCheckout.class);
    }

    /**
     * @deprecated
     *      Moved to {@link WipeWorkspace}
     */
    public boolean getWipeOutWorkspace() {
        return getExtensions().contains(WipeWorkspace.class);
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


    private static final long serialVersionUID = 1L;
}
