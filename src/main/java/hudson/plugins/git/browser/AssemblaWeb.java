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

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
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
        @Nonnull
        public String getDisplayName() {
            return "AssemblaWeb";
        }

        @Override
        public AssemblaWeb newInstance(StaplerRequest req, @Nonnull JSONObject jsonObject) throws FormException {
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
            if (!checkURIFormatAndHostName(cleanUrl, "assembla")) {
                return FormValidation.error(Messages.invalidUrl());
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

        private boolean checkURIFormatAndHostName(String url, String browserName) throws URISyntaxException {
            URI uri = new URI(url);
            String[] schemes = {"http", "https"};
            UrlValidator urlValidator = new UrlValidator(schemes);
            browserName = browserName + ".";
            return urlValidator.isValid(uri.toString()) && uri.getHost().contains(browserName);
        }
    }
}
