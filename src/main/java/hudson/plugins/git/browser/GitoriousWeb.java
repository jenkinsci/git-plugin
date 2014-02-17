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
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Git Browser for Gitorious
 */
public class GitoriousWeb extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public GitoriousWeb(String url) {
        super(url);
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(getUrl(), "commit/" + changeSet.getId().toString());
    }

    /**
     * Creates a link to the commit diff.
     * 
     * https://[Gitorious URL]/commit/a9182a07750c9a0dfd89a8461adf72ef5ef0885b/diffs?diffmode=sidebyside&fragment=1#[path to file]
     * 
     * @param path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        return new URL(getUrl(), "commit/" + changeSet.getId().toString() + "/diffs?diffmode=sidebyside&fragment=1#" + path.getPath());
    }

    /**
     * Creates a link to the file.
     * https://[Gitorious URL]/blobs/a9182a07750c9a0dfd89a8461adf72ef5ef0885b/pom.xml
     * 
     * @param path
     * @return file link
     * @throws IOException
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
        public String getDisplayName() {
            return "gitoriousweb";
        }

        @Override
        public GitoriousWeb newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindJSON(GitoriousWeb.class, jsonObject);
        }
    }

}
