package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * Tags every build.
 *
 * @author Kohsuke Kawaguchi
 */
public class PerBuildTag extends GitSCMExtension {
    @DataBoundConstructor
    public PerBuildTag() {
    }

    @Override
    public void onCheckoutCompleted(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        int buildNumber = build.getNumber();
        String buildnumber = "jenkins-" + build.getParent().getName().replace(" ", "_") + "-" + buildNumber;

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
