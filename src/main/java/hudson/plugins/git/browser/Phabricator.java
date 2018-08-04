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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Git Browser for Phabricator
 */
public class Phabricator extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    private final String repo;

    @DataBoundConstructor
    public Phabricator(String repoUrl, String repo) {
        super(repoUrl);
        this.repo = repo;
    }

    public String getRepo() {
        return repo;
    }

    /**
     * Creates a link to the changeset
     *
     * https://[Phabricator URL]/r$repo$sha
     *
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
        return new URL(getUrl(), String.format("/r%s%s", this.getRepo(), changeSet.getId()));
    }

    /**
     * Creates a link to the commit diff.
     *
     * https://[Phabricator URL]/commits/a9182a07750c9a0dfd89a8461adf72ef5ef0885b#[path to file]
     *
     *
     * @param path file path used in diff link
     * @return diff link
     * @throws IOException on input or output error
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        final String sha = changeSet.getId();
        final String spec = String.format("/diffusion/%s/change/master/%s;%s", this.getRepo(), path.getPath(), sha);
        return new URL(getUrl(), spec);
    }

    /**
     * Creates a link to the file.
     * https://[Phabricator URL]/a9182a07750c9a0dfd89a8461adf72ef5ef0885b/tree/pom.xml
     *
     * @param path file path used in diff link
     * @return file link
     * @throws IOException on input or output error
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        final String sha = changeSet.getId();
        final String spec = String.format("/diffusion/%s/history/master/%s;%s", this.getRepo(), path.getPath(), sha);
        return new URL(getUrl(), spec);
    }

    @Extension
    public static class PhabricatorDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @Nonnull
        public String getDisplayName() {
            return "phabricator";
        }

        @Override
        public Phabricator newInstance(StaplerRequest req, @Nonnull JSONObject jsonObject) throws FormException {
            assert req != null; //see inherited javadoc
            return req.bindJSON(Phabricator.class, jsonObject);
        }
    }
}
