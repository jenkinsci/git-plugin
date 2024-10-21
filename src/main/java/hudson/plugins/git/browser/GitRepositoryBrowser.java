package hudson.plugins.git.browser;

import hudson.EnvVars;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.RepositoryBrowser;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.IOException;
import java.io.Serial;
import java.net.IDN;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.CheckForNull;

public abstract class GitRepositoryBrowser extends RepositoryBrowser<GitChangeSet> {

    private /* mostly final */ String url;
    private static final Logger LOGGER = Logger.getLogger(GitRepositoryBrowser.class.getName());

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
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null) {
            Job job = req.findAncestorObject(Job.class);
            if (job != null) {
                EnvVars env;
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
     * @throws IOException on input or output error
     */
    public abstract URL getDiffLink(GitChangeSet.Path path) throws IOException;

    /**
     * Determines the link to a single file under Git.
     * This page should display all the past revisions of this file, etc.
     *
     * @param path affected file path
     * @return
     *      null if the browser doesn't have any suitable URL.
     * @throws IOException on input or output error
     * @throws URISyntaxException on URI syntax error
     */
    public abstract URL getFileLink(GitChangeSet.Path path) throws IOException, URISyntaxException;

    /**
     * Determines the link to the given change set ID (SHA).
     *
     * @param commitId commit identifier, usually a SHA-1 hash
     * @return the URL to the change set or {@code null} if this repository browser doesn't have any meaningful URL for
     *         a change set
     */
    @CheckForNull
    public URL getChangeSetLink(final String commitId) throws IOException {
        if (commitId != null && !commitId.isBlank()) {
            return getChangeSetLink(new CommitChangeSet(commitId));
        }
        return null;
    }

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
     * @throws IOException on input or output error
     */
    protected int getIndexOfPath(Path path) throws IOException {
    	final String pathAsString = path.getPath();
    	final GitChangeSet changeSet = path.getChangeSet();
    	int i = 0;
    	for (String affected : changeSet.getAffectedPaths())
    	{
		if (affected.compareTo(pathAsString) < 0)
    			i++;
    	}
        return i;
    }

    public static URL encodeURL(URL url) throws IOException {
        try {
            return new URI(url.getProtocol(), url.getUserInfo(), IDN.toASCII(url.getHost()), url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toURL();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    protected static boolean initialChecksAndReturnOk(Item project, String cleanUrl){
        if (cleanUrl == null) {
            return true;
        }
        if (project == null || !project.hasPermission(Item.CONFIGURE)) {
            return true;
        }
        if (cleanUrl.contains("$")) {
            // set by variable, can't validate
            return true;
        }
        return false;
    }

    /* Top level domains that should always be considered valid */
    private static final Pattern SUFFIXES = Pattern.compile(".*[.](corp|home|local|localnet)$");

    /* Browser URL validation of remote/local urls */
    protected static boolean validateUrl(String url) throws URISyntaxException {
        try {
            URL urlToValidate = new URL(url);
            String hostname = urlToValidate.getHost();
            if (hostname == null) {
                LOGGER.log(Level.FINE, "Invalid hostname validating URL {0}", url);
                return false;
            }
            if (SUFFIXES.matcher(hostname).matches()) {
                return true;
            }
            if (InetAddress.getByName(hostname) == null) {
                LOGGER.log(Level.FINE, "Host unknown validating URL {0}", url);
                return false;
            }
        } catch (MalformedURLException ex) {
            LOGGER.log(Level.FINE, "Malformed URL exception validating URL " + url, ex);
            return false;
        } catch (UnknownHostException ex) {
            LOGGER.log(Level.FINE, "Unknown host exception validating URL " + url, ex);
            return false;
        }
        return true;
    }

    /**
     * Used to obtain a repository link to a Git commit ID (SHA hash).
     */
    private static class CommitChangeSet extends GitChangeSet {
        private final String id;

        CommitChangeSet(final String id) {
            super(Collections.emptyList(), false);

            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            CommitChangeSet that = (CommitChangeSet) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), id);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
