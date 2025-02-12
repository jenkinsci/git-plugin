package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
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
 * Git Browser URLs for on-premise Bitbucket Server installation.
 */
public class BitbucketServer extends GitRepositoryBrowser {

    @Serial
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public BitbucketServer(String repoUrl) {
        super(repoUrl);
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commits/" + changeSet.getId());
    }

    /**
     * Creates a link to the file diff.
     * http://[BitbucketServer URL]/commits/[commitid]
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getDiffLink(GitChangeSet.Path path) throws IOException {
        if (path.getEditType() != EditType.EDIT || path.getSrc() == null || path.getDst() == null
                || path.getChangeSet().getParentCommit() == null) {
            return null;
        }
        return getDiffLinkRegardlessOfEditType(path);
    }


    private URL getDiffLinkRegardlessOfEditType(GitChangeSet.Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        final String pathAsString = path.getPath();
        return new URL(getChangeSetLink(changeSet), "#" + pathAsString);
    }

    /**
     * Creates a link to the file.
     *
     * @param path file
     * @return file link
     * @throws IOException on input or output error
     */
    @Override
    public URL getFileLink(GitChangeSet.Path path) throws IOException {
        final String pathAsString = path.getPath();
        URL url = getUrl();
        return encodeURL(new URL(url, url.getPath() + "browse/" + pathAsString));
    }

    @Extension @Symbol("bitbucketServer")
    public static class BitbucketServerDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        public String getDisplayName() {
            return "bitbucketserver";
        }

        @Override
        @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
                            justification = "Inherited javadoc commits that req is non-null")
        public BitbucketServer newInstance(StaplerRequest2 req, @NonNull JSONObject jsonObject) throws FormException {
            return req.bindJSON(BitbucketServer.class, jsonObject);
        }
    }
}
