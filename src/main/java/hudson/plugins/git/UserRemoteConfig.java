package hudson.plugins.git;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import hudson.plugins.git.GitTool;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.io.Serializable;

@ExportedBean
public class UserRemoteConfig extends AbstractDescribableImpl<UserRemoteConfig> implements Serializable {

    private String name;
    private String refspec;
    private String url;

    @DataBoundConstructor
    public UserRemoteConfig(String url, String name, String refspec) {
        this.url = StringUtils.trim(url);
        this.name = name;
        this.refspec = refspec;
    }

    @Exported
    public String getName() {
        return name;
    }

    @Exported
    public String getRefspec() {
        return refspec;
    }

    @Exported
    public String getUrl() {
        return url;
    }

    public String toString() {
        return getRefspec() + " => " + getUrl() + " (" + getName() + ")";
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<UserRemoteConfig> {

        public FormValidation doCheckUrl(@QueryParameter String value) throws IOException, InterruptedException {

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error("Please enter Git repository.");

            if (url.indexOf('$') >= 0)
                // set by variable, can't validate
                return FormValidation.ok();

            if (!Jenkins.getInstance().hasPermission(Item.CONFIGURE))
                return FormValidation.ok();

            // get git executable on master
            final EnvVars environment = new EnvVars(System.getenv()); // GitUtils.getPollEnvironment(project, null, launcher, TaskListener.NULL, false);

            GitClient git = Git.with(TaskListener.NULL, environment)
                    .using(GitTool.getDefaultInstallation().getGitExe())
                    .getClient();

            // attempt to connect the provided URL
            try {
                git.getHeadRev(url, "HEAD");
            } catch (GitException e) {
                return FormValidation.error(Messages.UserRemoteConfig_FailedToConnect(e.getMessage()));
            }

            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
