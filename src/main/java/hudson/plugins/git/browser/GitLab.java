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
 * Git Browser for GitLab
 */
public class GitLab extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    private final double version;

    @DataBoundConstructor
    public GitLab(String repoUrl, String version) {
        super(repoUrl);
        this.version = Double.valueOf(version);
    }

    public double getVersion() {
        return version;
    }

    /**
     * Creates a link to the changeset
     *
     * v &lt; 3.0: [GitLab URL]/commits/[Hash]
     * else:       [GitLab URL]/commit/[Hash]
     *
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        String  commitPrefix;

        return new URL(getUrl(), calculatePrefix() + changeSet.getId().toString());
    }

    /**
     * Creates a link to the commit diff.
     *
     * v &lt; 3.0: [GitLab URL]/commits/[Hash]#[File path]
     * else:       [GitLab URL]/commit/[Hash]#[File path]
     *
     * @param path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        return new URL(getUrl(), calculatePrefix() + changeSet.getId().toString() + "#" + path.getPath());
    }

    /**
     * Creates a link to the file.
     * v &lt; 5.1: [GitLab URL][Hash]/tree/[File path]
     * else:       [GitLab URL]blob/[Hash]/[File path]
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
            if(getVersion() < 5.1) {
                return new URL(getUrl(), path.getChangeSet().getId() + "/tree/" + path.getPath());
            } else {
                return new URL(getUrl(), "blob/" + path.getChangeSet().getId() + "/" + path.getPath());
            }
        }
    }

    @Extension
    public static class GitLabDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "gitlab";
        }

        @Override
        public GitLab newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
            return req.bindJSON(GitLab.class, jsonObject);
        }
    }

    private String calculatePrefix() {
        if(getVersion() < 3) {
            return "commits/";
        } else {
            return "commit/";
        }
    } 

}
