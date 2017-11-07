package hudson.plugins.git;

/**
 * Common interface to access the changelog options of multiple extensions
 *
 * @author <a href="mailto:jacob.e.keller@intel.com">Jacob Keller (jacob.e.keller@intel.com)</a>
 */
public interface ChangelogOptions {
    /* Return the revision specified by these changelog options */
    String getRevision();
}

