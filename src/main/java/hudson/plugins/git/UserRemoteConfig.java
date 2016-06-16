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
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
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

import static hudson.Util.*;

@ExportedBean
public class UserRemoteConfig extends AbstractDescribableImpl<UserRemoteConfig> implements Serializable {

    private String name;
    private String refspec;
    private String url;
    private String credentialsId;

    @DataBoundConstructor
    public UserRemoteConfig(String url, String name, String refspec, String credentialsId) {
        this.url = fixEmptyAndTrim(url);
        this.name = fixEmpty(name);
        this.refspec = fixEmpty(refspec);
        this.credentialsId = fixEmpty(credentialsId);
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project,
                                                     @QueryParameter String url) {
            if (project == null || !project.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
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

        public FormValidation doCheckCredentialsId(@AncestorInPath Item project,
                                                   @QueryParameter String url,
                                                   @QueryParameter String value) {
            if (project == null || !project.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            value = Util.fixEmptyAndTrim(value);
            if (value == null) {
                return FormValidation.ok();
            }

            url = Util.fixEmptyAndTrim(url);
            if (url == null)
            // not set, can't check
            {
                return FormValidation.ok();
            }

            if (url.indexOf('$') >= 0)
            // set by variable, can't check
            {
                return FormValidation.ok();
            }

            StandardCredentials credentials = lookupCredentials(project, value, url);

            if (credentials == null) {
                // no credentials available, can't check
                return FormValidation.warning("Cannot find any credentials with id " + value);
            }

            // TODO check if this type of credential is acceptible to the Git client or does it merit warning the user

            return FormValidation.ok();
        }

        public FormValidation doCheckUrl(@AncestorInPath Item item,
                                         @QueryParameter String credentialsId,
                                         @QueryParameter String value) throws IOException, InterruptedException {

            if (item == null || !item.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error("Please enter Git repository.");

            if (url.indexOf('$') >= 0)
                // set by variable, can't validate
                return FormValidation.ok();

            // get git executable on master
            EnvVars environment;
            final Jenkins jenkins = Jenkins.getActiveInstance();
            if (item instanceof Job) {
                environment = ((Job) item).getEnvironment(jenkins, TaskListener.NULL);
            } else {
                environment = jenkins.getComputer("(master)").buildEnvironment(TaskListener.NULL);
            }

            GitClient git = Git.with(TaskListener.NULL, environment)
                    .using(GitTool.getDefaultInstallation().getGitExe())
                    .getClient();
            git.addDefaultCredentials(lookupCredentials(item, credentialsId, url));

            // attempt to connect the provided URL
            try {
                git.getHeadRev(url, "HEAD");
            } catch (GitException e) {
                return FormValidation.error(Messages.UserRemoteConfig_FailedToConnect(e.getMessage()));
            }

            return FormValidation.ok();
        }

        private static StandardCredentials lookupCredentials(Item project, String credentialId, String uri) {
            return (credentialId == null) ? null : CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentials(StandardCredentials.class, project, ACL.SYSTEM,
                                GitURIRequirementsBuilder.fromUri(uri).build()),
                        CredentialsMatchers.withId(credentialId));
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
