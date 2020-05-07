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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.URL;

/**
 * Git Browser for Gitorious
 */
public class GitoriousWeb extends GitRepositoryBrowser {

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
        public GitoriousWeb newInstance(StaplerRequest req, @NonNull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            return req.bindJSON(GitoriousWeb.class, jsonObject);
        }
    }

}
