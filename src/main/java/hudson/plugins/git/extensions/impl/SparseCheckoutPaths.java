package hudson.plugins.git.extensions.impl;

import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SparseCheckoutPaths extends GitSCMExtension {
    private List<SparseCheckoutPath> sparseCheckoutPaths = Collections.emptyList();

    @DataBoundConstructor
    public SparseCheckoutPaths(List<SparseCheckoutPath> sparseCheckoutPaths) {
        this.sparseCheckoutPaths = sparseCheckoutPaths == null ? Collections.<SparseCheckoutPath>emptyList() : sparseCheckoutPaths;
    }

    public List<SparseCheckoutPath> getSparseCheckoutPaths() {
        return sparseCheckoutPaths;
    }

    @Override
    public void decorateCloneCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CloneCommand cmd) throws IOException, InterruptedException, GitException {
        if (! sparseCheckoutPaths.isEmpty()) {
            listener.getLogger().println("Using no checkout clone with sparse checkout.");
            cmd.noCheckout();
        }
    }

    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        cmd.sparseCheckoutPaths(Lists.transform(sparseCheckoutPaths, SparseCheckoutPath.SPARSE_CHECKOUT_PATH_TO_PATH));
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Sparse Checkout paths";
        }
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
        
        SparseCheckoutPaths that = (SparseCheckoutPaths) o;
        return Objects.equals(getSparseCheckoutPaths(), that.getSparseCheckoutPaths());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getSparseCheckoutPaths());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SparseCheckoutPaths{" +
                "sparseCheckoutPaths=" + sparseCheckoutPaths +
                '}';
    }
}
