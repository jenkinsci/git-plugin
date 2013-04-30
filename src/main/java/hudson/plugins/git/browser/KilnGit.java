package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.scm.browsers.QueryBuilder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author Chris Klaiber (cklaiber@gmail.com)
 */
public class KilnGit extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;
    private final URL url;

    @DataBoundConstructor
    public KilnGit(String url) throws MalformedURLException {
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
     * http://[KilnGit URL]/History/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(url, url.getPath() + "History/" + changeSet.getId() + param().toString());
    }

    /**
     * Creates a link to the file diff.
     * http://[KilnGit URL]/History/[commit]#diff-N
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        if (path.getEditType() != EditType.EDIT || path.getSrc() == null || path.getDst() == null
                || path.getChangeSet().getParentCommit() == null) {
            return null;
        }
        return getDiffLinkRegardlessOfEditType(path);
    }

    /**
     * Return a diff link regardless of the edit type by appending the index of the pathname in the changeset.
     *
     * @param path
     * @return
     * @throws IOException
     */
    private URL getDiffLinkRegardlessOfEditType(Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        final ArrayList<String> affectedPaths = new ArrayList<String>(changeSet.getAffectedPaths());
        // Kiln seems to sort the output alphabetically by the path.
        Collections.sort(affectedPaths);
        final String pathAsString = path.getPath();
        final int i = Collections.binarySearch(affectedPaths, pathAsString);
        if (i >= 0) {
            // Kiln diff indices begin at 1.
            return new URL(getChangeSetLink(changeSet), param().toString() + "#diff-" + String.valueOf(i + 1));
        }
        return getChangeSetLink(changeSet);
    }

    /**
     * Creates a link to the file.
     * http://[KilnGit URL]/FileHistory/[path]?rev=[commit]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType().equals(EditType.DELETE)) {
            return getDiffLinkRegardlessOfEditType(path);
        } else {
            GitChangeSet changeSet = path.getChangeSet();
            return new URL(url, url.getPath() + "FileHistory/" + path.getPath() + param().add("rev=" + changeSet.getId()).toString());
        }
    }

    @Extension
    public static class KilnGitDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "Kiln";
        }

        @Override
        public KilnGit newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindParameters(KilnGit.class, "kilngit.");
        }
    }
}
