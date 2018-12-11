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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author Chris Klaiber (cklaiber@gmail.com)
 */
public class KilnGit extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public KilnGit(String repoUrl) {
        super(repoUrl);
    }

    private QueryBuilder param(URL url) {
        return new QueryBuilder(url.getQuery());
    }

    /**
     * Creates a link to the change set
     * http://[KilnGit URL]/History/[commit]
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException on input or output error
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return encodeURL(new URL(url, url.getPath() + "History/" + changeSet.getId() + param(url).toString()));
    }

    /**
     * Creates a link to the file diff.
     * http://[KilnGit URL]/History/[commit]#diff-N
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
        final GitChangeSet changeSet = path.getChangeSet();
        final int i = getIndexOfPath(path);
        if (i >= 0) {
            // Kiln diff indices begin at 1.
            URL url = getUrl();
            return new URL(getChangeSetLink(changeSet), param(url).toString() + "#diff-" + String.valueOf(i + 1));
        }
        return getChangeSetLink(changeSet);
    }

    /**
     * Creates a link to the file.
     * http://[KilnGit URL]/FileHistory/[path]?rev=[commit]
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
            GitChangeSet changeSet = path.getChangeSet();
            URL url = getUrl();
            return encodeURL(new URL(url, url.getPath() + "FileHistory/" + path.getPath() + param(url).add("rev=" + changeSet.getId()).toString()));
        }
    }

    @Extension
    public static class KilnGitDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @Nonnull
        public String getDisplayName() {
            return "Kiln";
        }

        @Override
        public KilnGit newInstance(StaplerRequest req, @Nonnull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            return req.bindJSON(KilnGit.class, jsonObject);
        }
    }
}
