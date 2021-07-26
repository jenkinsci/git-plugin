package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.GitUtils;
import hudson.slaves.NodeProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.UnsupportedCommand;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * @author Kohsuke Kawaguchi
 */
public class CloneOption extends GitSCMExtension {
    private final boolean shallow;
    private final boolean noTags;
    private final String reference;
    private final Integer timeout;
    private Integer depth;
    private boolean honorRefspec;

    public CloneOption(boolean shallow, String reference, Integer timeout) {
        this(shallow, false, reference, timeout);
    }

    @DataBoundConstructor
    public CloneOption(boolean shallow, boolean noTags, String reference, Integer timeout) {
        this.shallow = shallow;
        this.noTags = noTags;
        this.reference = reference;
        this.timeout = timeout;
        this.honorRefspec = false;
    }

    @Whitelisted
    public boolean isShallow() {
        return shallow;
    }

    @Whitelisted
    public boolean isNoTags() {
        return noTags;
    }

    /**
     * This setting allows the job definition to control whether the refspec
     * will be honored during the first clone or not.
     *
     * Prior to git plugin 2.5.1, JENKINS-31393 caused the user provided refspec
     * to be ignored during the initial clone. It was honored in later fetch
     * operations, but not in the first clone. That meant the initial clone had
     * to fetch all the branches and their references from the remote
     * repository, even if those branches were later ignored due to the refspec.
     *
     * The fix for JENKINS-31393 exposed JENKINS-36507 which suggests that
     * the Gerrit Plugin assumes all references are fetched, even though it only
     * passes the refspec for one branch.
     *
     * @param honorRefspec true if refspec should be honored on clone
     */
    @DataBoundSetter
    public void setHonorRefspec(boolean honorRefspec) {
        this.honorRefspec = honorRefspec;
    }

    /**
     * Returns true if the job should clone only the items which match the
     * refspec, or if all references are cloned, then the refspec should be used
     * in later operations.
     *
     * Prior to git plugin 2.5.1, JENKINS-31393 caused the user provided refspec
     * to be ignored during the initial clone. It was honored in later fetch
     * operations, but not in the first clone. That meant the initial clone had
     * to fetch all the branches and their references from the remote
     * repository, even if those branches were later ignored due to the refspec.
     *
     * The fix for JENKINS-31393 exposed JENKINS-36507 which seems to show that
     * the Gerrit Plugin assumes all references are fetched, even though it only
     * passes the refspec for one branch.
     *
     * @return true if initial clone will honor the user defined refspec
     */
    @Whitelisted
    public boolean isHonorRefspec() {
        return honorRefspec;
    }

    @Whitelisted
    public String getReference() {
        return reference;
    }

    @Whitelisted
    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    @Whitelisted
    public Integer getDepth() {
        return depth;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateCloneCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CloneCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.shallow(shallow);
        if (shallow) {
            int usedDepth = 1;
            if (depth != null && depth > 0) {
                usedDepth = depth;
            }
            listener.getLogger().println("Using shallow clone with depth " + usedDepth);
            cmd.depth(usedDepth);
        }
        if (noTags) {
            listener.getLogger().println("Avoid fetching tags");
            cmd.tags(false);
        }

        Node node = GitUtils.workspaceToNode(git.getWorkTree());

        if (honorRefspec) {
            listener.getLogger().println("Honoring refspec on initial clone");
            // Read refspec configuration from the first configured repository.
            // Same technique is used in GitSCM.
            // Assumes the passed in scm represents a single repository, or if
            // multiple repositories are in use, the first repository in the
            // configuration is treated as authoritative.
            // Git plugin does not support multiple independent repositories
            // in a single job definition.
            EnvVars buildEnv = build.getEnvironment(listener);
            RemoteConfig rc = scm.getRepositories().get(0);
            cmd.refspecs(getRefSpecs(rc, buildEnv));
        }
        cmd.timeout(timeout);

        EnvVars env = build.getEnvironment(listener);
        Computer comp = node.toComputer();
        if (comp != null) {
            env.putAll(comp.getEnvironment());
        }
        for (NodeProperty nodeProperty: node.getNodeProperties()) {
            nodeProperty.buildEnvVars(env, listener);
        }
        cmd.reference(env.expand(reference));
    }

    private static String getParameterString(@CheckForNull String original, @NonNull EnvVars env) {
        return env.expand(original);
    }

    private static List<RefSpec> getRefSpecs(RemoteConfig repo, EnvVars env) {
        List<RefSpec> refSpecs = new ArrayList<>();
        for (RefSpec refSpec : repo.getFetchRefSpecs()) {
            refSpecs.add(new RefSpec(getParameterString(refSpec.toString(), env)));
        }
        return refSpecs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated // Deprecate because the super implementation is deprecated
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.shallow(shallow);
        if (shallow) {
            int usedDepth = 1;
            if (depth != null && depth > 0) {
                usedDepth = depth;
            }
            listener.getLogger().println("Using shallow fetch with depth " + usedDepth);
            cmd.depth(usedDepth);
        }
        cmd.tags(!noTags);
        /* cmd.refspecs() not required.
         * FetchCommand already requires list of refspecs through its
         * from(remote, refspecs) method, no need to adjust refspecs
         * here on initial clone
         */
        cmd.timeout(timeout);
    }

    @Override
    public void determineSupportForJGit(GitSCM scm, @NonNull UnsupportedCommand cmd) {
        cmd.timeout(timeout);
        cmd.shallow(shallow);
        cmd.depth(depth);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GitClientType getRequiredClient() {
        return GitClientType.GITCLI;
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

        CloneOption that = (CloneOption) o;

        return shallow == that.shallow
                && noTags == that.noTags
                && Objects.equals(depth, that.depth)
                && honorRefspec == that.honorRefspec
                && Objects.equals(reference, that.reference)
                && Objects.equals(timeout, that.timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(shallow, noTags, depth, honorRefspec, reference, timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "CloneOption{" +
                "shallow=" + shallow +
                ", noTags=" + noTags +
                ", reference='" + reference + '\'' +
                ", timeout=" + timeout +
                ", depth=" + depth +
                ", honorRefspec=" + honorRefspec +
                '}';
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.Advanced_clone_behaviours();
        }
    }

}
