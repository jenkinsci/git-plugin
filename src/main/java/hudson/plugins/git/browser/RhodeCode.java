package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.browsers.QueryBuilder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * RhodeCode Browser URLs
 */
public class RhodeCode extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;
    private final URL url;

    @DataBoundConstructor
    public RhodeCode(String url) throws MalformedURLException {
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
     * http://[RhodeCode URL]/files/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(url, url.getPath() + "files/" + changeSet.getId() + "/");
    }

    /**
     * Creates a link to the file diff.
     * http://[RhodeCode URL]/diff/[path]?at=[commit]&until=[commit]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();

        if (path.getEditType() == EditType.DELETE) {
            return new URL(url, url.getPath() + "diff/" + path.getPath() + param().add("at=" + changeSet.getParentCommit()).add("until=" + changeSet.getId()).toString());
        } else {
            return new URL(url, url.getPath() + "diff/" + path.getPath() + param().add("at=" + changeSet.getId()).add("until=" + changeSet.getId()).toString());
        }
    }

    /**
     * Creates a link to the file.
     * http://[RhodeCode URL]/files/[commit]/[path]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();

        if (path.getEditType() == EditType.DELETE) {
            return new URL(url, url.getPath() + "files/" + changeSet.getParentCommit().toString() + '/' + path.getPath());
        } else {
            return new URL(url, url.getPath() + "files/" + changeSet.getId().toString() + '/' + path.getPath());
        }
    }

    @Extension
    public static class RhodeCodeDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "rhodecode";
        }

        @Override
        public RhodeCode newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindParameters(RhodeCode.class, "rhodecode.");
        }
    }
}
