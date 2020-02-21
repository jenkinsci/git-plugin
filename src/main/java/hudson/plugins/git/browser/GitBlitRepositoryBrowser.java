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
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

public class GitBlitRepositoryBrowser extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    private final String projectName;

    @DataBoundConstructor
    public GitBlitRepositoryBrowser(String repoUrl, String projectName) {
        super(repoUrl);
        this.projectName = projectName;
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        URL url = getUrl();
        return new URL(url,
                String.format(url.getPath() + "blobdiff?r=%s&h=%s&hb=%s", encodeString(projectName), path.getChangeSet().getId(),
                        path.getChangeSet().getParentCommit()));
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType().equals(EditType.DELETE)) {
            return null;
        }
        URL url = getUrl();
        return new URL(url,
                String.format(url.getPath() + "blob?r=%s&h=%s&f=%s", encodeString(projectName), path.getChangeSet().getId(),
                        encodeString(path.getPath())));
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, String.format(url.getPath() + "commit?r=%s&h=%s", encodeString(projectName), changeSet.getId()));
    }

    public String getProjectName() {
        return projectName;
    }

    private String encodeString(final String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, "UTF-8").replaceAll("\\+", "%20");
    }

    @Extension
    public static class ViewGitWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @Nonnull
        public String getDisplayName() {
            return "gitblit";
        }

        @Override
        public GitBlitRepositoryBrowser newInstance(StaplerRequest req, @Nonnull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            return req.bindJSON(GitBlitRepositoryBrowser.class, jsonObject);
        }

        @RequirePOST
        public FormValidation doCheckRepoUrl(@AncestorInPath Item project, @QueryParameter(fixEmpty = true) final String repoUrl)
                throws IOException, ServletException, URISyntaxException {

            String cleanUrl = Util.fixEmptyAndTrim(repoUrl);

            if (cleanUrl == null) {
                return FormValidation.ok();
            }

            if (project == null || !project.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            if (cleanUrl.contains("$")) {
                // set by variable, can't validate
                return FormValidation.ok();
            }
            FormValidation response;
            if (checkURIFormat(cleanUrl)) {
                return new URLCheck() {
                    protected FormValidation check() throws IOException, ServletException {
                        String v = cleanUrl;
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
            } else {
                response = FormValidation.error(Messages.invalidUrl());
            }
            return response;
        }
    }
}
