package hudson.plugins.git.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Run;
import hudson.plugins.git.Branch;
import hudson.plugins.git.UserRemoteConfig;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import static hudson.Util.fixNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * Captures the Git related information for a single build.
 *
 * <p>
 * This object is added to {@link AbstractBuild#getActions()}.
 * It persists Git related information for a single build, and is used
 * at run time to build up an {@link BuildData} object.
 */
@ExportedBean(defaultVisibility = 999)
public class BuildDetails implements Action, Serializable, Cloneable {
    private static final long serialVersionUID = 1L;

    /**
     * The current build.
     */
    private final Build build;

    /**
     * The name of the SCM as given by the user.
     */
    private String scmName;

    /**
     * The URLs that have been referenced.
     */
    @NonNull
    private Set<String> remoteUrls = new HashSet<>();

    /**
     * Allow disambiguation of the action url when multiple {@link BuildDetails} actions present.
     */
    @CheckForNull
    private Integer index;

    public BuildDetails(Build build, String scmName, Collection<UserRemoteConfig> remoteConfigs) {
        this.build = build;
        this.scmName = scmName;
        for(UserRemoteConfig c : remoteConfigs) {
            remoteUrls.add(c.getUrl());
        }
    }

    @Exported
    public final Build getBuild() {
        return build;
    }

    /**
     * Returns the build details display name, optionally with SCM name.
     * This string needs to be relatively short because it is
     * displayed in a column with other short links.  If it is
     * lengthened, it causes the other data on the page to shift
     * right.  The page is then difficult to read.
     *
     * @return build details display name
     */
    public String getDisplayName() {
        if (scmName != null && !scmName.isEmpty())
            return "Git Build Details: " + scmName;
        return "Git Build Details";
    }

    public String getIconFileName() {
        return jenkins.model.Jenkins.RESOURCE_PATH+"/plugin/git/icons/git-32x32.png";
    }

    public String getUrlName() {
        return index == null ? "git" : "git-"+index;
    }

    /**
     * Sets an identifier used to disambiguate multiple {@link BuildDetails} actions attached to a {@link Run}
     *
     * @param index the index, indexes less than or equal to {@code 1} will be discarded.
     */
    public void setIndex(Integer index) {
        this.index = index == null || index <= 1 ? null : index;
    }

    /**
     * Gets the identifier used to disambiguate multiple {@link BuildDetails} actions attached to a {@link Run}.
     *
     * @return the index.
     */
    @CheckForNull
    public Integer getIndex() {
        return index;
    }

    @Restricted(NoExternalUse.class) // only used from stapler/jelly
    @CheckForNull
    public Run<?,?> getOwningRun() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req == null) {
            return null;
        }
        return req.findAncestorObject(Run.class);
    }

    public void setScmName(String scmName)
    {
        this.scmName = scmName;
    }

    @Exported
    public String getScmName()
    {
        if (scmName == null)
            scmName = "";
        return scmName;
    }

    public void addRemoteUrl(String remoteUrl) {
        remoteUrls.add(remoteUrl);
    }

    @Exported
    public  Set<String> getRemoteUrls() {
        return remoteUrls;
    }

    public boolean hasBeenReferenced(String remoteUrl) {
        return remoteUrls.contains(remoteUrl);
    }

    public Api getApi() {
        return new Api(this);
    }

    @Override
    public String toString() {
        final String scmNameString = scmName == null ? "<null>" : scmName;
        return super.toString()+"[scmName="+scmNameString+
                ",remoteUrls="+remoteUrls+
                ",build="+build+"]";
    }

    /**
     * Returns a normalized form of a source code URL to be used in guessing if
     * two different URL's are referring to the same source repository. Note
     * that the comparison is only a guess. Trailing slashes are removed from
     * the URL, and a trailing ".git" suffix is removed. If the input is a URL
     * form (like https:// or http:// or ssh://) then URI.normalize() is called
     * in an attempt to further normalize the URL.
     *
     * @param url repository URL to be normalized
     * @return normalized URL as a string
     */
    private String normalize(String url) {
        if (url == null) {
            return null;
        }
        /* Remove trailing slashes and .git suffix from URL */
        String normalized = url.replaceAll("/+$", "").replaceAll("[.]git$", "");
        if (url.contains("://")) {
            /* Only URI.normalize https://, http://, and ssh://, not user@hostname:path */
            try {
                /* Use URI.normalize() to further normalize the URI */
                URI uri = new URI(normalized);
                normalized = uri.normalize().toString();
            } catch (URISyntaxException ex) {
                LOGGER.log(Level.FINEST, "URI syntax exception on " + url, ex);
            }
        }
        return normalized;
    }

    /**
     * Like {@link #equals(Object)} but doesn't check the URL as strictly, since those can vary
     * while still representing the same remote repository.
     *
     * @param that the {@link BuildDetails} to compare with.
     * @return {@code true} if the supplied {@link BuildDetails} is similar to this {@link BuildDetails}.
     * @since 3.2.0
     */
    public boolean similarTo(BuildDetails that) {
        if (that == null) {
            return false;
        }
        /* Not similar if exactly one of the two remoteUrls is null */
        if ((this.remoteUrls == null) ^ (that.remoteUrls == null)) {
            return false;
        }
        if (this.build == null ? that.build != null : !this.build.equals(that.build)) {
            return false;
        }
        Set<String> thisUrls = new HashSet<>(this.remoteUrls.size());
        for (String url: this.remoteUrls) {
            thisUrls.add(normalize(url));
        }
        Set<String> thatUrls = new HashSet<>(that.remoteUrls.size());
        for (String url: that.remoteUrls) {
            thatUrls.add(normalize(url));
        }
        return thisUrls.equals(thatUrls);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BuildDetails)) {
            return false;
        }

        BuildDetails otherBuildDetails = (BuildDetails) o;

        /* Not equal if exactly one of the two remoteUrls is null */
        if ((this.remoteUrls == null) ^ (otherBuildDetails.remoteUrls == null)) {
            return false;
        }

        /* Not equal if remoteUrls differ */
        if ((this.remoteUrls != null) && (otherBuildDetails.remoteUrls != null)
                && !this.remoteUrls.equals(otherBuildDetails.remoteUrls)) {
            return false;
        }

        /* Not equal if exactly one of the two build is null */
        if ((this.build == null) ^ (otherBuildDetails.build == null)) {
            return false;
        }

        /* Not equal if build differs */
        if ((this.build != null) && (otherBuildDetails.build != null)
                && !this.build.equals(otherBuildDetails.build)) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int result = 3;
        result = result * 17 + ((this.remoteUrls == null) ? 5 : this.remoteUrls.hashCode());
        result = result * 17 + ((this.build == null) ? 11 : this.build.hashCode());
        return result;
    }

    /* Package protected for easier testing */
    static final Logger LOGGER = Logger.getLogger(BuildDetails.class.getName());
}
