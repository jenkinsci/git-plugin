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
public class AssemblaWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public AssemblaWeb(String repoUrl) {
        super(repoUrl);
    }

    private QueryBuilder param(URL url) {
        return new QueryBuilder(url.getQuery());
    }

    /**
     * Creates a link to the change set
     * http://[AssemblaWeb URL]/commits/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commit/" + changeSet.getId());
    }

    /**
     * Shows the difference between the referenced commit and the previous commit.
     * The changes section also display diffs, so a seperate url is unncessary.
     * http://[Assembla URL]/commits/[commit]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();
        return getChangeSetLink(changeSet);
    }

    /**
     * Creates a link to the file.
     * http://[Assembla URL]/nodes/[commit]/[path]
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
            return new URL(url, url.getPath() + "nodes/" + changeSet.getParentCommit() + path.getPath());
        } else {
            return new URL(url, url.getPath() + "nodes/" + changeSet.getId() + path.getPath());
        }
    }

    @Extension
    public static class ASSEMBLAWEBDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "AssemblaWeb";
        }

        @Override
        public CGit newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindJSON(CGit.class, jsonObject);
        }
    }
}
