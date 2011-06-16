package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.browsers.QueryBuilder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class ViewGitWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;
    private final URL url;
    private final String projectName;

    @DataBoundConstructor
    public ViewGitWeb(String url, String projectName) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
        this.projectName = projectName;
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        if (path.getEditType() == EditType.EDIT) {
        	String spec = buildCommitDiffSpec(path);
        	return new URL(url, url.getPath() + spec);            
        }
        return null;
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType() == EditType.DELETE) {
            String spec = buildCommitDiffSpec(path);
            return new URL(url, url.getPath() + spec);            
        }
        String spec = param().add("p=" + projectName).add("a=viewblob").add("h=" + path.getSrc()).add("f=" +  path.getPath()).toString();
        return new URL(url, url.getPath() + spec);
    }

	private String buildCommitDiffSpec(Path path)
			throws UnsupportedEncodingException {
		return param().add("p=" + projectName).add("a=commitdiff").add("h=" + path.getChangeSet().getId()).toString() + "#" +  URLEncoder.encode(path.getPath(),"UTF-8").toString();
	}

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(url, url.getPath() + param().add("p=" + projectName).add("a=commit").add("h=" + changeSet.getId()).toString());
    }

    private QueryBuilder param() {
        return new QueryBuilder(url.getQuery());
    }

    public URL getUrl() {
        return url;
    }

    public String getProjectName() {
        return projectName;
    }

    @Extension
    public static class ViewGitWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "viewgit";
        }

        @Override
        public ViewGitWeb newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindParameters(ViewGitWeb.class, "viewgit.");
        }
    }

}
