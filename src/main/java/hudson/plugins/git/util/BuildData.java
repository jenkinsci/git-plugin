package hudson.plugins.git.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Run;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.*;

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
     * Map of branch name -> build (Branch name to last built SHA1).
     *
     * <p>
     * This map contains all the branches we've built in the past (including the build that this {@link BuildData}
     * is attached to) 
     */
    public Map<String, Build> buildsByBranchName = new HashMap<String, Build>();

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
    public Set<String> remoteUrls = new HashSet<String>();

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
        return "git";
    }

    public Object readResolve() {
        Map<String,Build> newBuildsByBranchName = new HashMap<String,Build>();
        
        for (Map.Entry<String, Build> buildByBranchName : buildsByBranchName.entrySet()) {
            String branchName = fixNull(buildByBranchName.getKey());
            Build build = buildByBranchName.getValue();
            newBuildsByBranchName.put(branchName, build);
        }

        this.buildsByBranchName = newBuildsByBranchName;

        if(this.remoteUrls == null)
            this.remoteUrls = new HashSet<String>();

        return this;
    }
    
    /**
     * Return true if the history shows this SHA1 has been built.
     * False otherwise.
     * @param sha1
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

        IdentityHashMap<Build, Build> clonedBuilds = new IdentityHashMap<Build, Build>();

        clone.buildsByBranchName = new HashMap<String, Build>();
        clone.remoteUrls = new HashSet<String>();

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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BuildData)) {
            return false;
        } else {
            BuildData otherBuildData = (BuildData) o;

            if (otherBuildData.remoteUrls.equals(this.remoteUrls)
                    && otherBuildData.buildsByBranchName.equals(this.buildsByBranchName)
                    && otherBuildData.lastBuild.equals(this.lastBuild)) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Remove branches from BuildData that have been seen in the past but do not exist anymore
     * @param keepBranches all branches available in current repository state
     */
    public void purgeStaleBranches(Set<Branch> keepBranches) {
        Set<String> names = new HashSet<String>(buildsByBranchName.keySet());
        for (Branch branch : keepBranches) {
            String name = branch.getName();
            if (name.startsWith("refs/")) {
                names.remove(name.substring(5));
            }
            if (name.startsWith("remotes/")) {
                names.remove(name.substring(8));
            }
        }
        for (String name : names) {
            buildsByBranchName.remove(name);
        }
    }
}
