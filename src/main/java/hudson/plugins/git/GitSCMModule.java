package hudson.plugins.git;

import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.BuildData;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.spearce.jgit.lib.RepositoryConfig;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Tom
 * Date: 6/03/11
 * Time: 18:27
 * To change this template use File | Settings | File Templates.
 */
public class GitSCMModule implements Serializable {

    /**
     * All the remote repositories that we know about (in jgit format)
     */
    private List<RemoteConfig> remoteRepositories;

    /**
     * All the remote repositories that we know about (used by databinding)
     */
    private List<Repository> _remoteRepositories;

    /**
     * All the branches that we wish to care about building.
     */
    private List<BranchSpec> branches;

    /**
     * Optional local branch to work on.
     */
    private String localBranch;

    /**
     * Options for merging before a build.
     */
    private PreBuildMergeOptions mergeOptions;

    /**
     * Use --recursive flag on submodule commands - requires git>=1.6.5
     */
    private boolean recursiveSubmodules;

    private boolean doGenerateSubmoduleConfigurations;

    private Collection<SubmoduleConfig> submoduleCfg;

    private String relativeTargetDir;

    @DataBoundConstructor
    public GitSCMModule(List<Repository> remoteRepositories, List<BranchSpec> branches, String localBranch, PreBuildMergeOptions mergeOptions, String relativeTargetDir) {
        this.branches = branches;
        this.localBranch = Util.fixEmptyAndTrim(localBranch);
        this._remoteRepositories = remoteRepositories;
        this.mergeOptions = mergeOptions != null ? mergeOptions : new PreBuildMergeOptions();

//        this.doGenerateSubmoduleConfigurations = doGenerateSubmoduleConfigurations;
//        this.submoduleCfg = submoduleCfg;

        this.relativeTargetDir = relativeTargetDir != null ? relativeTargetDir : ".";
        this.recursiveSubmodules = recursiveSubmodules;

        try {
            this.remoteRepositories = GitSCM.DescriptorImpl.createRepositoryConfigurations(remoteRepositories);
        }
        catch (IOException e1) {
            throw new GitException("Error creating repositories", e1);
        }

        if (mergeOptions != null) {
            String mergeRemoteName = mergeOptions.getMergeRemoteName();
            if (StringUtils.isEmpty(mergeRemoteName)) {
                mergeOptions.setMergeRemote(this.remoteRepositories.get(0));
            } else {
                RemoteConfig mergeRemote = null;
                for (RemoteConfig remote : this.remoteRepositories) {
                    if (remote.getName().equals(mergeRemoteName)) {
                        mergeRemote = remote;
                        break;
                    }
                }
                if (mergeRemote == null) {
                    throw new IllegalArgumentException("No remote repository configured with name '" + mergeRemoteName + "'");
                }
            }
        }

        if(branches.size() == 0) {
            branches.add(new BranchSpec("*/master"));
        }



    }

    public Collection<SubmoduleConfig> getSubmoduleCfg() {
        return submoduleCfg;
    }

    public void setSubmoduleCfg(Collection<SubmoduleConfig> submoduleCfg) {
        this.submoduleCfg = submoduleCfg;
    }

    public List<Repository> getRemoteRepositories() {
        return _remoteRepositories;
    }

    public List<RemoteConfig> getRepositories() {
        // Handle null-value to ensure backwards-compatibility, ie project configuration missing the <repositories/> XML element
        if (remoteRepositories == null)
            return new ArrayList<RemoteConfig>();
        return remoteRepositories;
    }

    /**
     * Expand parameters in {@link #remoteRepositories} with the parameter values provided in the given build
     * and return them.
     *
     * @return can be empty but never null.
     */
    public List<RemoteConfig> getParamExpandedRepos(AbstractBuild<?,?> build) {
        List<RemoteConfig> expandedRepos = new ArrayList<RemoteConfig>();

        for (RemoteConfig oldRepo : Util.fixNull(remoteRepositories)) {
            expandedRepos.add(GitSCM.newRemoteConfig(oldRepo.getName(),
                    oldRepo.getURIs().get(0).toPrivateString(),
                    new RefSpec(getRefSpec(oldRepo, build))));
        }

        return expandedRepos;
    }

    public RemoteConfig getRepositoryByName(String repoName) {
        for (RemoteConfig r : getRepositories()) {
            if (r.getName().equals(repoName)) {
                return r;
            }
        }

        return null;
    }

    /**
     * If the configuration is such that we are tracking just one branch of one repository
     * return that branch specifier (in the form of something like "origin/master"
     *
     * Otherwise return null.
     */
    String getSingleBranch(AbstractBuild<?, ?> build) {
        // if we have multiple branches skip to advanced usecase
        if (getBranches().size() != 1 || getRepositories().size() != 1)
            return null;

        String branch = getBranches().get(0).getName();
        String repository = getRepositories().get(0).getName();

        // replace repository wildcard with repository name
        if (branch.startsWith("*/"))
            branch = repository + branch.substring(1);

        // if the branch name contains more wildcards then the simple usecase
        // does not apply and we need to skip to the advanced usecase
        if (branch.contains("*"))
            return null;

        // substitute build parameters if available
        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters != null)
            branch = parameters.substitute(build, branch);
        return branch;
    }

    public boolean getRecursiveSubmodules() {
        return this.recursiveSubmodules;
    }

    public boolean getDoGenerate() {
        return this.doGenerateSubmoduleConfigurations;
    }

    public List<BranchSpec> getBranches() {
        return branches;
    }

    public PreBuildMergeOptions getMergeOptions() {
        return mergeOptions;
    }

    /**
     * Given the workspace, gets the working directory, which will be the workspace
     * if no relative target dir is specified. Otherwise, it'll be "workspace/relativeTargetDir".
     *
     * @param workspace
     * @return working directory
     */
    protected FilePath workingDirectory(final FilePath workspace) {

        if (relativeTargetDir == null || relativeTargetDir.length() == 0 || relativeTargetDir.equals(".")) {
            return workspace;
        }
        return workspace.child(relativeTargetDir);
    }

    public String getLocalBranch() {
        return Util.fixEmpty(localBranch);
    }

    public String getParamLocalBranch(AbstractBuild<?,?> build) {
        String branch = getLocalBranch();
        // substitute build parameters if available
        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters != null)
            branch = parameters.substitute(build, branch);
        return branch;
    }

    public String getRelativeTargetDir() {
        return relativeTargetDir;
    }

    private String getRefSpec(RemoteConfig repo, AbstractBuild<?,?> build) {
        String refSpec = repo.getFetchRefSpecs().get(0).toString();

        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters != null)
            refSpec = parameters.substitute(build, refSpec);

        return refSpec;
    }

    /**
     * Look back as far as needed to find a valid BuildData.  BuildData
     * may not be recorded if an exception occurs in the plugin logic.
     * @param build
     * @param clone
     * @return the last recorded build data
     */
    public BuildData getBuildData(Run build, boolean clone) {
        BuildData buildData = null;
        while (build != null) {
            buildData = getBuildData(build);
            if (buildData != null)
                break;
            build = build.getPreviousBuild();
        }

        if (buildData == null)
            return clone? new BuildData(getRelativeTargetDir()) : null;

        if (clone)
            return buildData.clone();
        else
            return buildData;
    }

    private BuildData getBuildData(Run run) {
        for (BuildData action: run.getActions(BuildData.class)) {
            if (getRelativeTargetDir().equals(action.getId())) {
                return action;
            }
        }
        return null;
    }

    BuildData fixNull(BuildData bd) {
        return bd!=null ? bd : new BuildData(getRelativeTargetDir()) /*dummy*/;
    }
}
