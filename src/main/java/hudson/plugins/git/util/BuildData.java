package hudson.plugins.git.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Run;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
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
 * Captures the Git related information for a build.
 *
 * <p>
 * This object is added to {@link AbstractBuild#getActions()}.
 * This persists the Git related information of that build.
 */
@ExportedBean(defaultVisibility = 999)
public class BuildData implements Action, Serializable, Cloneable {
    private static final long serialVersionUID = 1L;

    /**
     * Map of branch {@code name -> build} (Branch name to last built SHA1).
     *
     * <p>
     * This map contains all the branches we've built in the past (including the build that this {@link BuildData}
     * is attached to)
     */
    public Map<String, Build> buildsByBranchName = new HashMap<>();

    /**
     * The last build that we did (among the values in {@link #buildsByBranchName}.)
     */
    public Build lastBuild;

    /**
     * The name of the SCM as given by the user.
     */
    public String scmName;

    /**
     * The URLs that have been referenced.
     */
    public Set<String> remoteUrls = new HashSet<>();

    /**
     * Allow disambiguation of the action url when multiple {@link BuildData} actions present.
     */
    @CheckForNull
    private Integer index;

    public BuildData() {
    }

    public BuildData(String scmName) {
        this.scmName = scmName;
    }

    public BuildData(String scmName, Collection<UserRemoteConfig> remoteConfigs) {
        this.scmName = scmName;
        for(UserRemoteConfig c : remoteConfigs) {
            remoteUrls.add(c.getUrl());
        }
    }

    /**
     * Returns the build data display name, optionally with SCM name.
     * This string needs to be relatively short because it is
     * displayed in a column with other short links.  If it is
     * lengthened, it causes the other data on the page to shift
     * right.  The page is then difficult to read.
     *
     * @return build data display name
     */
    public String getDisplayName() {
        if (scmName != null && !scmName.isEmpty())
            return "Git Build Data:" + scmName;
        return "Git Build Data";
    }

    public String getIconFileName() {
        return jenkins.model.Jenkins.RESOURCE_PATH+"/plugin/git/icons/git-32x32.png";
    }

    public String getUrlName() {
        return index == null ? "git" : "git-"+index;
    }

    /**
     * Sets an identifier used to disambiguate multiple {@link BuildData} actions attached to a {@link Run}
     *
     * @param index the index, indexes less than or equal to {@code 1} will be discarded.
     */
    public void setIndex(Integer index) {
        this.index = index == null || index <= 1 ? null : index;
    }

    /**
     * Gets the identifier used to disambiguate multiple {@link BuildData} actions attached to a {@link Run}.
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

    public Object readResolve() {
        Map<String,Build> newBuildsByBranchName = new HashMap<>();

        for (Map.Entry<String, Build> buildByBranchName : buildsByBranchName.entrySet()) {
            String branchName = fixNull(buildByBranchName.getKey());
            Build build = buildByBranchName.getValue();
            newBuildsByBranchName.put(branchName, build);
        }

        this.buildsByBranchName = newBuildsByBranchName;

        if(this.remoteUrls == null)
            this.remoteUrls = new HashSet<>();

        return this;
    }

    /**
     * Return true if the history shows this SHA1 has been built.
     * False otherwise.
     * @param sha1 SHA1 hash of commit
     * @return true if sha1 has been built
     */
    public boolean hasBeenBuilt(ObjectId sha1) {
    	return getLastBuild(sha1) != null;
    }

    public Build getLastBuild(ObjectId sha1) {
        // fast check by first checking most recent build
        if (lastBuild != null && (lastBuild.revision.getSha1().equals(sha1) || lastBuild.marked.getSha1().equals(sha1))) return lastBuild;
        try {
            for(Build b : buildsByBranchName.values()) {
                if(b.revision.getSha1().equals(sha1) || b.marked.getSha1().equals(sha1))
                    return b;
            }

            return null;
        }
        catch(Exception ex) {
            return null;
        }
    }

    public void saveBuild(Build build) {
    	lastBuild = build;
    	for(Branch branch : build.marked.getBranches()) {
            buildsByBranchName.put(fixNull(branch.getName()), build);
    	}
        for(Branch branch : build.revision.getBranches()) {
            buildsByBranchName.put(fixNull(branch.getName()), build);
        }
    }

    public Build getLastBuildOfBranch(String branch) {
        return buildsByBranchName.get(branch);
    }

    /**
     * Gets revision of the previous build.
     * @return revision of the last build.
     *    May be null will be returned if nothing has been checked out (e.g. due to wrong repository or branch)
     */
    @Exported
    public @CheckForNull Revision getLastBuiltRevision() {
        return lastBuild==null?null:lastBuild.revision;
    }

    @Exported
    public Map<String,Build> getBuildsByBranchName() {
        return buildsByBranchName;
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

    @Override
    public BuildData clone() {
        BuildData clone;
        try {
            clone = (BuildData) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Error cloning BuildData", e);
        }

        IdentityHashMap<Build, Build> clonedBuilds = new IdentityHashMap<>();

        clone.buildsByBranchName = new HashMap<>();
        clone.remoteUrls = new HashSet<>();

        for (Map.Entry<String, Build> buildByBranchName : buildsByBranchName.entrySet()) {
            String branchName = buildByBranchName.getKey();
            if (branchName == null) {
                branchName = "";
            }
            Build build = buildByBranchName.getValue();
            Build clonedBuild = clonedBuilds.get(build);
            if (clonedBuild == null) {
                clonedBuild = build.clone();
                clonedBuilds.put(build, clonedBuild);
            }
            clone.buildsByBranchName.put(branchName, clonedBuild);
        }

        if (lastBuild != null) {
            clone.lastBuild = clonedBuilds.get(lastBuild);
            if (clone.lastBuild == null) {
                clone.lastBuild = lastBuild.clone();
                clonedBuilds.put(lastBuild, clone.lastBuild);
            }
        }

        for(String remoteUrl : getRemoteUrls())
        {
            clone.addRemoteUrl(remoteUrl);
        }

        return clone;
    }

    public Api getApi() {
        return new Api(this);
    }

    @Override
    public String toString() {
        final String scmNameString = scmName == null ? "<null>" : scmName;
        return super.toString()+"[scmName="+scmNameString+
                ",remoteUrls="+remoteUrls+
                ",buildsByBranchName="+buildsByBranchName+
                ",lastBuild="+lastBuild+"]";
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
     * @param that the {@link BuildData} to compare with.
     * @return {@code true} if the supplied {@link BuildData} is similar to this {@link BuildData}.
     * @since 3.2.0
     */
    public boolean similarTo(BuildData that) {
        if (that == null) {
            return false;
        }
        /* Not similar if exactly one of the two remoteUrls is null */
        if ((this.remoteUrls == null) ^ (that.remoteUrls == null)) {
            return false;
        }
        if (this.lastBuild == null ? that.lastBuild != null : !this.lastBuild.equals(that.lastBuild)) {
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BuildData that = (BuildData) o;

        return Objects.equals(remoteUrls, that.remoteUrls)
                && Objects.equals(buildsByBranchName, that.buildsByBranchName)
                && Objects.equals(lastBuild, that.lastBuild);
    }

    @Override
    public int hashCode() {
        return Objects.hash(remoteUrls, buildsByBranchName, lastBuild);
    }

    /* Package protected for easier testing */
    static final Logger LOGGER = Logger.getLogger(BuildData.class.getName());
}
