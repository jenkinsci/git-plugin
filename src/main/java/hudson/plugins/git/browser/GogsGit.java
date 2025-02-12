package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import net.sf.json.JSONObject;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.Serial;
import java.net.URL;

/**
 * @author Norbert Lange (nolange79@gmail.com)
 */
public class GogsGit extends GitRepositoryBrowser {

    @Serial
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
     * @throws IOException on input or output error
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commit/" + changeSet.getId());
    }

    /**
     * Creates a link to the file diff.
     * http://[GogsGit URL]/commit/[commit]#diff-N
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException on input or output error
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
     * @param path file path used in diff link
     * @return url for differences
     * @throws IOException on input or output error
     */
    private URL getDiffLinkRegardlessOfEditType(Path path) throws IOException {
        // Gogs diff indices begin at 1.
        return encodeURL(new URL(getChangeSetLink(path.getChangeSet()), "#diff-" + (getIndexOfPath(path) + 1)));
    }

    /**
     * Creates a link to the file.
     * http://[GogsGit URL]/src/[commit]/[path]
     * Deleted Files link to the parent version. No easy way to find it
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType().equals(EditType.DELETE)) {
            return getDiffLinkRegardlessOfEditType(path);
        } else {
            URL url = getUrl();
            return encodeURL(new URL(url, url.getPath() + "src/" + path.getChangeSet().getId() + "/" + path.getPath()));
        }
    }

    @Extension
    @Symbol("gogs")
    public static class GogsGitDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        public String getDisplayName() {
            return "gogs";
        }

        @Override
        @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
                            justification = "Inherited javadoc commits that req is non-null")
        public GogsGit newInstance(StaplerRequest2 req, @NonNull JSONObject jsonObject) throws FormException {
            return req.bindJSON(GogsGit.class, jsonObject);
        }
    }
}
