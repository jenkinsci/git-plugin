package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleCombinator;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.SubmoduleUpdateCommand;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Further tweak the behaviour of git-submodule.
 *
 * <p>
 * Historically, the submodule support was on by default,
 * and given the clear marker file in the source tree, I think
 * keeping this default behaviour is sensible.
 *
 * So when we split out {@link GitSCMExtension}s, we decided
 * to keep the git-submodule handling enabled by default,
 * and this extension controls the recursiveness and the option
 * to switch it off.
 *
 * @author Yury V. Zaytsev
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class SubmoduleOption extends GitSCMExtension {
    /**
     * Use --recursive flag on submodule commands - requires git>=1.6.5
     * Use --remote flag on submodule update command - requires git>=1.8.2
     */
    private boolean disableSubmodules;
    private boolean recursiveSubmodules;
    private boolean trackingSubmodules;
    private List<SubmoduleBranch> submoduleBranches = Collections.emptyList();

    @Deprecated
    public SubmoduleOption(boolean disableSubmodules,
                           boolean recursiveSubmodules,
                           boolean trackingSubmodules) {
        this(disableSubmodules, recursiveSubmodules, trackingSubmodules, null);
    }

    @DataBoundConstructor
    public SubmoduleOption(boolean disableSubmodules,
                           boolean recursiveSubmodules,
                           boolean trackingSubmodules,
                           List<SubmoduleBranch> submoduleBranches) {
        this.disableSubmodules = disableSubmodules;
        this.recursiveSubmodules = recursiveSubmodules;
        this.trackingSubmodules = trackingSubmodules;
        this.submoduleBranches = submoduleBranches == null ? Collections.<SubmoduleBranch>emptyList() : submoduleBranches;
    }

    public boolean isDisableSubmodules() {
        return disableSubmodules;
    }

    public boolean isRecursiveSubmodules() {
        return recursiveSubmodules;
    }

    public boolean isTrackingSubmodules() {
        return trackingSubmodules;
    }

    public List<SubmoduleBranch> getSubmoduleBranches() {
        return submoduleBranches;
    }

    @Override
    public void onClean(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
        if (!disableSubmodules && git.hasGitModules()) {
            git.submoduleClean(recursiveSubmodules);
        }
    }

    @Override
    public void onCheckoutCompleted(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        BuildData revToBuild = scm.getBuildData(build);

        if (!disableSubmodules && git.hasGitModules()) {
            // This ensures we don't miss changes to submodule paths and allows
            // seamless use of bare and non-bare superproject repositories.
            git.setupSubmoduleUrls(revToBuild.lastBuild.getRevision(), listener);

            SubmoduleUpdateCommand upd = git.submoduleUpdate().recursive(recursiveSubmodules).remoteTracking(trackingSubmodules);
            if (submoduleBranches != null) {
                EnvVars env = build.getEnvironment();
                for (SubmoduleBranch sb : submoduleBranches) {
                    String expandedBranchValue = env.expand(sb.getBranch());

                    //
                    //  If nothing gets replaced, we are probably in the initial checkout of the
                    //  repo before node-based configuration matrix selection occurs.  If our,
                    //  branch uses variables based on node-based configuration, then it won't be
                    //  available in the initial checkout.  So, don't do the submodule branch
                    //  switch if we can't substitute it correctly.
                    //
                    if (!sb.getBranch().contains("$") || !expandedBranchValue.equals(sb.getBranch())) {
                        listener.getLogger().println("Updating submodule '" + sb.getSubmodule() + "' to '" + expandedBranchValue + "'");
                        upd = upd.useBranch(sb.getSubmodule(), expandedBranchValue);
                    }
                    else {
                        listener.getLogger().println("Couldn't substitute '" + sb.getBranch() + "' for submodule '" + sb.getSubmodule() + "'");
                    }
                }
            }
            upd.execute();
        }

        if (scm.isDoGenerateSubmoduleConfigurations()) {
            /*
                Kohsuke Note:

                I could be wrong, but this feels like a totally wrong place to do this.
                AFAICT, SubmoduleCombinator runs a lot of git-checkout and git-commit to
                create new commits and branches. At the end of this, the working tree is
                significantly altered, and HEAD no longer points to 'revToBuild'.

                Custom BuildChooser is probably the right place to do this kind of stuff,
                or maybe we can add a separate callback for GitSCMExtension.
             */
            SubmoduleCombinator combinator = new SubmoduleCombinator(git, listener, scm.getSubmoduleCfg());
            combinator.createSubmoduleCombinations();
        }
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Advanced sub-modules behaviours";
        }
    }
}
