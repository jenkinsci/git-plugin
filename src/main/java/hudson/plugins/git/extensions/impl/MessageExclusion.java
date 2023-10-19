package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * {@link GitSCMExtension} that ignores commits with specific messages.
 *
 * @author Kanstantsin Shautsou
 */
public class MessageExclusion extends GitSCMExtension {
    /**
     * Java Pattern for matching messages to be ignored.
     */
    private String excludedMessage;

    private transient volatile Pattern excludedPattern;

    @DataBoundConstructor
    public MessageExclusion(String excludedMessage) {
        this.excludedMessage = excludedMessage;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return true;
    }

    public String getExcludedMessage() {
        return excludedMessage;
    }

    @Override
    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "null used to indicate other extensions should decide")
    @CheckForNull
    public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws IOException, InterruptedException, GitException {
        if (excludedMessage == null || excludedMessage.trim().isEmpty()) {
            listener.getLogger().println("Excluded message pattern is empty. No commits will be excluded based on message.");
            return null; // No decision
        }

        if (excludedPattern == null) {
            try {
                excludedPattern = Pattern.compile(excludedMessage);
            } catch (PatternSyntaxException ex) {
                listener.getLogger().println("Error compiling the excluded message pattern: " + ex.getMessage());
                return null; // Pattern is invalid, so no decision
            }
        }

        String msg = commit.getComment();
        if (msg == null) {
            listener.getLogger().println("Commit " + commit.getId() + " has a null message. It won't be excluded based on message.");
            return null; // Commit message is null, so no decision
        }

        if (excludedPattern.matcher(msg).find()) {
            listener.getLogger().println("Ignored commit " + commit.getId() + ": Found excluded message: " + msg);
            return true; // Commit message contains the excluded pattern
        }

        return null; // No decision
    }

    @Extension
    // No @Symbol annotation because message exclusion is done using a trait in Pipeline
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        public FormValidation doCheckExcludedMessage(@QueryParameter String value) {
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException ex) {
                return FormValidation.error(ex.getMessage());
            }
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Polling ignores commits with certain messages";
        }
    }
}
