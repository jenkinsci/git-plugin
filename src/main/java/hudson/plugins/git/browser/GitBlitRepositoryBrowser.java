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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

     private String encodeString(final String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    @Extension
    @Symbol("gitblit")
    public static class ViewGitWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        public String getDisplayName() {
            return "gitblit";
        }

        @Override
        @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
                            justification = "Inherited javadoc commits that req is non-null")
        public GitBlitRepositoryBrowser newInstance(StaplerRequest req, @NonNull JSONObject jsonObject) throws FormException {
            return req.bindJSON(GitBlitRepositoryBrowser.class, jsonObject);
        }

        @RequirePOST
        public FormValidation doCheckRepoUrl(@AncestorInPath Item project, @QueryParameter(fixEmpty = true) final String repoUrl)
                throws IOException, ServletException, URISyntaxException {

            String cleanUrl = Util.fixEmptyAndTrim(repoUrl);
            if (initialChecksAndReturnOk(project, cleanUrl))
            {
                return FormValidation.ok();
            }
            if (!validateUrl(cleanUrl))
            {
                return FormValidation.error(Messages.invalidUrl());
            }
            return new URLCheck() {
                protected FormValidation check() throws IOException {
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
        }
    }
}
