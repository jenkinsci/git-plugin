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

/**
 * @author Kohsuke Kawaguchi
 */
public class CloneOption extends GitSCMExtension {
    private final boolean shallow;
    private final boolean noTags;
    private final String reference;
    private final Integer timeout;
    private int depth = 1;

    public CloneOption(boolean shallow, String reference, Integer timeout) {
        this(shallow, false, reference, timeout);
    }

    @DataBoundConstructor
    public CloneOption(boolean shallow, boolean noTags, String reference, Integer timeout) {
        this.shallow = shallow;
        this.noTags = noTags;
        this.reference = reference;
        this.timeout = timeout;
    }

    public boolean isShallow() {
        return shallow;
    }

    public boolean isNoTags() {
        return noTags;
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
