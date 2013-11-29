package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static hudson.Util.*;

/**
 * {@link GitSCMExtension} that ignores commits that are made by specific users.
 *
 * @author Kohsuke Kawaguchi
 */
public class UserExclusion extends GitSCMExtension {
    /**
     * Whitespace separated list of the user IDs to be ignored.
     */
    private String excludedUsers;

    @DataBoundConstructor
    public UserExclusion(String excludedUsers) {
        this.excludedUsers = excludedUsers;
    }

    @Override
    public boolean requiresWorkspaceForPolling () {
    	return true;
    }

    public String getExcludedUsers() {
        return excludedUsers;
    }

    public Set<String> getExcludedUsersNormalized() {
        String s = fixEmptyAndTrim(excludedUsers);
        if (s == null) {
            return Collections.emptySet();
        }

        Set<String> users = new HashSet<String>();
        for (String user : s.split("[\\r\\n]+")) {
            users.add(user.trim());
        }
        return users;
    }

    @Override
    public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) {
        String author = commit.getAuthorName();
        if (getExcludedUsersNormalized().contains(author)) {
            // If the author is an excluded user, don't count this entry as a change
            listener.getLogger().println("Ignored commit " + commit.getCommitId() + ": Found excluded author: " + author);
            return true;
        }

        return null;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Polling ignores commits from certain users";
        }
    }
}
