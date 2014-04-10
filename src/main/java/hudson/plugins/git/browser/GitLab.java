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
     * https://[GitLab URL]/commits/a9182a07750c9a0dfd89a8461adf72ef5ef0885b
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
     * https://[GitLab URL]/commits/a9182a07750c9a0dfd89a8461adf72ef5ef0885b#[path to file]
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
     * https://[GitLab URL]/a9182a07750c9a0dfd89a8461adf72ef5ef0885b/tree/pom.xml
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
            String spec;
            if(getVersion() >= 5.1) {
                spec = "blob/" + path.getChangeSet().getId() + "/" + path.getPath();
            } else {
                spec = path.getChangeSet().getId() + "/tree/" + path.getPath();
            }
            URL url = getUrl();
            return new URL(url, url.getPath() + spec);
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
        if(getVersion() >= 3){
            return "commit/";
        }

        return "commits/";
    }

}
