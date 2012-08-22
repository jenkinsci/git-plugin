package hudson.plugins.git;

import java.io.IOException;
import java.io.Serializable;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.util.FormValidation;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

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
            GitTool.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(GitTool.DescriptorImpl.class);
            String gitExe = descriptor.getInstallations()[0].forNode(Jenkins.getInstance(), TaskListener.NULL).getGitExe();
            IGitAPI git = new GitAPI(gitExe, null, TaskListener.NULL, environment, null);

            // attempt to connect the provided URL
            try {
                String headRevision = git.getHeadRev(url, "HEAD");
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
