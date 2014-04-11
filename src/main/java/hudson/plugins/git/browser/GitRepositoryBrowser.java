package hudson.plugins.git.browser;

import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.scm.RepositoryBrowser;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.URL;

public abstract class GitRepositoryBrowser extends RepositoryBrowser<GitChangeSet> {

    private /* mostly final */ String url;
    protected boolean normalizeUrl;

    @Deprecated
    protected GitRepositoryBrowser() {
    }

    protected GitRepositoryBrowser(String repourl) {
        this.url = repourl;
    }

    public final String getRepoUrl() {
        return url;
    }

    public final URL getUrl() throws IOException {
        String u = url;
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            Job job = req.findAncestorObject(Job.class);
            if (job != null) {
                EnvVars env = null;
                try {
                    env = job.getEnvironment(null, TaskListener.NULL);
                } catch (InterruptedException e) {
                    throw new IOException("Failed to retrieve job environment", e);
                }
                u = env.expand(url);
            }
        }

        if (normalizeUrl) {
            return normalizeToEndWithSlash(new URL(u));
        }
        else {
            return new URL(u);
        }
    }

    /**
     * Determines the link to the diff between the version
     * in the specified revision of {@link hudson.plugins.git.GitChangeSet.Path} to its previous version.
     *
     * @param path affected file path
     * @return
     *      null if the browser doesn't have any URL for diff.
     * @throws IOException
     */
    public abstract URL getDiffLink(GitChangeSet.Path path) throws IOException;

    /**
     * Determines the link to a single file under Git.
     * This page should display all the past revisions of this file, etc.
     *
     * @param path affected file path
     * @return
     *      null if the browser doesn't have any suitable URL.
     * @throws IOException
     */
    public abstract URL getFileLink(GitChangeSet.Path path) throws IOException;

    private static final long serialVersionUID = 1L;
}
