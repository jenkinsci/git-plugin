package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleCombinator;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

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
     * Use --reference flag on submodule update command - requires git>=1.6.4
     */
    private boolean disableSubmodules;
    private boolean recursiveSubmodules;
    private boolean trackingSubmodules;
    private String reference;
    private Integer timeout;

    @DataBoundConstructor
    public SubmoduleOption(boolean disableSubmodules, boolean recursiveSubmodules, boolean trackingSubmodules, String reference, Integer timeout) {
        this.disableSubmodules = disableSubmodules;
        this.recursiveSubmodules = recursiveSubmodules;
        this.trackingSubmodules = trackingSubmodules;
        this.reference = reference;
        this.timeout = timeout;
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

    public String getReference() {
        return reference;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @Override
    public void onClean(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
        if (!disableSubmodules && git.hasGitModules()) {
            git.submoduleClean(recursiveSubmodules);
        }
    }

    @Override
    public void onCheckoutCompleted(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        BuildData revToBuild = scm.getBuildData(build);

        try {
            if (!disableSubmodules && git.hasGitModules()) {
                // This ensures we don't miss changes to submodule paths and allows
                // seamless use of bare and non-bare superproject repositories.
                git.setupSubmoduleUrls(revToBuild.lastBuild.getRevision(), listener);
                git.submoduleUpdate()
                    .recursive(recursiveSubmodules)
                    .remoteTracking(trackingSubmodules)
                    .ref(build.getEnvironment(listener).expand(reference))
                    .timeout(timeout)
                    .execute();
        } catch (GitException e) {
            // Rethrow IOException so that retry will be able to catch it
            throw new IOException("Could not perform submodule update", e);
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
