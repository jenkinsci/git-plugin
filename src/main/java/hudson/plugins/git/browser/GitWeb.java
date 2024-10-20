package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.browsers.QueryBuilder;
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
 * Git Browser URLs
 */
public class GitWeb extends GitRepositoryBrowser {

    @Serial
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public GitWeb(String repoUrl) {
        super(repoUrl);
    }

    @Override
    protected boolean getNormalizeUrl() {
		return false;
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();

        return new URL(url, url.getPath()+ param(url).add("a=commit").add("h=" + changeSet.getId()));
    }

    private QueryBuilder param(URL url) {
        return new QueryBuilder(url.getQuery());
    }

    /**
     * Creates a link to the file diff.
     * http://[GitWeb URL]?a=blobdiff;f=[path];fp=[path];h=[dst];hp=[src];hb=[commit];hpb=[parent commit]
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
        GitChangeSet changeSet = path.getChangeSet();
        URL url = getUrl();
        String spec = param(url).add("a=blobdiff").add("f=" + path.getPath()).add("fp=" + path.getPath())
            .add("h=" + path.getSrc()).add("hp=" + path.getDst())
            .add("hb=" + changeSet.getId()).add("hpb=" + changeSet.getParentCommit()).toString();
        return new URL(url, url.getPath()+spec);
    }

    /**
     * Creates a link to the file.
     * http://[GitWeb URL]?a=blob;f=[path];h=[dst, or src for deleted files];hb=[commit]
     * @param path file
     * @return file link
     * @throws IOException on input or output error
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        URL url = getUrl();
        String h = (path.getDst() != null) ? path.getDst() : path.getSrc();
        String spec = param(url).add("a=blob").add("f=" + path.getPath())
            .add("h=" + h).add("hb=" + path.getChangeSet().getId()).toString();
        return encodeURL(new URL(url, url.getPath()+spec));
    }

    @Extension
    @Symbol("gitWeb")
    public static class GitWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        public String getDisplayName() {
            return "gitweb";
        }

        @Override
        @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
                            justification = "Inherited javadoc commits that req is non-null")
        public GitWeb newInstance(StaplerRequest2 req, @NonNull JSONObject jsonObject) throws FormException {
            return req.bindJSON(GitWeb.class, jsonObject);
        }
    }

}
