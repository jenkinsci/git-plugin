package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;

import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * @author Kohsuke Kawaguchi
 */
public class CloneOption extends GitSCMExtension {
    private final boolean shallow;
    private final boolean noTags;
    private final String reference;
    private final Integer timeout;
    private int depth = 1;
    private boolean honorRefspec = false;

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

    public boolean isShallow() {
        return shallow;
    }

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
     * @param honorRefspec
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
    public boolean isHonorRefspec() {
        return honorRefspec;
    }

    public String getReference() {
        return reference;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public void decorateCloneCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CloneCommand cmd) throws IOException, InterruptedException, GitException {
        if (shallow) {
            listener.getLogger().println("Using shallow clone");
            cmd.shallow();
            if (depth > 1) {
                listener.getLogger().println("shallow clone depth " + depth);
                cmd.depth(depth);
            }
        }
        if (noTags) {
            listener.getLogger().println("Avoid fetching tags");
            cmd.tags(false);
        }
        if (honorRefspec) {
            listener.getLogger().println("Honoring refspec on initial clone");
            // Read refspec configuration from the first configured repository.
            // Same technique is used in GitSCM.
            // Assumes the passed in scm represents a single repository, or if
            // multiple repositories are in use, the first repository in the
            // configuration is treated as authoritative.
            // Git plugin does not support multiple independent repositories
            // in a single job definition.
            RemoteConfig rc = scm.getRepositories().get(0);
            List<RefSpec> refspecs = rc.getFetchRefSpecs();
            cmd.refspecs(refspecs);
        }
        cmd.timeout(timeout);
        cmd.reference(build.getEnvironment(listener).expand(reference));
    }

    @Override
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.shallow(shallow);
        if (shallow && depth > 1) {
	    cmd.depth(depth);
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
    public GitClientType getRequiredClient() {
        return GitClientType.GITCLI;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Advanced clone behaviours";
        }
    }

}
