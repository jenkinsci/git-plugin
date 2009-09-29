package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.plugins.git.GitRepositoryBrowser;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;

import hudson.scm.browsers.QueryBuilder;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Git Browser URLs
 */
public class GitWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;
    private final URL url;

    @DataBoundConstructor
    public GitWeb(String url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(new URL(url));
    }

    public URL getUrl() {
        return url;
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(url, url.getPath()+param().add("a=commit").add("h=" + changeSet.getId()).toString());
    }

    private QueryBuilder param() {
        return new QueryBuilder(url.getQuery());
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
        GitChangeSet changeSet = path.getChangeSet();
        String spec = param().add("a=blobdiff").add("f=" + path.getPath()).add("fp=" + path.getPath())
                             .add("h=" + path.getSrc()).add("hp=" + path.getDst())
                             .add("hb=" + changeSet.getId()).add("hpb=" + changeSet.getParentCommit()).toString();
        return new URL(url, url.getPath()+spec);
    }

    /**
     * Creates a link to the file.
     * http://[GitWeb URL]?a=blob;f=[path];h=[dst, or src for deleted files];hb=[commit]
     * @param path file
     * @return file link
     * @throws IOException
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        String h = (path.getDst() != null) ? path.getDst() : path.getSrc();
        String spec = param().add("a=blob").add("f=" + path.getPath())
                             .add("h=" + h).add("hb=" + path.getChangeSet().getId()).toString();
        return new URL(url, url.getPath()+spec);
    }

    @Extension
    public static class GitWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "gitweb";
        }

        @Override
		public GitWeb newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
			return req.bindParameters(GitWeb.class, "gitweb.");
		}
	}

}
