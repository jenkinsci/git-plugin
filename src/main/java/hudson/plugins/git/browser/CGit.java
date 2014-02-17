package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.browsers.QueryBuilder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Git Browser URLs
 */
public class CGit extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public CGit(String url) {
        super(url);
    }

    private QueryBuilder param(URL url) {
        return new QueryBuilder(url.getQuery());
    }

    /**
     * Creates a link to the change set
     * http://[CGit URL]/commit?id=[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commit/" + param(url).add("id=" + changeSet.getId()).toString());
    }

    /**
     * Creates a link to the file diff.
     * http://[CGit URL]/diff/[path]?id=[commit]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();
        URL url = getUrl();
        return new URL(url, url.getPath() + "diff/" + path.getPath() + param(url).add("id=" + changeSet.getId()).toString());
    }

    /**
     * Creates a link to the file.
     * http://[CGit URL]/tree/[path]?id=[commit]
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
            return new URL(url, url.getPath() + "tree/" + path.getPath() + param(url).add("id=" + changeSet.getParentCommit()).toString());
        } else {
            return new URL(url, url.getPath() + "tree/" + path.getPath() + param(url).add("id=" + changeSet.getId()).toString());
        }
    }

    @Extension
    public static class CGITDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "cgit";
        }

        @Override
        public CGit newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindJSON(CGit.class, jsonObject);
        }
    }
}