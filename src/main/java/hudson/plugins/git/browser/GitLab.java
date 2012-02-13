package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Git Browser for GitLab
 */
public class GitLab extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;
    private final URL url;

    @DataBoundConstructor
    public GitLab(String url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
    }

    public URL getUrl() {
        return url;
    }


    /**
     * Creates a link to the changeset
     *
     * https://[GitLab URL]/commits/a9182a07750c9a0dfd89a8461adf72ef5ef0885b
     *
     * @param path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(url, "commits/" + changeSet.getId().toString());
    }

    /**
     * Creates a link to the commit diff.
     * 
     * https://[GitLab URL]/commits/a9182a07750c9a0dfd89a8461adf72ef5ef0885b#[path to file]
     * 
     * @param path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        return new URL(url, "commits/" + changeSet.getId().toString() + "#" + path.getPath());
    }

    /**
     * Creates a link to the file.
     * https://[GitLab URL]/a9182a07750c9a0dfd89a8461adf72ef5ef0885b/tree/pom.xml
     * 
     * @param path
     * @return file link
     * @throws IOException
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType().equals(EditType.DELETE)) {
            return getDiffLink(path);
        } else {
            final String spec = "/" + path.getChangeSet().getId() + "/tree/" + path.getPath();
            return new URL(url, url.getPath() + spec);
        }
    }

    @Extension
    public static class GitLabDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "gitlab";
        }

        @Override
        public GitLab newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindParameters(GitLab.class, "Gitlab.");
        }
    }

}
