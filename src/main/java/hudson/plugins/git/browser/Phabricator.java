package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
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
 * Git Browser for Phabricator
 */
public class Phabricator extends GitRepositoryBrowser {

    @Serial
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
        return new URL(getUrl(), "/r%s%s".formatted(this.getRepo(), changeSet.getId()));
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
        final String spec = "/diffusion/%s/change/master/%s;%s".formatted(this.getRepo(), path.getPath(), sha);
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
        final String spec = "/diffusion/%s/history/master/%s;%s".formatted(this.getRepo(), path.getPath(), sha);
        return encodeURL(new URL(getUrl(), spec));
    }

    @Extension
    @Symbol("phabricator")
    public static class PhabricatorDescriptor extends Descriptor<RepositoryBrowser<?>> {
        @NonNull
        public String getDisplayName() {
            return "phabricator";
        }

        @Override
        @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
                            justification = "Inherited javadoc commits that req is non-null")
        public Phabricator newInstance(StaplerRequest2 req, @NonNull JSONObject jsonObject) throws FormException {
            return req.bindJSON(Phabricator.class, jsonObject);
        }
    }
}
