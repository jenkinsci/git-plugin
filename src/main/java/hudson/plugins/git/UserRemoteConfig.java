package hudson.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
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
import java.util.UUID;
import org.apache.commons.lang.StringUtils;

import static hudson.Util.fixEmpty;
import static hudson.Util.fixEmptyAndTrim;
import hudson.model.FreeStyleProject;

@ExportedBean
public class UserRemoteConfig extends AbstractDescribableImpl<UserRemoteConfig> implements Serializable {

    private String name;
    private String refspec;
    private String url;
    private String credentialsId;

    @DataBoundConstructor
    public UserRemoteConfig(String url, String name, String refspec, @CheckForNull String credentialsId) {
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
    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    public String toString() {
        return getRefspec() + " => " + getUrl() + " (" + getName() + ")";
    }

    private final static Pattern SCP_LIKE = Pattern.compile("(.*):(.*)");

    @Extension
    public static class DescriptorImpl extends Descriptor<UserRemoteConfig> {

        @SuppressFBWarnings(value="NP_NULL_PARAM_DEREF", justification="fixed in credentials 2.1.9")
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project,
                                                     @QueryParameter String url,
                                                     @QueryParameter String credentialsId) {
            if (project == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                project != null && !project.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            if (project == null) {
                /* Construct a fake project */
                project = new FreeStyleProject(Jenkins.getInstance(), "fake-" + UUID.randomUUID().toString());
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            project instanceof Queue.Task
                                    ? Tasks.getAuthenticationOf((Queue.Task) project)
                                    : ACL.SYSTEM,
                            project,
                            StandardUsernameCredentials.class,
                            GitURIRequirementsBuilder.fromUri(url).build(),
                            GitClient.CREDENTIALS_MATCHER)
                    .includeCurrentValue(credentialsId);
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath Item project,
                                                   @QueryParameter String url,
                                                   @QueryParameter String value) {
            if (project == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                project != null && !project.hasPermission(Item.EXTENDED_READ)) {
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
            for (ListBoxModel.Option o : CredentialsProvider
                    .listCredentials(StandardUsernameCredentials.class, project, project instanceof Queue.Task
                                    ? Tasks.getAuthenticationOf((Queue.Task) project)
                                    : ACL.SYSTEM,
                            GitURIRequirementsBuilder.fromUri(url).build(),
                            GitClient.CREDENTIALS_MATCHER)) {
                if (StringUtils.equals(value, o.value)) {
                    // TODO check if this type of credential is acceptable to the Git client or does it merit warning
                    // NOTE: we would need to actually lookup the credential to do the check, which may require
                    // fetching the actual credential instance from a remote credentials store. Perhaps this is
                    // not required
                    return FormValidation.ok();
                }
            }
            // no credentials available, can't check
            return FormValidation.warning("Cannot find any credentials with id " + value);
        }

        @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification="Jenkins.getInstance() is not null")
        public FormValidation doCheckUrl(@AncestorInPath Item item,
                                         @QueryParameter String credentialsId,
                                         @QueryParameter String value) throws IOException, InterruptedException {

            // Normally this permission is hidden and implied by Item.CONFIGURE, so from a view-only form you will not be able to use this check.
            // (TODO under certain circumstances being granted only USE_OWN might suffice, though this presumes a fix of JENKINS-31870.)
            if (item == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                item != null && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
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
                environment = jenkins.toComputer().buildEnvironment(TaskListener.NULL);
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

        private static StandardCredentials lookupCredentials(@CheckForNull Item project, String credentialId, String uri) {
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
