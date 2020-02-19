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
import hudson.scm.browsers.QueryBuilder;
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
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

public class ViewGitWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    private final String projectName;

    @DataBoundConstructor
    public ViewGitWeb(String repoUrl, String projectName) {
        super(repoUrl);
        this.projectName = projectName;
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        if (path.getEditType() == EditType.EDIT) {
            URL url = getUrl();
            String spec = buildCommitDiffSpec(url, path);
            return new URL(url, url.getPath() + spec);
        }
        return null;
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        URL url = getUrl();
        if (path.getEditType() == EditType.DELETE) {
            String spec = buildCommitDiffSpec(url, path);
            return encodeURL(new URL(url, url.getPath() + spec));
        }
        String spec = param(url).add("p=" + projectName).add("a=viewblob").add("h=" + path.getDst()).add("f=" + path.getPath()).toString();
        return encodeURL(new URL(url, url.getPath() + spec));
    }

    private String buildCommitDiffSpec(URL url, Path path)
            throws UnsupportedEncodingException {
        return param(url).add("p=" + projectName).add("a=commitdiff").add("h=" + path.getChangeSet().getId()) + "#" + URLEncoder.encode(path.getPath(), "UTF-8").toString();
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + param(url).add("p=" + projectName).add("a=commit").add("h=" + changeSet.getId()).toString());
    }

    private QueryBuilder param(URL url) {
        return new QueryBuilder(url.getQuery());
    }

    public String getProjectName() {
        return projectName;
    }

    @Extension
    public static class ViewGitWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @Nonnull
        public String getDisplayName() {
            return "viewgit";
        }

        @Override
        public ViewGitWeb newInstance(StaplerRequest req, @Nonnull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            return req.bindJSON(ViewGitWeb.class, jsonObject);
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
                            if (findText(open(new URL(v)), "ViewGit")) {
                                return FormValidation.ok();
                            } else {
                                return FormValidation.error("This is a valid URL but it doesn't look like ViewGit");
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

        private boolean checkURIFormat(String url) throws URISyntaxException {
            URI uri = new URI(url);
            String[] schemes = {"http", "https"};
            UrlValidator urlValidator = new UrlValidator(schemes);
            return urlValidator.isValid(uri.toString()) && uri.getHost().contains("viewgit");
        }
    }
}
