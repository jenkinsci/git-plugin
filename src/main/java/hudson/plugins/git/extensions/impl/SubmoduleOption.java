package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Messages;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;
import java.io.IOException;
import java.util.Objects;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.SubmoduleUpdateCommand;
import org.jenkinsci.plugins.gitclient.UnsupportedCommand;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import edu.umd.cs.findbugs.annotations.NonNull;

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
    private boolean disableSubmodules;
    /** Use --recursive flag on submodule commands - requires git>=1.6.5 */
    private boolean recursiveSubmodules;
    /** Use --remote flag on submodule update command - requires git>=1.8.2 */
    private boolean trackingSubmodules;
    /** Use --reference flag on submodule update command - requires git>=1.6.4 */
    private String reference;
    private boolean parentCredentials;
    private Integer timeout;
    /** Use --depth flag on submodule update command - requires git>=1.8.4 */
    private boolean shallow;
    private Integer depth;
    private Integer threads;

    @DataBoundConstructor
    public SubmoduleOption() {
        this(false, false, false, null, null, false);
    }

    @Whitelisted
    public SubmoduleOption(boolean disableSubmodules, boolean recursiveSubmodules, boolean trackingSubmodules, String reference, Integer timeout, boolean parentCredentials) {
        this.disableSubmodules = disableSubmodules;
        this.recursiveSubmodules = recursiveSubmodules;
        this.trackingSubmodules = trackingSubmodules;
        this.parentCredentials = parentCredentials;
        this.reference = reference;
        this.timeout = timeout;
    }

    @Whitelisted
    public boolean isDisableSubmodules() {
        return disableSubmodules;
    }

    @DataBoundSetter
    public void setDisableSubmodules(boolean value) {
        disableSubmodules = value;
    }

    @Whitelisted
    public boolean isRecursiveSubmodules() {
        return recursiveSubmodules;
    }

    @DataBoundSetter
    public void setRecursiveSubmodules(boolean value) {
        recursiveSubmodules = value;
    }

    @Whitelisted
    public boolean isTrackingSubmodules() {
        return trackingSubmodules;
    }

    @DataBoundSetter
    public void setTrackingSubmodules(boolean value) {
        trackingSubmodules = value;
    }

    @Whitelisted
    public boolean isParentCredentials() {
        return parentCredentials;
    }

    @DataBoundSetter
    public void setParentCredentials(boolean value) {
        parentCredentials = value;
    }

    @Whitelisted
    public String getReference() {
        return reference;
    }

    @DataBoundSetter
    public void setReference(String value) {
        reference = value;
    }

    @Whitelisted
    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(Integer value) {
        timeout = value;
    }

    @DataBoundSetter
    public void setShallow(boolean shallow) {
        this.shallow = shallow;
    }

    @Whitelisted
    public boolean getShallow() {
        return shallow;
    }

    @DataBoundSetter
    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    @Whitelisted
    public Integer getDepth() {
        return depth;
    }

    @Whitelisted
    public Integer getThreads() {
        return threads;
    }

    @DataBoundSetter
    public void setThreads(Integer threads) {
        this.threads = threads;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClean(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
        if (!disableSubmodules && git.hasGitModules()) {
            git.submoduleClean(recursiveSubmodules);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCheckoutCompleted(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        BuildData revToBuild = scm.getBuildData(build);

        try {
            if (!disableSubmodules && git.hasGitModules() && revToBuild != null && revToBuild.lastBuild != null) {
                // This ensures we don't miss changes to submodule paths and allows
                // seamless use of bare and non-bare superproject repositories.
                git.setupSubmoduleUrls(revToBuild.lastBuild.getRevision(), listener);
                SubmoduleUpdateCommand cmd = git.submoduleUpdate()
                        .recursive(recursiveSubmodules)
                        .remoteTracking(trackingSubmodules)
                        .parentCredentials(parentCredentials)
                        .ref(build.getEnvironment(listener).expand(reference))
                        .timeout(timeout)
                        .shallow(shallow);
                if (shallow) {
                    int usedDepth = depth == null || depth < 1 ? 1 : depth;
                    listener.getLogger().println("Using shallow submodule update with depth " + usedDepth);
                    cmd.depth(usedDepth);
                }
                int usedThreads = threads == null || threads < 1 ? 1 : threads;
                cmd.threads(usedThreads);
                cmd.execute();
            }
        } catch (GitException e) {
            // Re-throw as an IOException in order to allow generic retry
            // logic to kick in properly.
            throw new IOException("Could not perform submodule update", e);
        }
    }

    @Override
    public void determineSupportForJGit(GitSCM scm, @NonNull UnsupportedCommand cmd) {
        /* Prevent JGit with ANY use of SubmoduleOption by always setting a value
         * for threads.  See JENKINS-64382.
         */
        if (threads == null) {
            cmd.threads(1);
        } else {
            cmd.threads(threads);
        }
        cmd.depth(depth);
        cmd.shallow(shallow);
        cmd.timeout(timeout);
        cmd.ref(reference);
        cmd.parentCredentials(parentCredentials);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SubmoduleOption that = (SubmoduleOption) o;

        return disableSubmodules == that.disableSubmodules
                && recursiveSubmodules == that.recursiveSubmodules
                && trackingSubmodules == that.trackingSubmodules
                && parentCredentials == that.parentCredentials
                && Objects.equals(reference, that.reference)
                && Objects.equals(timeout, that.timeout)
                && shallow == that.shallow
                && Objects.equals(depth, that.depth)
                && Objects.equals(threads, that.threads);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(disableSubmodules, recursiveSubmodules, trackingSubmodules, parentCredentials, reference, timeout, shallow, depth, threads);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SubmoduleOption{" +
                "disableSubmodules=" + disableSubmodules +
                ", recursiveSubmodules=" + recursiveSubmodules +
                ", trackingSubmodules=" + trackingSubmodules +
                ", reference='" + reference + '\'' +
                ", parentCredentials=" + parentCredentials +
                ", timeout=" + timeout +
                ", shallow=" + shallow +
                ", depth=" + depth +
                ", threads=" + threads +
                '}';
    }

    @Extension
    @Symbol("submodule")
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.advanced_sub_modules_behaviours();
        }
    }
}
