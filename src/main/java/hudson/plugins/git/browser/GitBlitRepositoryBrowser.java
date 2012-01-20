package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import hudson.util.FormValidation.URLCheck;
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

public class GitBlitRepositoryBrowser extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;
    private final URL url;
    private final String projectName;

    @DataBoundConstructor
    public GitBlitRepositoryBrowser(String url, String projectName) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
        this.projectName = projectName;
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        return new URL(url,
                String.format(url.getPath() + "blobdiff?r=%s&h=%s&hb=%s", encodeString(projectName), path.getChangeSet().getId(),
                        path.getChangeSet().getParentCommit()));
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType().equals(EditType.DELETE)) {
            return null;
        }
        return new URL(url,
                String.format(url.getPath() + "blob?r=%s&h=%s&f=%s", encodeString(projectName), path.getChangeSet().getId(),
                        encodeString(path.getPath())));
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(url, String.format(url.getPath() + "commit?r=%s&h=%s", encodeString(projectName), changeSet.getId()));
    }

    public URL getUrl() {
        return url;
    }

    public String getProjectName() {
        return projectName;
    }

     private String encodeString(final String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, "UTF-8").replaceAll("\\+", "%20");
    }
    @Extension
    public static class ViewGitWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "gitblit";
        }

        @Override
        public GitBlitRepositoryBrowser newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindParameters(GitBlitRepositoryBrowser.class, "gitblit.");
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
                        if (findText(open(new URL(v)), "Gitblit")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it doesn't look like Gitblit");
                        }
                    } catch (IOException e) {
                        return handleIOException(v, e);
                    }
                }
            }.check();
        }
    }
}
