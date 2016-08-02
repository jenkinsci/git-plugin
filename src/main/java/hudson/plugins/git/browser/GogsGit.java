package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.URL;

/**
 * @author Norbert Lange (nolange79@gmail.com)
 */
public class GogsGit extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public GogsGit(String repoUrl) {
        super(repoUrl);
    }

    /**
     * Creates a link to the change set
     * http://[GogsGit URL]/commit/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commit/" + changeSet.getId().toString());
    }

    /**
     * Creates a link to the file diff.
     * http://[GogsGit URL]/commit/[commit]#diff-N
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
     * @return url for differences
     * @throws IOException
     */
    private URL getDiffLinkRegardlessOfEditType(Path path) throws IOException {
        // Gogs diff indices begin at 1.
        return new URL(getChangeSetLink(path.getChangeSet()), "#diff-" + String.valueOf(getIndexOfPath(path) + 1));
    }

    /**
     * Creates a link to the file.
     * http://[GogsGit URL]/src/[commit]/[path]
     * Deleted Files link to the parent version. No easy way to find it
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
            URL url = getUrl();
            return new URL(url, url.getPath() + "src/" + path.getChangeSet().getId().toString() + "/" + path.getPath());
        }
    }

    @Extension
    public static class GogsGitDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "gogs";
        }

        @Override
        public GogsGit newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindJSON(GogsGit.class, jsonObject);
        }
    }
}
