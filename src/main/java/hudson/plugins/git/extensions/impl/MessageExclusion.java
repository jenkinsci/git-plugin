package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.regex.Pattern;

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
	public MessageExclusion(String excludedMessage) { this.excludedMessage = excludedMessage; }

	@Override
	public boolean requiresWorkspaceForPolling() { return true; }

	public String getExcludedMessage() { return excludedMessage; }

	@Override
	public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws IOException, InterruptedException, GitException {
		if (excludedPattern == null){
			excludedPattern = Pattern.compile(excludedMessage);
		}
		String msg = commit.getComment();
		if (excludedPattern.matcher(msg).matches()){
			listener.getLogger().println("Ignored commit " + commit.getId() + ": Found excluded message: " + msg);
			return true;
		}

		return null;
	}

	@Extension
	public static class DescriptorImpl extends GitSCMExtensionDescriptor {
		@Override
		public String getDisplayName() {
			return "Polling ignores commits with certain messages";
		}
	}
}
