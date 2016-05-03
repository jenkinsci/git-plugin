package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletException;

import org.kohsuke.stapler.QueryParameter;

/**
 * Git Browser for GitLab
 */
public class GitLab extends GitRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    private final double version;

    /* package */
    static final double DEFAULT_VERSION = 7.11;

    private static double valueOfVersion(String version) throws NumberFormatException {
        double tmpVersion = Double.valueOf(version);
        if (Double.isNaN(tmpVersion)) {
            throw new NumberFormatException("Version cannot be NaN (not a number)");
        }
        if (Double.isInfinite(tmpVersion)) {
            throw new NumberFormatException("Version cannot be infinite");
        }
        return tmpVersion;
    }

    @DataBoundConstructor
    public GitLab(String repoUrl, String version) {
        super(repoUrl);
        double tmpVersion;
        try {
            tmpVersion = valueOfVersion(version);
        } catch (NumberFormatException nfe) {
            tmpVersion = DEFAULT_VERSION;
        }
        this.version = tmpVersion;
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
        return new URL(getUrl(), calculatePrefix() + changeSet.getId());
    }

    /**
     * Creates a link to the commit diff.
     *
     * v &lt; 3.0: [GitLab URL]/commits/[Hash]#[File path]
     * v &lt; 8.0: [GitLab URL]/commit/[Hash]#[File path]
     * else:       [GitLab URL]/commit/[Hash]#diff-[index]
     *
     * @param path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
        final GitChangeSet changeSet = path.getChangeSet();
        String filelink = null;
        if(getVersion() < 8.0) {
        	filelink = "#" + path.getPath().toString();
        } else
        {
        	filelink = "#diff-" + String.valueOf(getIndexOfPath(path));
        }
        return new URL(getUrl(), calculatePrefix() + changeSet.getId() + filelink);
    }

    /**
     * Creates a link to the file.
     * v &le; 4.2: [GitLab URL]tree/[Hash]/[File path]
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
            if(getVersion() <= 4.2) {
                return new URL(getUrl(), "tree/" + path.getChangeSet().getId() + "/" + path.getPath());
            } else if(getVersion() < 5.1) {
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

        /**
         * Validate the contents of the version field.
         *
         * @param version gitlab version value entered by the user
         * @return validation result, either ok() or error(msg)
         * @throws IOException
         * @throws ServletException
         */
        public FormValidation doCheckVersion(@QueryParameter(fixEmpty = true) final String version)
                throws IOException, ServletException {
            if (version == null) {
                return FormValidation.error("Version is required");
            }
            try {
                valueOfVersion(version);
            } catch (NumberFormatException nfe) {
                return FormValidation.error("Can't convert '" + version + "' to a number: " + nfe.getMessage());
            }
            return FormValidation.ok();
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
