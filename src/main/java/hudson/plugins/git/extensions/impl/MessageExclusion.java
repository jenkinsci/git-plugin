package hudson.plugins.git.extensions.impl;

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
 * {@link GitSCMExtension} that ignores commits with specific messages or only includes commits with a message.
 * Note that the class should be more aptly named "MessageInclusionExclusion" but this would break backward compatibility.
 *
 * @author Kanstantsin Shautsou
 */
public class MessageExclusion extends GitSCMExtension {
	/**
	 * Java Pattern for matching messages to be ignored or needed.
	 */
	private String excludedMessage;

	private transient volatile Pattern excludedPattern;
    
    private boolean includeInsteadOfExclude = false;
    
    private boolean partialMatch = false;

	@DataBoundConstructor
	public MessageExclusion(String excludedMessage, boolean includeInsteadOfExclude, boolean partialMatch) {
        this.excludedMessage = excludedMessage;
        this.includeInsteadOfExclude = includeInsteadOfExclude;
        this.partialMatch = partialMatch;
    }

	@Override
	public boolean requiresWorkspaceForPolling() { return true; }

	public String getExcludedMessage() { return excludedMessage; }

    public boolean isIncludeInsteadOfExclude() { return includeInsteadOfExclude; }
    
    public boolean isPartialMatch() { return partialMatch; }
    
	@Override
	public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws IOException, InterruptedException, GitException {
		if (excludedPattern == null){
			excludedPattern = Pattern.compile(excludedMessage);
		}
		String msg = commit.getComment();
        
        boolean matched = isPartialMatch() ? excludedPattern.matcher(msg).find() : excludedPattern.matcher(msg).matches();

        if (!includeInsteadOfExclude && matched){
			listener.getLogger().println("Ignored commit " + commit.getId() + ": Found excluded message: " + msg);
			return true;
		} else if (includeInsteadOfExclude && !matched) {
            listener.getLogger().println("Ignored commit " + commit.getId() + ": Message pattern not found: " + msg);
            return true;
        }

		return null;
	}

	@Extension
	public static class DescriptorImpl extends GitSCMExtensionDescriptor {

		public FormValidation doCheckExcludedMessage(@QueryParameter String value) {
			try {
				Pattern.compile(value);
			} catch (PatternSyntaxException ex){
				return FormValidation.error(ex.getMessage());
			}
			return FormValidation.ok();
		}

        public FormValidation doMatchMessage(
                @QueryParameter String excludedMessage,
                @QueryParameter boolean includeInsteadOfExclude,
                @QueryParameter boolean partialMatch,
                @QueryParameter String testMessage) {
            Pattern pattern = null;
            
            try {
                pattern = Pattern.compile(excludedMessage);
            } catch (PatternSyntaxException ex){
                return FormValidation.error(ex.getMessage());
            }
            
            boolean matches = partialMatch ? pattern.matcher(testMessage).find() : pattern.matcher(testMessage).matches();
            
            String matchMessage = matches ? "Pattern matches, " : "Pattern does not match, ";
            String consider = includeInsteadOfExclude ^ matches ? "commit <b>would not</b> considered." : "commit <b>would</b> considered.";
            
            return FormValidation.okWithMarkup(matchMessage + consider);
        }
        
		@Override
		public String getDisplayName() {
			return "Polling acts only on commits with/without certain messages";
		}
	}
}
