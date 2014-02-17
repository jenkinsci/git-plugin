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
 * Stash Browser URLs
 */
public class Stash extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public Stash(String url) {
        super(url);
    }

    private QueryBuilder param(URL url) {
        return new QueryBuilder(url.getQuery());
    }

    /**
     * Creates a link to the change set
     * http://[Stash URL]/commits/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commits/" + changeSet.getId());
    }

    /**
     * Creates a link to the file diff.
     * http://[Stash URL]/diff/[path]?at=[commit]&until=[commit]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();
        URL url = getUrl();

        if (path.getEditType() == EditType.DELETE) {
            return new URL(url, url.getPath() + "diff/" + path.getPath() + param(url).add("at=" + changeSet.getParentCommit()).add("until=" + changeSet.getId()).toString());
        } else {
            return new URL(url, url.getPath() + "diff/" + path.getPath() + param(url).add("at=" + changeSet.getId()).add("until=" + changeSet.getId()).toString());
        }
    }

    /**
     * Creates a link to the file.
     * http://[Stash URL]/browse/[path]?at=[commit]
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
            return new URL(url, url.getPath() + "browse/" + path.getPath() + param(url).add("at=" + changeSet.getParentCommit()).toString());
        } else {
            return new URL(url, url.getPath() + "browse/" + path.getPath() + param(url).add("at=" + changeSet.getId()).toString());
        }
    }

    @Extension
    public static class StashDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "stash";
        }

        @Override
        public Stash newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindJSON(Stash.class, jsonObject);
        }
    }
}
