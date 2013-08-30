package hudson.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.GitURIRequirementsBuilder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.io.Serializable;
import java.util.regex.Pattern;

@ExportedBean
public class UserRemoteConfig extends AbstractDescribableImpl<UserRemoteConfig> implements Serializable {

    private String name;
    private String refspec;
    private String url;
    private String credentialsId;

    @DataBoundConstructor
    public UserRemoteConfig(String url, String name, String refspec, String credentialsId) {
        this.url = StringUtils.trim(url);
        this.name = name;
        this.refspec = refspec;
        this.credentialsId = credentialsId;
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

    @Exported
    public String getCredentialsId() {
        return credentialsId;
    }

    public String toString() {
        return getRefspec() + " => " + getUrl() + " (" + getName() + ")";
    }

    private final static Pattern SCP_LIKE = Pattern.compile("(.*):(.*)");

    @Extension
    public static class DescriptorImpl extends Descriptor<UserRemoteConfig> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath AbstractProject project,
                                                     @QueryParameter String url) {
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            GitClient.CREDENTIALS_MATCHER,
                            CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                    project,
                                    ACL.SYSTEM,
                                    GitURIRequirementsBuilder.fromUri(url).build())
                    );
        }

        public FormValidation doCheckUrl(@AncestorInPath AbstractProject project,
                                         @QueryParameter String credentialId,
                                         @QueryParameter String value) throws IOException, InterruptedException {

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
            git.addDefaultCredentials(lookupCredentials(project, credentialId, url));

            // attempt to connect the provided URL
            try {
                git.getHeadRev(url, "HEAD");
            } catch (GitException e) {
                return FormValidation.error(Messages.UserRemoteConfig_FailedToConnect(e.getMessage()));
            }

            return FormValidation.ok();
        }

        private static StandardCredentials lookupCredentials(AbstractProject project, String credentialId, String uri) {
            return (credentialId == null) ? null : CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentials(StandardCredentials.class, project, ACL.SYSTEM, GitURIRequirementsBuilder.fromUri(



                                uri).build()),
                        CredentialsMatchers.withId(credentialId));
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
