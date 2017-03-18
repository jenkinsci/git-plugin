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
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import static hudson.Util.fixNull;
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
     * Like {@link #equals(Object)} but doesn't check the branch names as strictly  as those can vary depending on the
     * configured remote name.
     *
     * @param that the {@link BuildData} to compare with.
     * @return {@code true} if the supplied {@link BuildData} is similar to this {@link BuildData}.
     * @since TODO
     */
    public boolean similarTo(BuildData that) {
        if (that == null) return false;
        if (this.remoteUrls == null ? that.remoteUrls != null : !this.remoteUrls.equals(that.remoteUrls)) {
            return false;
        }
        if (this.lastBuild == null ? that.lastBuild != null : !this.lastBuild.equals(that.lastBuild)) {
            return false;
        }
        // assume if there is a prefix/ that the prefix is the origin name and strip it for similarity comparison
        // now if branch names contain slashes anyway and the user has not configured an origin name
        // we could have a false positive... but come on, it's the same repo and the same revision on the same build
        // that's similar enough. If you had configured a remote name we would see these as origin/feature/foobar and
        // origin/bugfix/foobar but you have not configured a remote name, and both branches are the same revision
        // anyway... and on the same build
        // TODO consider revisiting as part of fixing JENKINS-42665
        Set<String> thisUrls = new HashSet<>(this.remoteUrls.size());
        for (String url: this.remoteUrls) {
            int index = url.indexOf('/');
            if (index == -1 || index + 1 >= url.length()) {
                thisUrls.add(url);
            } else {
                thisUrls.add(url.substring(index + 1));
            }
        }
        Set<String> thatUrls = new HashSet<>(that.remoteUrls.size());
        for (String url: that.remoteUrls) {
            int index = url.indexOf('/');
            if (index == -1 || index + 1 >= url.length()) {
                thatUrls.add(url);
            } else {
                thatUrls.add(url.substring(index + 1));
            }
        }
        return thisUrls.equals(thatUrls);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BuildData)) {
            return false;
        }

        BuildData otherBuildData = (BuildData) o;

        /* Not equal if exactly one of the two remoteUrls is null */
        if ((this.remoteUrls == null) ^ (otherBuildData.remoteUrls == null)) {
            return false;
        }

        /* Not equal if remoteUrls differ */
        if ((this.remoteUrls != null) && (otherBuildData.remoteUrls != null)
                && !this.remoteUrls.equals(otherBuildData.remoteUrls)) {
            return false;
        }

        /* Not equal if exactly one of the two buildsByBranchName is null */
        if ((this.buildsByBranchName == null) ^ (otherBuildData.buildsByBranchName == null)) {
            return false;
        }

        /* Not equal if buildsByBranchName differ */
        if ((this.buildsByBranchName != null) && (otherBuildData.buildsByBranchName != null)
                && !this.buildsByBranchName.equals(otherBuildData.buildsByBranchName)) {
            return false;
        }

        /* Not equal if exactly one of the two lastBuild is null */
        if ((this.lastBuild == null) ^ (otherBuildData.lastBuild == null)) {
            return false;
        }

        /* Not equal if lastBuild differs */
        if ((this.lastBuild != null) && (otherBuildData.lastBuild != null)
                && !this.lastBuild.equals(otherBuildData.lastBuild)) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int result = 3;
        result = result * 17 + ((this.remoteUrls == null) ? 5 : this.remoteUrls.hashCode());
        result = result * 17 + ((this.buildsByBranchName == null) ? 7 : this.buildsByBranchName.hashCode());
        result = result * 17 + ((this.lastBuild == null) ? 11 : this.lastBuild.hashCode());
        return result;
    }
}
