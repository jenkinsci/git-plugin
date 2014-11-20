package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.util.LogTaskListener;
import hudson.util.VariableResolver;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Tags every build.
 *
 * @author Kohsuke Kawaguchi
 */
public class PerBuildTag extends GitSCMExtension {

	private final String tagName;
	private final String tagComment;

	public String getTagName() {
		return tagName;
	}

	public String getTagComment() {
		return tagComment;
	}

    @DataBoundConstructor
    public PerBuildTag(String tagComment, String tagName) {
		this.tagComment = tagComment;
		this.tagName = tagName;
    }

    @Override
    public void onCheckoutCompleted(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
		Map<String, String> messageEnvVars = new HashMap<String, String>();

		messageEnvVars.putAll(build.getCharacteristicEnvVars());
		messageEnvVars.putAll(build.getBuildVariables());
		messageEnvVars.putAll(build.getEnvironment(listener));

		git.tag(Util.replaceMacro(tagName, messageEnvVars),
				Util.replaceMacro(tagComment, messageEnvVars));
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Create a tag for every build";
        }
    }
}
