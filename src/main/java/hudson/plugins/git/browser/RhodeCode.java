package hudson.plugins.git.browser;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.browsers.QueryBuilder;
import java.io.IOException;
import java.net.URL;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * RhodeCode Browser URLs
 */
public class RhodeCode extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public RhodeCode(String repoUrl) {
        super(repoUrl);
    }

    private QueryBuilder param(URL url) {
        return new QueryBuilder(url.getQuery());
    }

    /**
     * Creates a link to the change set
     * {@code http://[RhodeCode URL]/changeset/[commit]}
     *
     * @param changeSet commit hash
     * @return change set link
     * @throws IOException on input or output error
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        URL url = getUrl();
        return new URL(url, url.getPath() + "changeset/" + changeSet.getId());
    }

    /**
     * Creates a link to the file diff.
     * {@code http://[RhodeCode URL]/diff/[path]?diff2=[commit]&diff1=[commit]&diff=diff+to+revision}
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();
        URL url = getUrl();
        return new URL(
                url,
                url.getPath() + "diff/" + path.getPath()
                        + param(url).add("diff2=" + changeSet.getParentCommit()).add("diff1=" + changeSet.getId())
                        + "&diff=diff+to+revision");
    }

    /**
     * Creates a link to the file.
     * {@code http://[RhodeCode URL]/files/[commit]/[path]}
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        GitChangeSet changeSet = path.getChangeSet();
        URL url = getUrl();

        if (path.getEditType() == EditType.DELETE) {
            String parentCommit = changeSet.getParentCommit();
            if (parentCommit == null) {
                parentCommit = ".";
            }
            return encodeURL(new URL(url, url.getPath() + "files/" + parentCommit + '/' + path.getPath()));
        } else {
            return encodeURL(new URL(url, url.getPath() + "files/" + changeSet.getId() + '/' + path.getPath()));
        }
    }

    @Extension
    @Symbol("rhodeCode")
    public static class RhodeCodeDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @Override
        @NonNull
        public String getDisplayName() {
            return "rhodecode";
        }

        @Override
        public RhodeCode newInstance(StaplerRequest req, @NonNull JSONObject jsonObject) throws FormException {
            assert req != null; // see inherited javadoc
            return req.bindJSON(RhodeCode.class, jsonObject);
        }
    }
}
