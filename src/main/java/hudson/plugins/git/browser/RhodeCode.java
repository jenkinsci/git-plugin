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
     * http://[RhodeCode URL]/changeset/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(url, url.getPath() + "changeset/" + changeSet.getId());
    }

    /**
     * Creates a link to the file diff.
     * http://[RhodeCode URL]/diff/[path]?diff2=[commit]&diff1=[commit]&diff=diff+to+revision
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();

        if (path.getEditType() == EditType.DELETE) {
	    return new URL(url, url.getPath() + "diff/" + path.getPath() + param().add("diff2=" + changeSet.getParentCommit()).add("diff1=" + changeSet.getId()).toString() + "&diff=diff+to+revision");
        } else {
            return new URL(url, url.getPath() + "diff/" + path.getPath() + param().add("diff2=" + changeSet.getId()).add("diff1=" + changeSet.getId()).toString()  + "&diff=diff+to+revision");
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
            return req.bindJSON(RhodeCode.class, jsonObject);
        }
    }
}
