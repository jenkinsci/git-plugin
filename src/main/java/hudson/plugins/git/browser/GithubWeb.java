package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.plugins.git.GitRepositoryBrowser;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Collection;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Git Browser URLs
 */
public class GithubWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;
    private final URL url;

    @DataBoundConstructor
    public GithubWeb(String url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
    }

    public URL getUrl() {
        return url;
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(url, url.getPath()+"commit/" + changeSet.getId().toString());
    }

    /**
     * Creates a link to the file diff.
     * http://[GitWeb URL]?a=blobdiff;f=[path];fp=[path];h=[dst];hp=[src];hb=[commit];hpb=[parent commit]
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
        final GitChangeSet changeSet = path.getChangeSet();
        final Collection<String> affectedPaths = changeSet.getAffectedPaths();
        int i = 0;
        final String pathAsString = path.getPath();
        for (String affectedPath : affectedPaths) {
            if (affectedPath.equals(pathAsString)) {
                return new URL(url, url.getPath()+"commit/" + changeSet.getId().toString() + "#diff-" + String.valueOf(i));
            } else {
                i++;
            }
        }
        return null;
    }

    /**
     * Creates a link to the file.
     * http://[GitHib URL]/blob/573670a3bb1f3b939e87f1dee3e99b6bfe281fcb/src/main/java/hudson/plugins/git/browser/GithubWeb.java
     *
     * @todo Do not know how to handle deleted files.
     * @param path file
     * @return file link
     * @throws IOException
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        final String spec = "blob/" + path.getChangeSet().getId() + "/" + path.getPath();
        return new URL(url, url.getPath()+spec);
    }

    @Extension
    public static class GithubWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "githubweb";
        }

        @Override
		public GithubWeb newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
			return req.bindParameters(GithubWeb.class, "githubweb.");
		}
	}

}
