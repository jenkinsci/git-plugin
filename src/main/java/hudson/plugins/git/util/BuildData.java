package hudson.plugins.git.util;

import hudson.Functions;
import hudson.model.Action;
import hudson.model.Api;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.stapler.export.Exported;

import java.io.Serializable;
import java.util.*;

import static hudson.Util.fixNull;

/**
 * @deprecated see {@link hudson.plugins.git.BuildHistory}
 */
public class BuildData implements Action, Serializable, Cloneable {
    private static final long serialVersionUID = 1L;

    /**
     * Map of branch name -> build (Branch name to last built SHA1).
     *
     * <p>
     * This map contains all the branches we've built in the past (including the build that this {@link BuildData}
     * is attached to) 
     */
    public transient Map<String, BuiltRevision> buildsByBranchName = new HashMap<String, BuiltRevision>();

    /**
     * The last build that we did (among the values in {@link #buildsByBranchName}.)
     */
    public transient BuiltRevision lastBuild;

    /**
     * The name of the SCM as given by the user.
     */
    public transient String scmName;

    /**
     * The URLs that have been referenced.
     */
    public transient Set<String> remoteUrls = new HashSet<String>();

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
        return Functions.getResourcePath()+"/plugin/git/icons/git-32x32.png";
    }

    public String getUrlName() {
        return "git";
    }

    public Object readResolve() {
        Map<String,BuiltRevision> newBuildsByBranchName = new HashMap<String,BuiltRevision>();
        
        for (Map.Entry<String, BuiltRevision> buildByBranchName : buildsByBranchName.entrySet()) {
            String branchName = fixNull(buildByBranchName.getKey());
            BuiltRevision builtRevision = buildByBranchName.getValue();
            newBuildsByBranchName.put(branchName, builtRevision);
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
     * @return
     */
    public boolean hasBeenBuilt(ObjectId sha1) {
    	try {
            for(BuiltRevision b : buildsByBranchName.values()) {
                if(b.revision.getSha1().equals(sha1) || b.marked.getSha1().equals(sha1))
                    return true;
            }

            return false;
    	}
    	catch(Exception ex) {
            return false;
    	}
    }

    public void saveBuild(BuiltRevision builtRevision) {
    	lastBuild = builtRevision;
    	for(Branch branch : builtRevision.marked.getBranches()) {
            buildsByBranchName.put(fixNull(branch.getName()), builtRevision);
    	}
    }

    public BuiltRevision getLastBuildOfBranch(String branch) {
        return buildsByBranchName.get(branch);
    }

    @Exported
    public Revision getLastBuiltRevision() {
        return lastBuild==null?null:lastBuild.revision;
    }

    @Exported
    public Map<String,BuiltRevision> getBuildsByBranchName() {
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

        IdentityHashMap<BuiltRevision, BuiltRevision> clonedBuilds = new IdentityHashMap<BuiltRevision, BuiltRevision>();

        clone.buildsByBranchName = new HashMap<String, BuiltRevision>();
        clone.remoteUrls = new HashSet<String>();

        for (Map.Entry<String, BuiltRevision> buildByBranchName : buildsByBranchName.entrySet()) {
            String branchName = buildByBranchName.getKey();
            if (branchName == null) {
                branchName = "";
            }
            BuiltRevision builtRevision = buildByBranchName.getValue();
            BuiltRevision clonedBuiltRevision = clonedBuilds.get(builtRevision);
            if (clonedBuiltRevision == null) {
                clonedBuiltRevision = builtRevision.clone();
                clonedBuilds.put(builtRevision, clonedBuiltRevision);
            }
            clone.buildsByBranchName.put(branchName, clonedBuiltRevision);
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
        return super.toString()+"[scmName="+scmName==null?"<null>":scmName+
                ",remoteUrls="+remoteUrls+
                ",buildsByBranchName="+buildsByBranchName+
                ",lastBuild="+lastBuild+"]";
    }
}
