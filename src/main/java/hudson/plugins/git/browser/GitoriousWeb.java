package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.Serial;
import java.net.URL;

/**
 * Git Browser for Gitorious
 */
public class GitoriousWeb extends GitRepositoryBrowser {

    @Serial
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public GitoriousWeb(String repoUrl) {
        super(repoUrl);
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(getUrl(), "commit/" + changeSet.getId());
    }

    /**
     * Creates a link to the commit diff.
     * 
     * {@code https://[Gitorious URL]/commit/a9182a07750c9a0dfd89a8461adf72ef5ef0885b/diffs?diffmode=sidebyside&fragment=1#[path to file]}
     * 
     * @param path file path used in diff link
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        return encodeURL(new URL(getUrl(), "commit/" + changeSet.getId() + "/diffs?diffmode=sidebyside&fragment=1#" + path.getPath()));
    }

    /**
     * Creates a link to the file.
     * {@code https://[Gitorious URL]/blobs/a9182a07750c9a0dfd89a8461adf72ef5ef0885b/pom.xml}
     * 
     * @param path file path used in diff link
     * @return file link
     * @throws IOException on input or output error
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType().equals(EditType.DELETE)) {
            return getDiffLink(path);
        } else {
            final String spec = "blobs/" + path.getChangeSet().getId() + "/" + path.getPath();
            URL url = getUrl();
            return new URL(url, url.getPath() + spec);
        }
    }

    @Extension
    public static class GitoriousWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        public String getDisplayName() {
            return "gitoriousweb";
        }

        @Override
        @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
                            justification = "Inherited javadoc commits that req is non-null")
        public GitoriousWeb newInstance(StaplerRequest2 req, @NonNull JSONObject jsonObject) throws FormException {
            return req.bindJSON(GitoriousWeb.class, jsonObject);
        }
    }

}
