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
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
        String spec = param(url).add("p=" + projectName).add("a=viewblob").add("h=" + path.getDst()).add("f=" +  path.getPath()).toString();
        return encodeURL(new URL(url, url.getPath() + spec));
    }

	private String buildCommitDiffSpec(URL url, Path path)
			throws UnsupportedEncodingException {
        return param(url).add("p=" + projectName).add("a=commitdiff").add("h=" + path.getChangeSet().getId()) + "#" +  URLEncoder.encode(path.getPath(),"UTF-8").toString();
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
        @NonNull
        public String getDisplayName() {
            return "viewgit";
        }

        @Override
        public ViewGitWeb newInstance(StaplerRequest req, @NonNull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            return req.bindJSON(ViewGitWeb.class, jsonObject);
        }

        @RequirePOST
        public FormValidation doCheckRepoUrl(@AncestorInPath Item project, @QueryParameter(fixEmpty = true) final String repoUrl)
                throws IOException, ServletException, URISyntaxException {

            String cleanUrl = Util.fixEmptyAndTrim(repoUrl);
            // Connect to URL and check content only if we have admin permission
            if (initialChecksAndReturnOk(project, cleanUrl))
                return FormValidation.ok();
            if (!checkURIFormat(cleanUrl)) {
                return FormValidation.error(Messages.invalidUrl());
            }
            return new URLCheck() {
                protected FormValidation check() throws IOException, ServletException {
                    String v = cleanUrl;
                    if (!v.endsWith("/"))
                        v += '/';

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
        }
    }
}
