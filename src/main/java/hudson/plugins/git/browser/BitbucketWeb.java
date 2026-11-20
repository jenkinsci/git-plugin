package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.Util;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.Serial;
import java.net.URL;

/**
 * Git Browser URLs
 */
public class BitbucketWeb extends GitRepositoryBrowser {

    @Serial
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public BitbucketWeb(String repoUrl) {
        super(repoUrl);
    }

    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "commits/" + changeSet.getId());
    }

    /**
     * Creates a link to the file diff.
     * http://[BitbucketWeb URL]/commits/[commitid]
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
        return new URL(getChangeSetLink(changeSet), "#chg-" + Util.rawEncode(pathAsString));
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
        return encodeURL(new URL(url, url.getPath() + "history/" + Util.rawEncode(pathAsString)));
    }

    @Extension
    @Symbol("bitbucket")
    public static class BitbucketWebDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        public String getDisplayName() {
            return "bitbucketweb";
        }

        @Override
        public BitbucketWeb newInstance(@NonNull StaplerRequest2 req, @NonNull JSONObject jsonObject) throws FormException {
            return req.bindJSON(BitbucketWeb.class, jsonObject);
        }
    }

}
