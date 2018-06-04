package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import hudson.util.FormValidation.URLCheck;

import jenkins.model.Jenkins;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Manolo Carrasco Moñino
 */
public class Gitiles extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public Gitiles(String repoUrl) {
        super(repoUrl);
    }

    // https://gwt.googlesource.com/gwt/+/d556b611fef6df7bfe07682262b02309e6d41769%5E%21/#F3
    @Override
    public URL getDiffLink(Path path) throws IOException {
        URL url = getUrl();
        return new URL(url + "+/" + path.getChangeSet().getId() + "%5E%21");
    }

    // https://gwt.googlesource.com/gwt/+blame/d556b611fef6df7bfe07682262b02309e6d41769/dev/codeserver/java/com/google/gwt/dev/codeserver/ModuleState.java
    @Override
    public URL getFileLink(Path path) throws IOException {
        URL url = getUrl();
        return new URL(url + "+blame/" + path.getChangeSet().getId() + "/" + path.getPath());
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url + "+/" + changeSet.getId() + "%5E%21");
    }


    @Extension
    public static class ViewGitWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @Nonnull
        public String getDisplayName() {
            return "gitiles";
        }

        @Override
        public Gitiles newInstance(StaplerRequest req, @Nonnull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            return req.bindJSON(Gitiles.class, jsonObject);
        }

        @RequirePOST
        public FormValidation doCheckUrl(@QueryParameter(fixEmpty = true) final String url) throws IOException, ServletException {
            if (url == null) // nothing entered yet
                return FormValidation.ok();
            // Connect to URL and check content only if we have admin permission
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null || !jenkins.hasPermission(Jenkins.ADMINISTER))
                return FormValidation.ok();
            return new URLCheck() {
                protected FormValidation check() throws IOException, ServletException {
                    String v = url;
                    if (!v.endsWith("/"))
                        v += '/';

                    try {
                        // gitiles has a line in main page indicating how to clone the project
                        if (findText(open(new URL(v)), "git clone")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it doesn't look like Gitiles");
                        }
                    } catch (IOException e) {
                        return handleIOException(v, e);
                    }
                }
            }.check();
        }
    }
}
