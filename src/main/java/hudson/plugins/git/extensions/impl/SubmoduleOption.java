package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.GitException;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
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
     */
    private boolean disableSubmodules;
    private boolean recursiveSubmodules;

    @DataBoundConstructor
    public SubmoduleOption(boolean disableSubmodules, boolean recursiveSubmodules) {
        this.disableSubmodules = disableSubmodules;
        this.recursiveSubmodules = recursiveSubmodules;
    }

    public boolean isDisableSubmodules() {
        return disableSubmodules;
    }

    public boolean isRecursiveSubmodules() {
        return recursiveSubmodules;
    }

    @Override
    public void onClean(GitClient git) throws IOException, InterruptedException, GitException {
        if (!disableSubmodules && git.hasGitModules()) {
            git.submoduleClean(recursiveSubmodules);
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
