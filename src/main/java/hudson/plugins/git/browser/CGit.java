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
    private final URL url;

    @DataBoundConstructor
    public CGit(String url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
    }

    public URL getUrl() {
        return url;
    }

    private QueryBuilder param() {
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
        return new URL(url, url.getPath() + "commit/" + param().add("id=" + changeSet.getId()).toString());
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
        return new URL(url, url.getPath() + "diff/" + path.getPath() + param().add("id=" + changeSet.getId()).toString());
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

        if (path.getEditType() == EditType.DELETE) {
            return new URL(url, url.getPath() + "tree/" + path.getPath() + param().add("id=" + changeSet.getParentCommit()).toString());
        } else {
            return new URL(url, url.getPath() + "tree/" + path.getPath() + param().add("id=" + changeSet.getId()).toString());
        }
    }

    @Extension
    public static class CGITDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "cgit";
        }

        @Override
        public CGit newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindParameters(CGit.class, "cgit.");
        }
    }
}