package hudson.plugins.git.extensions.impl;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.queue.Tasks;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import java.util.List;

import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * git-lfs-pull after the checkout.
 *
 * @author Matt Hauck
 */
public class GitLFSPull extends GitSCMExtension {
    private String credentialsId;

    @DataBoundConstructor
    public GitLFSPull(@CheckForNull String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateCheckoutCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, CheckoutCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger().println("Enabling Git LFS pull");
        List<RemoteConfig> repos = scm.getParamExpandedRepos(build, listener);
        // repos should never be empty, but check anyway
        if (!repos.isEmpty()) {
            // Pull LFS files from the first configured repository.
            // Same technique is used in GitSCM and CLoneOption.
            // Assumes the passed in scm represents a single repository, or if
            // multiple repositories are in use, the first repository in the
            // configuration is treated as authoritative.
            // Git plugin does not support multiple independent repositories
            // in a single job definition.
            RemoteConfig repo = repos.get(0);
            cmd.lfsRemote(repo.getName());
            if (!StringUtils.isEmpty(credentialsId)) {
                Job project = build.getParent();
                List<StandardUsernameCredentials> urlCredentials = CredentialsProvider.lookupCredentials(
                        StandardUsernameCredentials.class,
                        project,
                        project instanceof Queue.Task
                                ? ((Queue.Task) project).getDefaultAuthenticationOf()
                                : ACL.SYSTEM,
                        URIRequirementBuilder.fromUri(repo.getURIs().get(0).toString()).build()
                );
                CredentialsMatcher ucMatcher = CredentialsMatchers.withId(credentialsId);
                CredentialsMatcher idMatcher = CredentialsMatchers.allOf(ucMatcher, GitClient.CREDENTIALS_MATCHER);
                StandardUsernameCredentials credentials = CredentialsMatchers.firstOrNull(urlCredentials, idMatcher);
                cmd.lfsCredentials(credentials);
            }
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
        return o instanceof GitLFSPull;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return GitLFSPull.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "GitLFSPull{}";
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project,
                                                     @QueryParameter String url,
                                                     @QueryParameter String credentialsId) {
            return new UserRemoteConfig.DescriptorImpl().doFillCredentialsIdItems(
                    project, url, credentialsId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Git LFS pull after checkout";
        }
    }
}
