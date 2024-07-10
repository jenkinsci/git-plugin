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
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
    @Symbol("assembla")
    public static class AssemblaWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        public String getDisplayName() {
            return "AssemblaWeb";
        }

        @Override
        @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
                            justification = "Inherited javadoc commits that req is non-null")
        public AssemblaWeb newInstance(StaplerRequest req, @NonNull JSONObject jsonObject) throws FormException {
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
                protected FormValidation check() throws IOException {
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
                        return FormValidation.error("Exception reading from Assembla URL " + cleanUrl + " : " + handleIOException(v, e));
                    }
                }
            }.check();
        }

        private boolean checkURIFormatAndHostName(String url, String hostNameFragment) throws URISyntaxException {
            URI uri = new URI(url);
            hostNameFragment = hostNameFragment + ".";
            String uriHost = uri.getHost();
            return uriHost != null && validateUrl(uri.toString()) && uriHost.contains(hostNameFragment);
        }
    }
}
