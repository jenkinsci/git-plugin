package hudson.plugins.git.browser;

import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.RepositoryBrowser;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.URL;

public abstract class GitRepositoryBrowser extends RepositoryBrowser<GitChangeSet> {

    private /* mostly final */ String url;

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

        if (getNormalizeUrl()) {
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

    /**
     * Determines whether a URL should be normalized
	 * Overridden in the rare case where it shouldn't
     *
     * @return True if the URL should be normalized
     */
    protected boolean getNormalizeUrl() {
		return true;
	}
    
    /**
     * Calculate the index of the given path in a
     * sorted list of affected files
     *
     * @param path affected file path
     * @return The index in the lexicographical sorted filelist
     */
    protected int getIndexOfPath(Path path) throws IOException {
    	final String pathAsString = path.getPath();
    	final GitChangeSet changeSet = path.getChangeSet();
    	int i = 0;
    	boolean found = false;
    	for (String affected : changeSet.getAffectedPaths())
    	{
    		int res = affected.compareTo(pathAsString);
    		if (res == 0)
    			found = true;
    		else if (res < 0)
    			i++;
    	}
    	assert found;
    	return found ? i : -1;
    }

    private static final long serialVersionUID = 1L;
}
