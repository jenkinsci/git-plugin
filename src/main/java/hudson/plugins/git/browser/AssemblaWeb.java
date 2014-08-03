package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import hudson.util.FormValidation.URLCheck;
import hudson.scm.browsers.QueryBuilder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Git Browser URLs
 */
public class AssemblaWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public AssemblaWeb(String repoUrl) {
        super(repoUrl);
    }

    private QueryBuilder param(URL url) {
        return new QueryBuilder(url.getQuery());
    }

    /**
     * Creates a link to the change set
     * http://[AssemblaWeb URL]/commits/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commit/" + changeSet.getId());
    }

    /**
     * Shows the difference between the referenced commit and the previous commit.
     * The changes section also display diffs, so a seperate url is unncessary.
     * http://[Assembla URL]/commits/[commit]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
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
     * @throws IOException
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();
        URL url = getUrl();
        if (path.getEditType() == EditType.DELETE) {
            return new URL(url, url.getPath() + "nodes/" + changeSet.getParentCommit() + path.getPath());
        } else {
            return new URL(url, url.getPath() + "nodes/" + changeSet.getId() + path.getPath());
        }
    }

    @Extension
    public static class AssemblaWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "AssemblaWeb";
        }

        @Override
        public CGit newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindJSON(CGit.class, jsonObject);
        }

        public FormValidation doCheckUrl(@QueryParameter(fixEmpty = true) final String url)
                throws IOException, ServletException {
            if (url == null) // nothing entered yet
            {
                return FormValidation.ok();
            }
            return new URLCheck() {
                protected FormValidation check() throws IOException, ServletException {
                    String v = url;
                    if (!v.endsWith("/")) {
                        v += '/';
                    }

                    try {
                        if (findText(open(new URL(v)), "Assembla")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it doesn't look like Assembla");
                        }
                    } catch (IOException e) {
                        return handleIOException(v, e);
                    }
                }
            }.check();
    }
}
