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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URI;
import java.net.IDN;

/**
 * Git Browser URLs
 */
public class GithubWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public GithubWeb(String repoUrl) {
        super(repoUrl);
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath()+"commit/" + changeSet.getId());
    }

    /**
     * Creates a link to the file diff.
     * http://[GitHib URL]/commit/573670a3bb1f3b939e87f1dee3e99b6bfe281fcb#diff-N
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
    	// Github seems to sort the output alphabetically by the path.
        return new URL(getChangeSetLink(path.getChangeSet()), "#diff-" + String.valueOf(getIndexOfPath(path)));
    }

    /**
     * Creates a link to the file.
     * http://[GitHib URL]/blob/573670a3bb1f3b939e87f1dee3e99b6bfe281fcb/src/main/java/hudson/plugins/git/browser/GithubWeb.java
     *  Github seems to have no URL for deleted files, so just return
     * a difflink instead.
     *
     * @param path file
     * @return file link
     * @throws IOException on input or output error
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType().equals(EditType.DELETE)) {
            return getDiffLinkRegardlessOfEditType(path);
        } else {
            final String spec = "blob/" + path.getChangeSet().getId() + "/" + path.getPath();
            URL url = buildURL(spec);
            URI uri;
            try {
                uri = new URI(url.getProtocol(), url.getUserInfo(), IDN.toASCII(url.getHost()), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
            return uri.toURL();
        }
    }

    private URL buildURL(String spec) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + spec);
    }

    @Extension
    public static class GithubWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @Nonnull
        public String getDisplayName() {
            return "githubweb";
        }

        @Override
		public GithubWeb newInstance(StaplerRequest req, @Nonnull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
			return req.bindJSON(GithubWeb.class, jsonObject);
		}
	}

}
