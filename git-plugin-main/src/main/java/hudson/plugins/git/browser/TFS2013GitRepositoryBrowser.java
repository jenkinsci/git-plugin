package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitSCM;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Browser for Git repositories on Microsoft Team Foundation Server (TFS) 2013 and higher versions using the
 * same format. This includes Git repositories hosted with the Visual Studio Team Services.
 */
public class TFS2013GitRepositoryBrowser extends GitRepositoryBrowser {

    @DataBoundConstructor
    public TFS2013GitRepositoryBrowser(String repoUrl) {
        super(repoUrl);
    }

    @Override
    public URL getDiffLink(GitChangeSet.Path path) throws IOException {
        String spec = String.format("commit/%s#path=%s&_a=compare", path.getChangeSet().getId(), path.getPath());
        return new URL(getRepoUrl(path.getChangeSet()), spec);
    }

    @Override
    public URL getFileLink(GitChangeSet.Path path) throws IOException {
        String spec = String.format("commit/%s#path=%s&_a=history", path.getChangeSet().getId(), path.getPath());
        return encodeURL(new URL(getRepoUrl(path.getChangeSet()), spec));
    }

    @Override
    public URL getChangeSetLink(GitChangeSet gitChangeSet) throws IOException {
        return new URL(getRepoUrl(gitChangeSet), "commit/" + gitChangeSet.getId());
    }

    /*default*/ URL getRepoUrl(GitChangeSet changeSet) throws IOException { // default visibility for tests
        String result = getRepoUrl();
        
        if (StringUtils.isBlank(result))
            return normalizeToEndWithSlash(getUrlFromFirstConfiguredRepository(changeSet));

        else if (!result.contains("/"))
            return normalizeToEndWithSlash(getResultFromNamedRepository(changeSet));

        return getUrl();
    }

    private URL getResultFromNamedRepository(GitChangeSet changeSet) throws MalformedURLException {
        GitSCM scm = getScmFromProject(changeSet);
        return new URL(scm.getRepositoryByName(getRepoUrl()).getURIs().get(0).toString());
    }

    private URL getUrlFromFirstConfiguredRepository(GitChangeSet changeSet) throws MalformedURLException {
        GitSCM scm = getScmFromProject(changeSet);
        return new URL(scm.getRepositories().get(0).getURIs().get(0).toString());
    }

    private GitSCM getScmFromProject(GitChangeSet changeSet) {
        AbstractProject<?,?> build = (AbstractProject<?, ?>) changeSet.getParent().getRun().getParent();

        return (GitSCM) build.getScm();
    }

    @Extension
    public static class TFS2013GitRepositoryBrowserDescriptor extends Descriptor<RepositoryBrowser<?>> {

        private static final String REPOSITORY_BROWSER_LABEL = "Microsoft Team Foundation Server/Visual Studio Team Services";
        @NonNull
        public String getDisplayName() {
            return REPOSITORY_BROWSER_LABEL;
        }

        @Override
        public TFS2013GitRepositoryBrowser newInstance(StaplerRequest req, @NonNull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            try {
                req.getSubmittedForm();
            } catch (ServletException e) {
                e.printStackTrace();
            }
            return req.bindJSON(TFS2013GitRepositoryBrowser.class, jsonObject);
        }

        /**
         * Performs on-the-fly validation of the URL.
         * @param value URL value to be checked
         * @param project project context used for check
         * @return form validation result
         * @throws IOException on input or output error
         * @throws ServletException on servlet error
         */
        @RequirePOST
        public FormValidation doCheckRepoUrl(@QueryParameter(fixEmpty = true) String value, @AncestorInPath AbstractProject project) throws IOException,
                ServletException {

            // Connect to URL and check content only if we have admin permission
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER))
                return FormValidation.ok();

            if (value == null) // nothing entered yet
                value = "origin";

            if (!value.contains("/") && project != null) {
                GitSCM scm = (GitSCM) project.getScm();
                RemoteConfig remote = scm.getRepositoryByName(value);
                if (remote == null)
                    return FormValidation.errorWithMarkup("There is no remote with the name <code>" + Util.escape(value) + "</code>");
                
                value = remote.getURIs().get(0).toString();
            }
            
            if (!value.endsWith("/"))
                value += '/';
            if (!URL_PATTERN.matcher(value).matches())
                return FormValidation.errorWithMarkup("The URL should end like <code>.../_git/foobar/</code>");

            final String finalValue = value;
            return new FormValidation.URLCheck() {
                @Override
                protected FormValidation check() throws IOException, ServletException {
                    try {
                        if (findText(open(new URL(finalValue)), "icrosoft")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it doesn't look like a Microsoft server");
                        }
                    } catch (IOException e) {
                        return handleIOException(finalValue, e);
                    }
                }
            }.check();
        }

        private static final Pattern URL_PATTERN = Pattern.compile(".+/_git/[^/]+/");
    }
}
