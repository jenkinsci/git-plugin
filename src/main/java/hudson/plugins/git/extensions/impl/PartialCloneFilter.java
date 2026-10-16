package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;

public class PartialCloneFilter extends GitSCMExtension {

    private final String filterSpec;

    @DataBoundConstructor
    public PartialCloneFilter(String filterSpec) {
        this.filterSpec = filterSpec;
    }

    @Whitelisted
    public String getFilterSpec() {
        return filterSpec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateCloneCommand(
            GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CloneCommand cmd)
            throws IOException, InterruptedException, GitException {

        cmd.filter(filterSpec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateFetchCommand(
            GitSCM scm, @CheckForNull Run<?, ?> run, GitClient git, TaskListener listener, FetchCommand cmd)
            throws IOException, InterruptedException, GitException {

        cmd.filter(filterSpec);
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

        PartialCloneFilter that = (PartialCloneFilter) o;

        return Objects.equals(filterSpec, that.filterSpec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(filterSpec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "PartialCloneFilter{" + "filterSpec=" + filterSpec + '}';
    }

    @Extension
    @Symbol("partialCloneFilter")
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Partial clone";
        }
    }
}
