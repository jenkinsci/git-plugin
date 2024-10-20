package hudson.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Computer;
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
import jenkins.plugins.git.GitSCMSource;
import jenkins.security.FIPS140;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;
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
import java.util.Objects;
import java.util.UUID;

import static hudson.Util.fixEmpty;
import static hudson.Util.fixEmptyAndTrim;
import hudson.model.FreeStyleProject;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.interceptor.RequirePOST;

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
        if (FIPS140.useCompliantAlgorithms() && StringUtils.isNotEmpty(this.credentialsId) && StringUtils.startsWith(this.url, "http:")) {
            throw new IllegalArgumentException(Messages.git_fips_url_notsecured());
        }
    }

    @Exported
    @Whitelisted
    public String getName() {
        return name;
    }

    @Exported
    @Whitelisted
    public String getRefspec() {
        return refspec;
    }

    @Exported
    @CheckForNull
    @Whitelisted
    public String getUrl() {
        return url;
    }

    @Exported
    @Whitelisted
    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public String toString() {
        return getRefspec() + " => " + getUrl() + " (" + getName() + ")";
    }

    private final static Pattern SCP_LIKE = Pattern.compile("(.*):(.*)");

    @Extension
    public static class DescriptorImpl extends Descriptor<UserRemoteConfig> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project,
                                                     @QueryParameter String url,
                                                     @QueryParameter String credentialsId) {
            if (project == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
                project != null && !project.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            if (project == null) {
                /* Construct a fake project, suppress the deprecation warning because the
                 * replacement for the deprecated API isn't accessible in this context. */
                @SuppressWarnings("deprecation")
                Item fakeProject = new FreeStyleProject(Jenkins.get(), "fake-" + UUID.randomUUID());
                project = fakeProject;
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            project instanceof Queue.Task t
                                    ? Tasks.getAuthenticationOf(t)
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
            if (project == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
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
                    .listCredentialsInItem(StandardUsernameCredentials.class, project, project instanceof Queue.Task t
                                    ? Tasks.getAuthenticationOf2(t)
                                    : ACL.SYSTEM2,
                            GitURIRequirementsBuilder.fromUri(url).build(),
                            GitClient.CREDENTIALS_MATCHER)) {
                if (Objects.equals(value, o.value)) {
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

        @RequirePOST
        public FormValidation doCheckUrl(@AncestorInPath Item item,
                                         @QueryParameter String credentialsId,
                                         @QueryParameter String value) throws IOException, InterruptedException {

            if (!GitSCMSource.isFIPSCompliantTLS(credentialsId, value)) {
                return FormValidation.error(hudson.plugins.git.Messages.git_fips_url_notsecured());
            }

            // Normally this permission is hidden and implied by Item.CONFIGURE, so from a view-only form you will not be able to use this check.
            // (TODO under certain circumstances being granted only USE_OWN might suffice, though this presumes a fix of JENKINS-31870.)
            if (item == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
                item != null && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return FormValidation.ok();
            }

            String url = Util.fixEmptyAndTrim(value);
            if (url == null)
                return FormValidation.error(Messages.UserRemoteConfig_CheckUrl_UrlIsNull());

            if (url.indexOf('$') >= 0)
                // set by variable, can't validate
                return FormValidation.ok();

            // get git executable on controller
            EnvVars environment;
            Jenkins jenkins = Jenkins.get();
            if (item instanceof Job<?,?> job) {
                environment = job.getEnvironment(jenkins, TaskListener.NULL);
            } else {
                Computer computer = jenkins.toComputer();
                environment = computer == null ? new EnvVars() : computer.buildEnvironment(TaskListener.NULL);
            }

            GitClient git = Git.with(TaskListener.NULL, environment)
                    .using(GitTool.getDefaultInstallation().getGitExe())
                    .getClient();
            StandardCredentials credential = lookupCredentials(item, credentialsId, url);
            git.addDefaultCredentials(credential);

            // Should not track credentials use in any checkURL method, rather should track
            // credentials use at the point where the credential is used to perform an
            // action (like poll the repository, clone the repository, publish a change
            // to the repository).

            // attempt to connect the provided URL
            try {
                git.getHeadRev(url, "HEAD");
            } catch (GitException e) {
                return FormValidation.error(Messages.UserRemoteConfig_FailedToConnect(e.getMessage()));
            }

            return FormValidation.ok();
        }

        /**
         * A form validation logic as a method to check the specification of 'refSpec' and notify the user about
         * illegal specs before applying the project configuration
         * @param name Name of the remote repository
         * @param url Repository URL
         * @param value value of RefSpec
         * @return FormValidation.ok() or FormValidation.error()
         * @throws IllegalArgumentException on unexpected argument error
         */
        public FormValidation doCheckRefspec(@QueryParameter String name,
                                             @QueryParameter String url,
                                             @QueryParameter String value) throws IllegalArgumentException {

            String refSpec = Util.fixEmptyAndTrim(value);

            if(refSpec == null){
                // We fix empty field value with a default refspec, hence we send ok.
                return FormValidation.ok();
            }

            if(refSpec.contains("$")){
                // set by variable, can't validate
                return FormValidation.ok();
            }

            Config repoConfig = new Config();

            repoConfig.setString("remote", name, "url", url);
            repoConfig.setString("remote", name, "fetch", refSpec);

            //Attempt to fetch remote repositories using the repoConfig
            try {
                RemoteConfig.getAllRemoteConfigs(repoConfig);
            } catch (Exception e) {
                return FormValidation.error(Messages.UserRemoteConfig_CheckRefSpec_InvalidRefSpec());
            }

            return FormValidation.ok();
        }

        private static StandardCredentials lookupCredentials(@CheckForNull Item project, String credentialId, String uri) {
            return (credentialId == null) ? null : CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentialsInItem(StandardCredentials.class, project, ACL.SYSTEM2,
                                GitURIRequirementsBuilder.fromUri(uri).build()),
                        CredentialsMatchers.withId(credentialId));
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
