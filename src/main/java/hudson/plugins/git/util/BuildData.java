package hudson.plugins.git.util;

import hudson.Functions;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.RunAction;
import hudson.model.Api;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.GitSCM.BuildsBySourceMapper;

import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.*;

import static hudson.Util.fixNull;
import static hudson.init.InitMilestone.JOB_LOADED;

/**
 * Captures the Git related information for a build.
 *
 * <p>
 * This object is added to {@link AbstractBuild#getActions()}.
 * This persists the Git related information of that build.
 */
@ExportedBean(defaultVisibility = 999)
public class BuildData implements RunAction, Serializable, Cloneable {
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
     * until the mapping is established with the next build on that job.
     * 
     * @see BuildData#configureXtream()
     * @see BuildData#localBuildsMapperLink
     * @see BuildData#buildsByBranchName
     * @see GitSCM#buildsMapper
     * @see GitSCM.BuildsBySourceMapper
     */
    private Map<String, Build> buildsByBranchName;

    /**
     * This field must be a reference to the global {@link GitSCM#buildsMapper}.
     * <p>
     * Basically, it has to be transmittable over a channel to a remote host,
     * but may not be saved in the job configuration via XSTREAM.
     * <p>
     * This strange set-up is needed, because this map needs to be available on
     * the slave when it executes certain GIT commands, but may not be saved in
     * each build and also may not be a copy of the static field in
     * {@link GitSCM} to conserve disk space and main memory.
     * <p>
     * Use {@link #getLocalBuildMapperLink()} to get this field without having
     * to care that is null in old deserialised {@link BuildData} versions.
     * 
     * @see BuildData#configureXtream()
     * @see BuildData#localBuildsMapperLink
     * @see BuildData#buildsByBranchName
     * @see GitSCM#buildsMapper
     * @see GitSCM.BuildsBySourceMapper
     * 
     * @since 1.4.1
     */
    private BuildsBySourceMapper localBuildsMapperLink = GitSCM.buildsMapper;
    
    /**
     * The last build that we did. Do note that this is possibly not the newest
     * value as stored in the global build-by-branch map stored in
     * {@link GitSCM#buildsMapper} / {@link #localBuildsMapperLink}.
     * <p>
     * Therefore, {@link #getBuildsByBranchName()} makes sure to return a copy
     * of that map where the {@link #lastBuild} is spliced in.
     * 
     * @see #getBuildsByBranchName()
     * @see #addOwnBranches(Map)
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
                localBuildsMapperLink.addBranchToBuildMap(
                        this.projectName, fixNull(branch.getName()), build
                );
            }
        }
    }
    
    
    public void onLoad() {
        //Nothing to do
    }
    
    public void onBuildComplete() {
        //Nothing to do
    }
    
    @SuppressWarnings("rawtypes")
    public void onAttached(Run run) {
        Job<?,?> job = run.getParent();
        if (job != null) {
            String pName = job.getFullName();
            if (pName != null) {
                this.projectName = pName;
            }
        }
    }

    private BuildsBySourceMapper getLocalBuildMapperLink() {
        if (localBuildsMapperLink == null) {
            localBuildsMapperLink = GitSCM.buildsMapper;
        }
        return localBuildsMapperLink;
    }
    
    public Build getLastBuildOfBranch(String branch) {
        return this.getBuildsByBranchName().get(branch);
    }

    @Exported
    public Revision getLastBuiltRevision() {
        return lastBuild==null?null:lastBuild.revision;
    }

    /**
     * This method takes a build map and returns a copy of it, where the last
     * build for the branches stored in the revisions from {@link #lastBuild}
     * are replaced by the above last build.
     * <p>
     * This is necessary, as many parts in the plug-in assume that the
     * {@link #buildsByBranchName} map still is copied into each build and is
     * a snapshot in time.
     * <p>
     * Thankfully, that assumption only needs to hold in respect to the
     * branches stored in {@link #lastBuild}. Therefore, this method makes
     * sure that the outside assumption of the "snapshot in time" character
     * holds well enough.
     * 
     * @param buildMap the current map containing the branches-to-builds
     * association.
     * @return a copy of the buildMap with values from {@link #lastBuild}
     * spliced in.
     */
    private Map<String, Build> addOwnBranches(Map<String, Build> buildMap) {
        if (buildMap == null) {
            return null;
        }
        Map<String, Build> copy = new HashMap<String,Build>(buildMap);
        if (this.lastBuild != null) {
            Revision r = this.lastBuild.getRevision();
            if (r != null) {
                for (Branch b : r.getBranches()) {
                    //TODO: Should we really overwrite a branch, if it is already there?
                    copy.put(b.getName(), this.lastBuild);
                }
            }
        }
        return copy;
    }
    
    @Exported
    public Map<String,Build> getBuildsByBranchName() {
        //Check if an old map exists, if so, we add its content to the
        //global map without overwriting existing entries
        if (buildsByBranchName != null) {
            //Check if we have a project<->BuildData mapping
            if (this.projectName != null) {
                // Get the global mapper and splice-in the correct mapping
                if (getLocalBuildMapperLink() == null) {
                    //This should not have happened!
                    return buildsByBranchName;
                }
                getLocalBuildMapperLink().addAllFromBranchToBuildMap(
                        this.projectName, buildsByBranchName, false
                );
                //Removing the old buildsByBranchName object
                buildsByBranchName = null;
                //Grab the newest branch map; and splice-in ourselves if missing
                return addOwnBranches(
                        getLocalBuildMapperLink().getBranchToBuildMap(this.projectName)
                );
            } else {
                //Must wait until GitSCM.getBuildData() has set the project name
                return buildsByBranchName;
            }
        }
        if (this.projectName == null) {
            // Without a project name, we can't retrieve mappings from the
            // global object; in that case, we return an empty map
            return addOwnBranches(new HashMap<String, Build>());
        }
        return addOwnBranches(
                getLocalBuildMapperLink().getBranchToBuildMap(this.projectName)
        );
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
    
    
    /**
     * This method makes sure that the {@link #localBuildsMapperLink} field is
     * not serialised to disk.
     * <p>
     * It is still serialized via the network, though, when transmitted to build
     * hosts. Keep that in mind given that this object never actually stops
     * growing, as new branches are continuously added it, but never removed.
     * 
     * @see BuildData#configureXtream()
     * @see BuildData#localBuildsMapperLink
     * @see BuildData#buildsByBranchName
     * @see GitSCM#buildsMapper
     * @see GitSCM.BuildsBySourceMapper
     */
    @Initializer(before=JOB_LOADED)
    public static void configureXtream() {
        //Making sure that XStream does not save this field to disk; it shall
        //only be serialised over the network; but not saved to disk!
        Items.XSTREAM.omitField(BuildData.class, "localBuildsMapperLink");
    }
}
