package hudson.plugins.git;

import hudson.scm.RepositoryBrowser;
import java.io.IOException;
import java.net.URL;

public abstract class GitRepositoryBrowser extends RepositoryBrowser<GitChangeSet> {
    /**
     * Determines the link to the diff between the version
     * in the specified revision of {@link GitChangeSet.Path} to its previous version.
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
