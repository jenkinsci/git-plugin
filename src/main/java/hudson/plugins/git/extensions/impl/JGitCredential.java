package hudson.plugins.git.extensions.impl;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUser;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitAPIImpl;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

/**
 * Controls the credentials that JGit uses.
 *
 * @author Kohsuke Kawaguchi
 */
public class JGitCredential extends GitSCMExtension {
    /**
     * The id of the credentials to use.
     */
    private final String credentialsId;

    @DataBoundConstructor
    public JGitCredential(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public SSHUser getCredentials() {
        // TODO: once we define a credential type for HTTP BASIC auth, look there in addition to SSHUser
        List<SSHUser> all = CredentialsProvider.lookupCredentials(SSHUser.class, Jenkins.getInstance());
        for (SSHUser u: all) {
            if (u.getId().equals(credentialsId)) {
                return u;
            }
        }

        // looks like the credentials is gone. pick something in the hope that it'll match
        // in small instances, this tends to work well, and even if it doesn't, it's no worse
        // than returning null.
        if (!all.isEmpty())     return all.get(0);
        return null;
    }

    @Override
    public GitClient decorate(GitSCM scm, final GitClient git) throws IOException, InterruptedException, GitException {
        final SSHUser cred = getCredentials();

        // 'git' might be a proxy to the remote object so we need a closure that runs locally to 'git' to de-reference
        // 'git' to JGit.
        git.withRepository(new RepositoryCallback<Void>() {
            public Void invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
                if (git instanceof JGitAPIImpl) {
                    JGitAPIImpl jgit = (JGitAPIImpl) git;
                    jgit.setCredentials(cred);
                }
                return null;
            }
        });

        return git;
    }

    @Override
    public GitClientType getRequiredClient() {
        return GitClientType.JGIT;
    }



    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Credential for authentication (JGit only/experimental)";
        }

        public SSHUserListBoxModel doFillCredentialsIdItems(@AncestorInPath AbstractProject context) {
            return new SSHUserListBoxModel().addCollection(
                    CredentialsProvider.lookupCredentials(SSHUser.class, context));
        }
    }
}
