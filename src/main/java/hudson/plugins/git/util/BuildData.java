package hudson.plugins.git.util;

import hudson.Functions;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.GitSCM;
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
     * This map contains all the branches we've built in the past 
     * (including the build that this {@link BuildData} is attached to)
     * <p>
     * Has been demoted from public to private access in v1.4.1. 
     * 
     * @deprecated Since 1.4.1. Replaced by global mapping, as build-local
     * mapping is inefficient on GIT repositories with many branches. Use
     * {@link #getBuildsByBranchName()} to get a view into the global mapping.
     * Not yet marked as transient, as old builds will not have established
     * the proper project<->BuildData mapping and must continue using this field
     * until the mapping is established with another build.
     */
    private Map<String, Build> buildsByBranchName;

    /**
     * The last build that we did. Do note that this is possibly not the newest
     * value as stored in the map returned by {@link #getBuildsByBranchName()}.
     */
    public Build              lastBuild;

    /**
     * The name of the SCM as given by the user.
     */
    public String scmName;

    /**
     * The name of the project for which this BuildData is intended.
     * <p>
     * Used to identify the correct entry in {@link GitSCM.BuildsBySourceMapper}
     * to store the branches-to-builds information in the global map.
     * Previously, this was implicitly handled by storing the map in each build.
     * See the annotation of {@link #buildsByBranchName} why this was
     * deprecated.
     * <p>
     * Do note that this field will be null for builds created prior to
     * 1.4.1. This
     * 
     * @since 1.4.1
     */
    public String projectName;
    
    /**
     * The URLs that have been referenced.
     */
    public Set<String> remoteUrls = new HashSet<String>();

    public BuildData() {
    }

    public BuildData(String scmName) {
        this.scmName = scmName;
    }

    public BuildData(String projectName, String scmName, Collection<UserRemoteConfig> remoteConfigs) {
        this.projectName = projectName;
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
        /* Previously, this method refreshed the locally stored
         * buildsByBranchName map. Since that map is now a global property,
         * no refresh is needed.
         */
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
            for(Build b : this.getBuildsByBranchName().values()) {
                if(b.revision.getSha1().equals(sha1))
                    return true;
            }
            return false;
        }
        catch(Exception ex) {
            return false;
        }
    }

    public void saveBuild(Build build) {
        lastBuild = build;
        if (this.projectName != null) {
            for(Branch branch : build.revision.getBranches()) {
                GitSCM.buildsMapper.addBranchToBuildMap(
                        this.projectName, fixNull(branch.getName()), build
                );
            }
        }
    }

    public Build getLastBuildOfBranch(String branch) {
        return this.getBuildsByBranchName().get(branch);
    }

    @Exported
    public Revision getLastBuiltRevision() {
        return lastBuild==null?null:lastBuild.revision;
    }

    @Exported
    public Map<String,Build> getBuildsByBranchName() {
        //Check if an old map exists, if so, we add its content to the
        //global map without overwriting existing entries
        if (buildsByBranchName != null) {
            //Check if we have a project<->BuildData mapping
            if (this.projectName != null) {
                // Get the global mapper and splice-in the correct mapping
                if (GitSCM.buildsMapper == null) {
                    //This should not have happened!
                    return buildsByBranchName;
                }
                GitSCM.buildsMapper.addAllFromBranchToBuildMap(
                        this.projectName, buildsByBranchName, false
                );
                //Removing the old buildsByBranchName object
                buildsByBranchName = null;
                return GitSCM.buildsMapper.getBranchToBuildMap(this.projectName);
            } else {
                //Must wait until GitSCM.getBuildData() has set the project name
                return buildsByBranchName;
            }
        }
        if (this.projectName == null) {
            // Without a project name, we can't retrieve mappings from the
            // global object; in that case, we return an empty map
            return new HashMap<String, Build>();
        }
        return GitSCM.buildsMapper.getBranchToBuildMap(this.projectName);
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
        
        if (projectName != null) {
            clone.projectName = projectName;
        }
        
        clone.lastBuild = this.lastBuild.clone();
        
        clone.remoteUrls = new HashSet<String>();
        
        for(String remoteUrl : getRemoteUrls()) {
            clone.addRemoteUrl(remoteUrl);
        }

        return clone;
    }

    public Api getApi() {
        return new Api(this);
    }

    @Override
    public String toString() {
        return String.format(
                "[scmName=%s, remoteUrls=%s, project=%s, buildsByBranchName=%s, lastBuild=%s]",
                (scmName == null) ? "<null>" : scmName,
                remoteUrls,
                (projectName == null) ? "<null>" : projectName,
                this.getBuildsByBranchName(),
                lastBuild
        );
    }
}
