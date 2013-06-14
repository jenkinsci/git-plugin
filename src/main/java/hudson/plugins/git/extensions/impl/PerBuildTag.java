package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;

/**
 * Tags every build.
 *
 * @author Kohsuke Kawaguchi
 */
public class PerBuildTag extends GitSCMExtension {
    @Override
    public void onCheckoutCompleted(AbstractBuild<?, ?> build, Launcher launcher, GitClient git, BuildListener listener) throws IOException, InterruptedException, GitException {
        int buildNumber = build.getNumber();
        String buildnumber = "jenkins-" + build.getProject().getName().replace(" ", "_") + "-" + buildNumber;

        git.tag(buildnumber, "Jenkins Build #" + buildNumber);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Create a tag for every build";
        }
    }
}
