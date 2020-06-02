package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.plugins.git.Messages;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import hudson.util.FormValidation.URLCheck;
import net.sf.json.JSONObject;
import org.apache.commons.validator.routines.UrlValidator;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * AssemblaWeb Git Browser URLs
 */
public class AssemblaWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public AssemblaWeb(String repoUrl) {
        super(repoUrl);
    }

    /**
     * Creates a link to the change set
     * http://[AssemblaWeb URL]/commits/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException on input or output error
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commits/" + changeSet.getId());
    }

    /**
     * Shows the difference between the referenced commit and the previous commit.
     * The changes section also display diffs, so a separate url is unnecessary.
     * http://[Assembla URL]/commits/[commit]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();
        return getChangeSetLink(changeSet);
    }

    /**
     * Creates a link to the file.
     * http://[Assembla URL]/nodes/[commit]/[path]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();
        URL url = getUrl();
        if (path.getEditType() == EditType.DELETE) {
            return encodeURL(new URL(url, url.getPath() + "nodes/" + changeSet.getParentCommit() + path.getPath()));
        } else {
            return encodeURL(new URL(url, url.getPath() + "nodes/" + changeSet.getId() + path.getPath()));
        }
    }

    @Extension
    public static class AssemblaWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "AssemblaWeb";
        }

        @Override
        public AssemblaWeb newInstance(StaplerRequest req, @NonNull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            return req.bindJSON(AssemblaWeb.class, jsonObject);
        }

        @RequirePOST
        public FormValidation doCheckRepoUrl(@AncestorInPath Item project, @QueryParameter(fixEmpty = true) final String repoUrl)
                throws IOException, ServletException, URISyntaxException {

            String cleanUrl = Util.fixEmptyAndTrim(repoUrl);
            if (initialChecksAndReturnOk(project, cleanUrl))
            {
                return FormValidation.ok();
            }
            // Connect to URL and check content only if we have permission
            FormValidation validation = checkURIFormatAndHostName(cleanUrl, "assembla");
            if (validation != FormValidation.ok()) {
                return validation;
            }
            return new URLCheck() {
                protected FormValidation check() throws IOException, ServletException {
                    String v = cleanUrl;
                    if (!v.endsWith("/")) {
                        v += '/';
                    }

                    try {
                        if (findText(open(new URL(v)), "Assembla")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it does not look like Assembla");
                        }
                    } catch (IOException e) {
                        return handleIOException(v, e);
                    }
                }
            }.check();
        }

        /*
         * Return FormValidation for url after checking that the hostname of the URL includes hostNameFragment.
         */
        private FormValidation checkURIFormatAndHostName(String url, String hostNameFragment) {
            URL checkURL;
            try {
                checkURL = new URL(url);
            } catch (MalformedURLException e) {
                return FormValidation.error(e.getMessage());
            }
            String hostname = checkURL.getHost();
            if (hostname == null) {
                return FormValidation.error("Invalid URL: " + url + " null hostname");
            }
            if (hostname.isEmpty()) {
                return FormValidation.error("Invalid URL: " + url + " could not parse hostname in URL");
            }
            if (!hostname.contains(hostNameFragment + ".")) {
                return FormValidation.error("Invalid URL: " + url + " hostname does not include " + hostNameFragment + ".");
            }
            String[] schemes = {"http", "https"};
            UrlValidator urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS);
            if (!urlValidator.isValid(url)) {
                return FormValidation.error("Invalid URL: " + url);
            }
            return FormValidation.ok();
        }
    }
}
