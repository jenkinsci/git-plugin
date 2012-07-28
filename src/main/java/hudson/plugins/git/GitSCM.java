package hudson.plugins.git;

import static hudson.Util.fixEmptyAndTrim;
import static hudson.init.InitMilestone.PLUGINS_PREPARED;
import static hudson.init.InitMilestone.PLUGINS_STARTED;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Hudson.MasterComputer;
import hudson.model.Items;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.util.BuildChooserDescriptor;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.plugins.git.util.GitUtils;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.FormValidation;
import hudson.util.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import net.sf.json.JSONObject;

/**
 * Git SCM.
 *
 * @author Nigel Magnay
 */
public class GitSCM extends SCM implements Serializable {

    // old fields are left so that old config data can be read in, but
    // they are deprecated. transient so that they won't show up in XML
    // when writing back
    @Deprecated
    transient String source;
    @Deprecated
    transient String branch;
    /**
     * Store a config version so we're able to migrate config on various
     * functionality upgrades.
     */
    private Long configVersion;
    /**
     * All the remote repositories that we know about.
     */
    private List<UserRemoteConfig> userRemoteConfigs;
    private transient List<RemoteConfig> remoteRepositories;
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
    private UserMergeOptions userMergeOptions;
    private transient PreBuildMergeOptions mergeOptions;
    /**
     * Use --recursive flag on submodule commands - requires git>=1.6.5
     */
    private boolean disableSubmodules;
    private boolean recursiveSubmodules;
    private boolean doGenerateSubmoduleConfigurations;
    private boolean authorOrCommitter;
    private boolean clean;
    private boolean wipeOutWorkspace;
    private boolean pruneBranches;
    private boolean remotePoll;
    private boolean ignoreNotifyCommit;

    /**
     * @deprecated
     *      Replaced by {@link #buildChooser} instead.
     */
    private transient String choosingStrategy;
    private BuildChooser buildChooser;
    public String gitTool = null;
    private GitRepositoryBrowser browser;
    private Collection<SubmoduleConfig> submoduleCfg;
    public static final String GIT_BRANCH = "GIT_BRANCH";
    public static final String GIT_COMMIT = "GIT_COMMIT";
    private String relativeTargetDir;
    private String reference;
    private String excludedRegions;
    private String excludedUsers;
    private String gitConfigName;
    private String gitConfigEmail;
    private boolean skipTag;
    private String includedRegions;
    private String scmName;

    public Collection<SubmoduleConfig> getSubmoduleCfg() {
        return submoduleCfg;
    }

    public void setSubmoduleCfg(Collection<SubmoduleConfig> submoduleCfg) {
        this.submoduleCfg = submoduleCfg;
    }

    static private List<UserRemoteConfig> createRepoList(String url) {
        List<UserRemoteConfig> repoList = new ArrayList<UserRemoteConfig>();
        repoList.add(new UserRemoteConfig(url, null, null));
        return repoList;
    }

    /**
     * A convenience constructor that sets everything to default.
     *
     * @param repositoryUrl
     *      Repository URL to clone from.
     */
    public GitSCM(String repositoryUrl) {
        this(
                null,
                createRepoList(repositoryUrl),
                Collections.singletonList(new BranchSpec("")),
                null,
                false, Collections.<SubmoduleConfig>emptyList(), false,
                false, new DefaultBuildChooser(), null, null, false, null,
                null,
                null, null, null, false, false, false, false, null, null, false, null, false);
    }

    @DataBoundConstructor
    public GitSCM(
            String scmName,
            List<UserRemoteConfig> userRemoteConfigs,
            List<BranchSpec> branches,
            UserMergeOptions userMergeOptions,
            Boolean doGenerateSubmoduleConfigurations,
            Collection<SubmoduleConfig> submoduleCfg,
            boolean clean,
            boolean wipeOutWorkspace,
            BuildChooser buildChooser, GitRepositoryBrowser browser,
            String gitTool,
            boolean authorOrCommitter,
            String relativeTargetDir,
            String reference,
            String excludedRegions,
            String excludedUsers,
            String localBranch,
            boolean disableSubmodules,
            boolean recursiveSubmodules,
            boolean pruneBranches,
            boolean remotePoll,
            String gitConfigName,
            String gitConfigEmail,
            boolean skipTag,
            String includedRegions,
            boolean ignoreNotifyCommit) {

        this.scmName = scmName;

        // moved from createBranches
        if (branches == null) {
            branches = new ArrayList<BranchSpec>();
        }
        if (branches.isEmpty()) {
            branches.add(new BranchSpec("*/master"));
        }
        this.branches = branches;

        this.localBranch = Util.fixEmptyAndTrim(localBranch);

        this.userRemoteConfigs = userRemoteConfigs;
        this.userMergeOptions = userMergeOptions;
        updateFromUserData();

        // TODO: getBrowserFromRequest
        this.browser = browser;

        // emulate bindJSON behavior here
        if (doGenerateSubmoduleConfigurations != null) {
            this.doGenerateSubmoduleConfigurations = doGenerateSubmoduleConfigurations.booleanValue();
        } else {
            this.doGenerateSubmoduleConfigurations = false;
        }

        if (submoduleCfg == null) {
            submoduleCfg = new ArrayList<SubmoduleConfig>();
        }
        this.submoduleCfg = submoduleCfg;

        this.clean = clean;
        this.wipeOutWorkspace = wipeOutWorkspace;
        this.configVersion = 2L;
        this.gitTool = gitTool;
        this.authorOrCommitter = authorOrCommitter;
        this.buildChooser = buildChooser;
        this.relativeTargetDir = relativeTargetDir;
        this.reference = reference;
        this.excludedRegions = excludedRegions;
        this.excludedUsers = excludedUsers;
        this.disableSubmodules = disableSubmodules;
        this.recursiveSubmodules = recursiveSubmodules;
        this.pruneBranches = pruneBranches;
        this.ignoreNotifyCommit = ignoreNotifyCommit;
        if (remotePoll
            && (branches.size() != 1
            || branches.get(0).getName().contains("*")
            || userRemoteConfigs.size() != 1
            || (excludedRegions != null && excludedRegions.length() > 0)
            || (submoduleCfg.size() != 0)
            || (excludedUsers != null && excludedUsers.length() > 0))) {
            LOGGER.log(Level.WARNING, "Cannot poll remotely with current configuration.");
            this.remotePoll = false;
        } else {
            this.remotePoll = remotePoll;
        }

        this.gitConfigName = gitConfigName;
        this.gitConfigEmail = gitConfigEmail;
        this.skipTag = skipTag;
        this.includedRegions = includedRegions;
        buildChooser.gitSCM = this; // set the owner
    }

    private void updateFromUserData() throws GitException {
        // do what newInstance used to do directly from the request data
        try {
            String[] pUrls = new String[userRemoteConfigs.size()];
            String[] repoNames = new String[userRemoteConfigs.size()];
            String[] refSpecs = new String[userRemoteConfigs.size()];
            for (int i = 0; i < userRemoteConfigs.size(); ++i) {
                pUrls[i] = userRemoteConfigs.get(i).getUrl();
                repoNames[i] = userRemoteConfigs.get(i).getName();
                refSpecs[i] = userRemoteConfigs.get(i).getRefspec();
            }
            this.remoteRepositories = DescriptorImpl.createRepositoryConfigurations(pUrls, repoNames, refSpecs);

            // TODO: replace with new repositories
        } catch (IOException e1) {
            throw new GitException("Error creating repositories", e1);
        }

        try {
            this.mergeOptions = DescriptorImpl.createMergeOptions(userMergeOptions, remoteRepositories);

            // replace with new merge options
            if (userMergeOptions != null) {
                this.userMergeOptions = new UserMergeOptions(userMergeOptions.getMergeRemote(), userMergeOptions.getMergeTarget());
            }
        } catch (FormException ex) {
            throw new GitException("Error creating JGit merge options", ex);
        }
    }

    public Object readResolve() {
        // Migrate data

        // Default unspecified to v0
        if (configVersion == null) {
            configVersion = 0L;
        }


        if (source != null) {
            remoteRepositories = new ArrayList<RemoteConfig>();
            branches = new ArrayList<BranchSpec>();
            doGenerateSubmoduleConfigurations = false;
            mergeOptions = new PreBuildMergeOptions();

            recursiveSubmodules = false;

            remoteRepositories.add(newRemoteConfig("origin", source, new RefSpec("+refs/heads/*:refs/remotes/origin/*")));
            if (branch != null) {
                branches.add(new BranchSpec(branch));
            } else {
                branches.add(new BranchSpec("*/master"));
            }
        }


        if (configVersion < 1 && branches != null) {
            // Migrate the branch specs from
            // single * wildcard, to ** wildcard.
            for (BranchSpec branchSpec : branches) {
                String name = branchSpec.getName();
                name = name.replace("*", "**");
                branchSpec.setName(name);
            }
        }

        // update from version 1
        if (mergeOptions != null && userMergeOptions == null) {
            // update from version 1
            if (mergeOptions.doMerge()) {
                userMergeOptions = new UserMergeOptions(mergeOptions.getRemoteBranchName(),
                        mergeOptions.getMergeTarget());
            }
        }

        if (remoteRepositories != null && userRemoteConfigs == null) {
            userRemoteConfigs = new ArrayList<UserRemoteConfig>();
            for(RemoteConfig cfg : remoteRepositories) {
                // converted as in config.jelly
                String url = "";
                if (cfg.getURIs().size() > 0 && cfg.getURIs().get(0) != null)
                    url = cfg.getURIs().get(0).toPrivateString();

                String refspec = "";
                if (cfg.getFetchRefSpecs().size() > 0 && cfg.getFetchRefSpecs().get(0) != null)
                    refspec = cfg.getFetchRefSpecs().get(0).toString();

                userRemoteConfigs.add(new UserRemoteConfig(url, cfg.getName(), refspec));
            }
        }

        // patch internal objects from user data
        // if (configVersion == 2) {
        if (remoteRepositories == null || mergeOptions == null) {
            // if we don't catch GitException here, the whole job fails to load
            try {
                updateFromUserData();
            } catch (GitException e) {
                LOGGER.log(Level.WARNING, "Failed to load SCM data", e);
                mergeOptions = new PreBuildMergeOptions();
            }
        }

        if (mergeOptions.doMerge() && mergeOptions.getMergeRemote() == null) {
            mergeOptions.setMergeRemote(remoteRepositories.get(0));
        }

        if (choosingStrategy != null && buildChooser == null) {
            for (BuildChooserDescriptor d : BuildChooser.all()) {
                if (choosingStrategy.equals(d.getLegacyId())) {
                    try {
                        buildChooser = d.clazz.newInstance();
                    } catch (InstantiationException e) {
                        LOGGER.log(Level.WARNING, "Failed to instantiate the build chooser", e);
                    } catch (IllegalAccessException e) {
                        LOGGER.log(Level.WARNING, "Failed to instantiate the build chooser", e);
                    }
                }
            }
        }
        if (buildChooser == null) {
            buildChooser = new DefaultBuildChooser();
        }
        buildChooser.gitSCM = this;

        return this;
    }

    public String getIncludedRegions() {
        return includedRegions;
    }

    public String[] getIncludedRegionsNormalized() {
        return (includedRegions == null || includedRegions.trim().equals(""))
                ? null : includedRegions.split("[\\r\\n]+");
    }

    private Pattern[] getIncludedRegionsPatterns() {
        String[] included = getIncludedRegionsNormalized();
        return getRegionsPatterns(included);
    }

    public String getExcludedRegions() {
        return excludedRegions;
    }

    public String[] getExcludedRegionsNormalized() {
        return (excludedRegions == null || excludedRegions.trim().equals(""))
                ? null : excludedRegions.split("[\\r\\n]+");
    }

    private Pattern[] getExcludedRegionsPatterns() {
        String[] excluded = getExcludedRegionsNormalized();
        return getRegionsPatterns(excluded);
    }

    private Pattern[] getRegionsPatterns(String[] regions) {
        if (regions != null) {
            Pattern[] patterns = new Pattern[regions.length];

            int i = 0;
            for (String region : regions) {
                patterns[i++] = Pattern.compile(region);
            }

            return patterns;
        }

        return new Pattern[0];
    }

    public String getExcludedUsers() {
        return excludedUsers;
    }

    public Set<String> getExcludedUsersNormalized() {
        String s = fixEmptyAndTrim(excludedUsers);
        if (s == null) {
            return Collections.emptySet();
        }

        Set<String> users = new HashSet<String>();
        for (String user : s.split("[\\r\\n]+")) {
            users.add(user.trim());
        }
        return users;
    }

    @Override
    public GitRepositoryBrowser getBrowser() {
        return browser;
    }

    public String getGitConfigName() {
        return gitConfigName;
    }

    public String getGitConfigEmail() {
        return gitConfigEmail;
    }

    public String getReference() {
        return reference;
    }

    public String getGitConfigNameToUse() {
        String confName = fixEmptyAndTrim(gitConfigName);
        if (confName == null) {
        	String globalConfigName = ((DescriptorImpl) getDescriptor()).getGlobalConfigName();
        	confName = fixEmptyAndTrim(globalConfigName);
        }
        return confName;
    }

    public String getGitConfigEmailToUse() {
        String confEmail = fixEmptyAndTrim(gitConfigEmail);
        if (confEmail == null) {
        	String globalConfigEmail = ((DescriptorImpl) getDescriptor()).getGlobalConfigEmail();
        	confEmail = fixEmptyAndTrim(globalConfigEmail);
        }
        return confEmail;
    }

    public boolean getSkipTag() {
        return this.skipTag;
    }

    public boolean getPruneBranches() {
        return this.pruneBranches;
    }

    public boolean getRemotePoll() {
        return this.remotePoll;
    }

    public boolean getWipeOutWorkspace() {
        return this.wipeOutWorkspace;
    }

    public boolean getClean() {
        return this.clean;
    }

    public boolean isIgnoreNotifyCommit() {
        return ignoreNotifyCommit;
    }

    public BuildChooser getBuildChooser() {
        return buildChooser;
    }

    public void setBuildChooser(BuildChooser buildChooser) {
        this.buildChooser = buildChooser;
    }

    /**
     * Expand parameters in {@link #remoteRepositories} with the parameter values provided in the given build
     * and return them.
     *
     * @return can be empty but never null.
     */
    public List<RemoteConfig> getParamExpandedRepos(AbstractBuild<?, ?> build) {
        List<RemoteConfig> expandedRepos = new ArrayList<RemoteConfig>();

        for (RemoteConfig oldRepo : Util.fixNull(remoteRepositories)) {
            expandedRepos.add(newRemoteConfig(getParameterString(oldRepo.getName(), build),
                    getParameterString(oldRepo.getURIs().get(0).toPrivateString(), build),
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

    @Exported
    public List<UserRemoteConfig> getUserRemoteConfigs() {
        return Collections.unmodifiableList(userRemoteConfigs);
    }

    @Exported
    public List<RemoteConfig> getRepositories() {
        // Handle null-value to ensure backwards-compatibility, ie project configuration missing the <repositories/> XML element
        if (remoteRepositories == null) {
            return new ArrayList<RemoteConfig>();
        }
        return remoteRepositories;
    }

    public String getGitTool() {
        return gitTool;
    }

    private String getParameterString(String original, AbstractBuild<?, ?> build) {
        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters != null) {
            original = parameters.substitute(build, original);
        }

        return original;
    }

    private String getRefSpec(RemoteConfig repo, AbstractBuild<?, ?> build) {
        String refSpec = repo.getFetchRefSpecs().get(0).toString();

        return getParameterString(refSpec, build);
    }

    /**
     * If the configuration is such that we are tracking just one branch of one repository
     * return that branch specifier (in the form of something like "origin/master"
     *
     * Otherwise return null.
     */
    private String getSingleBranch(AbstractBuild<?, ?> build) {
        // if we have multiple branches skip to advanced usecase
        if (getBranches().size() != 1 || getRepositories().size() != 1) {
            return null;
        }

        String branch = getBranches().get(0).getName();
        String repository = getRepositories().get(0).getName();

        // replace repository wildcard with repository name
        if (branch.startsWith("*/")) {
            branch = repository + branch.substring(1);
        }

        // if the branch name contains more wildcards then the simple usecase
        // does not apply and we need to skip to the advanced usecase
        if (branch.contains("*")) {
            return null;
        }

        // substitute build parameters if available
        branch = getParameterString(branch, build);

        // Check for empty string - replace with "**" when seen.
        if (branch.equals("")) {
            branch = "**";
        }

        return branch;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> abstractBuild, Launcher launcher, TaskListener taskListener) throws IOException, InterruptedException {
        return SCMRevisionState.NONE;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        if(remotePoll)
            return false;
        return true;
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, final TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        // Poll for changes. Are there any unbuilt revisions that Hudson ought to build ?

        listener.getLogger().println("Using strategy: " + buildChooser.getDisplayName());

        final AbstractBuild lastBuild = project.getLastBuild();

        if (lastBuild != null) {
            listener.getLogger().println("[poll] Last Build : #" + lastBuild.getNumber());
        } else {
            // If we've never been built before, well, gotta build!
            listener.getLogger().println("[poll] No previous build, so forcing an initial build.");
            return PollingResult.BUILD_NOW;
        }

        final BuildData buildData = fixNull(getBuildData(lastBuild, false));

        if (buildData.lastBuild != null) {
            listener.getLogger().println("[poll] Last Built Revision: " + buildData.lastBuild.revision);
        }

        final String singleBranch = getSingleBranch(lastBuild);

        // fast remote polling needs a single branch and an existing last build
        if (this.remotePoll && singleBranch != null && buildData.lastBuild != null && buildData.lastBuild.getRevision() != null) {
            String gitExe = "";
            GitTool[] installations = ((hudson.plugins.git.GitTool.DescriptorImpl)Hudson.getInstance().getDescriptorByType(GitTool.DescriptorImpl.class)).getInstallations();
            for(GitTool i : installations) {
                if(i.getName().equals(gitTool)) {
                    gitExe = i.getGitExe();
                    break;
                }
            }
            final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener);
            IGitAPI git = new GitAPI(gitExe, workspace, listener, environment, reference);
            String gitRepo = getParamExpandedRepos(lastBuild).get(0).getURIs().get(0).toString();
            String headRevision = git.getHeadRev(gitRepo, getBranches().get(0).getName());

            if(buildData.lastBuild.getRevision().getSha1String().equals(headRevision)) {
                return PollingResult.NO_CHANGES;
            } else {
                return PollingResult.BUILD_NOW;
            }

        }

        final String gitExe;
        {
            //If this project is tied onto a node, it's built always there. On other cases,
            //polling is done on the node which did the last build.
            //
            Label label = project.getAssignedLabel();
            if (label != null && label.isSelfLabel()) {
                if (label.getNodes().iterator().next() != project.getLastBuiltOn()) {
                    listener.getLogger().println("Last build was not on tied node, forcing rebuild.");
                    return PollingResult.BUILD_NOW;
                }
                gitExe = getGitExe(label.getNodes().iterator().next(), listener);
            } else {
                gitExe = getGitExe(project.getLastBuiltOn(), listener);
            }
        }

        final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener);

        FilePath workingDirectory = workingDirectory(workspace,environment);

        // Rebuild if the working directory doesn't exist
        // I'm actually not 100% sure about this, but I'll leave it in for now.
        // Update 9/9/2010 - actually, I think this *was* needed, since we weren't doing a better check
        // for whether we'd ever been built before. But I'm fixing that right now anyway.
        
        // JENKINS-10880: workingDirectory can be null
        if (workingDirectory == null || !workingDirectory.exists()) {
            return PollingResult.BUILD_NOW;
        }

        final List<RemoteConfig> paramRepos = getParamExpandedRepos(lastBuild);
//        final String singleBranch = getSingleBranch(lastBuild);
        final BuildChooserContext context = new BuildChooserContextImpl(project,null);

        boolean pollChangesResult = workingDirectory.act(new FileCallable<Boolean>() {

            private static final long serialVersionUID = 1L;

            public Boolean invoke(File localWorkspace, VirtualChannel channel) throws IOException, InterruptedException {
                IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment, reference);

                if (git.hasGitRepo()) {
                    // Repo is there - do a fetch
                    listener.getLogger().println("Fetching changes from the remote Git repositories");

                    // Fetch updates
                    for (RemoteConfig remoteRepository : paramRepos) {
                        fetchFrom(git, listener, remoteRepository);
                    }

                    listener.getLogger().println("Polling for changes in");

                    Collection<Revision> origCandidates = buildChooser.getCandidateRevisions(
                            true, singleBranch, git, listener, buildData, context);

                    List<Revision> candidates = new ArrayList<Revision>();

                    for (Revision c : origCandidates) {
                        if (!isRevExcluded(git, c, listener)) {
                            candidates.add(c);
                        }
                    }

                    return (candidates.size() > 0);
                } else {
                    listener.getLogger().println("No Git repository yet, an initial checkout is required");
                    return true;
                }
            }
        });

        return pollChangesResult ? PollingResult.SIGNIFICANT : PollingResult.NO_CHANGES;
    }

    private BuildData fixNull(BuildData bd) {
        return bd != null ? bd : new BuildData(getScmName()) /*dummy*/;
    }

    private void cleanSubmodules(IGitAPI parentGit,
            File workspace,
            TaskListener listener,
            RemoteConfig remoteRepository) {

        List<IndexEntry> submodules = parentGit.getSubmodules("HEAD");

        for (IndexEntry submodule : submodules) {
            try {
                RemoteConfig submoduleRemoteRepository = getSubmoduleRepository(parentGit, remoteRepository, submodule.getFile());
                File subdir = new File(workspace, submodule.getFile());
                listener.getLogger().println("Trying to clean submodule in " + subdir);
                IGitAPI subGit = new GitAPI(parentGit.getGitExe(), new FilePath(subdir),
                        listener, parentGit.getEnvironment(), parentGit.getReference());

                subGit.clean();
            } catch (Exception ex) {
                listener.getLogger().println(
                        "Problem cleaning submodule in "
                        + submodule.getFile()
                        + " - could be unavailable. Continuing anyway");
            }

        }
    }

    /**
     * Fetch information from a particular remote repository.
     *
     * @param git
     * @param listener
     * @param remoteRepository
     * @return true if fetch goes through, false otherwise.
     * @throws
     */
    private boolean fetchFrom(IGitAPI git,
            TaskListener listener,
            RemoteConfig remoteRepository) {
        try {
            git.fetch(remoteRepository);
            return true;
        } catch (GitException ex) {
            ex.printStackTrace(listener.error(
                    "Problem fetching from " + remoteRepository.getName()
                    + " / " + remoteRepository.getName()
                    + " - could be unavailable. Continuing anyway"));
        }
        return false;
    }

    /**
     * Fetch submodule information from relative to a particular remote repository.
     *
     * @param git
     * @param listener
     * @param remoteRepository
     * @return true if fetch goes through, false otherwise.
     * @throws
     */
    private boolean fetchSubmodulesFrom(IGitAPI git,
            File workspace,
            TaskListener listener,
            RemoteConfig remoteRepository) {
        boolean fetched = true;

        try {
            // This ensures we don't miss changes to submodule paths and allows
            // seamless use of bare and non-bare superproject repositories.
            git.setupSubmoduleUrls(remoteRepository.getName(), listener);

            /* with the new re-ordering of "git checkout" and the submodule
             * commands, it appears that this test will always succeed... But
             * we'll keep it anyway for now. */
            boolean hasHead = true;
            try {
                git.revParse("HEAD");
            } catch (GitException e) {
                hasHead = false;
            }

            if (hasHead) {
                List<IndexEntry> submodules = git.getSubmodules("HEAD");

                for (IndexEntry submodule : submodules) {
                    try {
                        RemoteConfig submoduleRemoteRepository = getSubmoduleRepository(git,
                                remoteRepository,
                                submodule.getFile());
                        File subdir = new File(workspace, submodule.getFile());

                        listener.getLogger().println(
                                "Trying to fetch " + submodule.getFile() + " into " + subdir);

                        IGitAPI subGit = new GitAPI(git.getGitExe(),
                                new FilePath(subdir),
                                listener, git.getEnvironment(), git.getReference());

                        subGit.fetch(submoduleRemoteRepository);
                    } catch (Exception ex) {
                        ex.printStackTrace(listener.error(
                                "Problem fetching from submodule "
                                + submodule.getFile()
                                + " - could be unavailable. Continuing anyway"));
                    }
                }
            }
        } catch (GitException ex) {
            ex.printStackTrace(listener.error(
                    "Problem fetching submodules from a path relative to "
                    + remoteRepository.getName()
                    + " / " + remoteRepository.getName()
                    + " - could be unavailable. Continuing anyway"));
            fetched = false;
        }

        return fetched;
    }

    public RemoteConfig getSubmoduleRepository(IGitAPI parentGit,
            RemoteConfig orig,
            String name) throws GitException {
        // The first attempt at finding the URL in this new code relies on
        // submoduleInit, submoduleSync, fixSubmoduleUrls already being executed
        // since the last fetch of the super project.  (This is currently done
        // by calling git.setupSubmoduleUrls(...). )
        String refUrl = parentGit.getSubmoduleUrl(name);
        return newRemoteConfig(name, refUrl, orig.getFetchRefSpecs().get(0));
    }

    private RemoteConfig newRemoteConfig(String name, String refUrl, RefSpec refSpec) {

        try {
            Config repoConfig = new Config();
            // Make up a repo config from the request parameters

            repoConfig.setString("remote", name, "url", refUrl);
            repoConfig.setString("remote", name, "fetch", refSpec.toString());

            return RemoteConfig.getAllRemoteConfigs(repoConfig).get(0);
        } catch (Exception ex) {
            throw new GitException("Error trying to create JGit configuration", ex);
        }
    }

    private boolean changeLogResult(String changeLog, File changelogFile) throws IOException {
        if (changeLog == null) {
            return false;
        } else {
            changelogFile.delete();

            FileOutputStream fos = new FileOutputStream(changelogFile);
            fos.write(changeLog.getBytes());
            fos.close();
            // Write to file
            return true;
        }
    }

    /**
     * Exposing so that we can get this from GitPublisher.
     */
    public String getGitExe(Node builtOn, TaskListener listener) {
        GitTool[] gitToolInstallations = Hudson.getInstance().getDescriptorByType(GitTool.DescriptorImpl.class).getInstallations();
        for (GitTool t : gitToolInstallations) {
            //If gitTool is null, use first one.
            if (gitTool == null) {
                gitTool = t.getName();
            }

            if (t.getName().equals(gitTool)) {
                if (builtOn != null) {
                    try {
                        String s = t.forNode(builtOn, listener).getGitExe();
                        return s;
                    } catch (IOException e) {
                        listener.getLogger().println("Failed to get git executable");
                    } catch (InterruptedException e) {
                        listener.getLogger().println("Failed to get git executable");
                    }
                }
            }
        }
        return null;
    }

    /**
     * If true, use the commit author as the changeset author, rather
     * than the committer.
     */
    public boolean getAuthorOrCommitter() {
        return authorOrCommitter;
    }

    /**
     * Web-bound method to let people look up a build by their SHA1 commit.
     */
    public AbstractBuild<?,?> getBySHA1(String sha1) {
        AbstractProject<?,?> p = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);
        for (AbstractBuild b : p.getBuilds()) {
            BuildData d = b.getAction(BuildData.class);
            if (d!=null && d.lastBuild!=null) {
                Build lb = d.lastBuild;
                if (lb.revision!=null      && lb.revision.getSha1String().startsWith(sha1))  return b;
                if (lb.mergeRevision!=null && lb.mergeRevision.getSha1String().startsWith(sha1))  return b;
            }
        }
        return null;
    }

    /*package*/ static class BuildChooserContextImpl implements BuildChooserContext, Serializable {
        final AbstractProject project;
        final AbstractBuild build;

        BuildChooserContextImpl(AbstractProject project, AbstractBuild build) {
            this.project = project;
            this.build = build;
        }

        public <T> T actOnBuild(ContextCallable<AbstractBuild<?,?>, T> callable) throws IOException, InterruptedException {
            return callable.invoke(build,Hudson.MasterComputer.localChannel);
        }

        public <T> T actOnProject(ContextCallable<AbstractProject<?,?>, T> callable) throws IOException, InterruptedException {
            return callable.invoke(project, MasterComputer.localChannel);
        }

        private Object writeReplace() {
            return Channel.current().export(BuildChooserContext.class,new BuildChooserContext() {
                public <T> T actOnBuild(ContextCallable<AbstractBuild<?,?>, T> callable) throws IOException, InterruptedException {
                    return callable.invoke(build,Channel.current());
                }

                public <T> T actOnProject(ContextCallable<AbstractProject<?,?>, T> callable) throws IOException, InterruptedException {
                    return callable.invoke(project,Channel.current());
                }
            });
        }
    }

    @Override
    public boolean checkout(final AbstractBuild build, Launcher launcher,
            final FilePath workspace, final BuildListener listener, File _changelogFile)
            throws IOException, InterruptedException {

        final EnvVars environment = build.getEnvironment(listener);
        
        final FilePath changelogFile = new FilePath(_changelogFile);

        listener.getLogger().println("Checkout:" + workspace.getName() + " / " + workspace.getRemote() + " - " + workspace.getChannel());
        listener.getLogger().println("Using strategy: " + buildChooser.getDisplayName());

        final FilePath workingDirectory = workingDirectory(workspace,environment);

        if (!workingDirectory.exists()) {
            workingDirectory.mkdirs();
        }

        final String projectName = build.getProject().getName();
        final int buildNumber = build.getNumber();

        final String gitExe = getGitExe(build.getBuiltOn(), listener);

        final String buildnumber = "jenkins-" + projectName + "-" + buildNumber;

        final BuildData buildData = getBuildData(build.getPreviousBuild(), true);

        if (buildData.lastBuild != null) {
            listener.getLogger().println("Last Built Revision: " + buildData.lastBuild.revision);
        }

        final String singleBranch = environment.expand( getSingleBranch(build) );
        final String paramLocalBranch = getParamLocalBranch(build);
        Revision tempParentLastBuiltRev = null;

        if (build instanceof MatrixRun) {
            MatrixBuild parentBuild = ((MatrixRun) build).getParentBuild();
            if (parentBuild != null) {
                BuildData parentBuildData = getBuildData(parentBuild, false);
                if (parentBuildData != null) {
                    tempParentLastBuiltRev = parentBuildData.getLastBuiltRevision();
                }
            }
        }

        final List<RemoteConfig> paramRepos = getParamExpandedRepos(build);

        final Revision parentLastBuiltRev = tempParentLastBuiltRev;

        final RevisionParameterAction rpa = build.getAction(RevisionParameterAction.class);
        final BuildChooserContext context = new BuildChooserContextImpl(build.getProject(), build);

        final Revision revToBuild = workingDirectory.act(new FileCallable<Revision>() {

            private static final long serialVersionUID = 1L;

            public Revision invoke(File localWorkspace, VirtualChannel channel)
                    throws IOException, InterruptedException {
                FilePath ws = new FilePath(localWorkspace);
                final PrintStream log = listener.getLogger();
                IGitAPI git = new GitAPI(gitExe, ws, listener, environment, reference);

                if (wipeOutWorkspace) {
                    log.println("Wiping out workspace first.");
                    try {
                        ws.deleteContents();
                    } catch (InterruptedException e) {
                        // I don't really care if this fails.
                    }
                }

                if (git.hasGitRepo()) {
                    // It's an update

                    if (paramRepos.size() == 1)
                        log.println("Fetching changes from 1 remote Git repository");
                    else
                        log.println(MessageFormat
                                .format("Fetching changes from {0} remote Git repositories",
                                        paramRepos));

                    boolean fetched = false;

                    for (RemoteConfig remoteRepository : paramRepos) {
                        if (fetchFrom(git, listener, remoteRepository)) {
                            fetched = true;
                        }
                    }

                    if (!fetched) {
                        listener.error("Could not fetch from any repository");
                        throw new GitException("Could not fetch from any repository");
                    }
                    // Do we want to prune first?
                    if (pruneBranches) {
                        log.println("Pruning obsolete local branches");
                        for (RemoteConfig remoteRepository : paramRepos) {
                            git.prune(remoteRepository);
                        }
                    }

                } else {

                    log.println("Cloning the remote Git repository");

                    // Go through the repositories, trying to clone from one
                    //
                    boolean successfullyCloned = false;
                    for (RemoteConfig rc : paramRepos) {
                        try {
                            git.clone(rc);
                            successfullyCloned = true;
                            break;
                        } catch (GitException ex) {
                            ex.printStackTrace(listener.error("Error cloning remote repo '%s' : %s", rc.getName(), ex.getMessage()));
                            // Failed. Try the next one
                            log.println("Trying next repository");
                        }
                    }

                    if (!successfullyCloned) {
                        listener.error("Could not clone repository");
                        throw new GitException("Could not clone");
                    }

                    boolean fetched = false;

                    // Also do a fetch
                    for (RemoteConfig remoteRepository : paramRepos) {
                        try {
                            git.fetch(remoteRepository);
                            fetched = true;
                        } catch (Exception e) {
                            e.printStackTrace(listener.error(
                                    "Problem fetching from " + remoteRepository.getName()
                                    + " / " + remoteRepository.getName()
                                    + " - could be unavailable. Continuing anyway."));
                        }
                    }

                    if (!fetched) {
                        listener.error("Could not fetch from any repository");
                        throw new GitException("Could not fetch from any repository");
                    }

                    if (getClean()) {
                        log.println("Cleaning workspace");
                        git.clean();

                        if (git.hasGitModules() && !disableSubmodules) {
                            git.submoduleClean(recursiveSubmodules);
                        }
                    }
                }

                if (parentLastBuiltRev != null) {
                    return parentLastBuiltRev;
                }

                if (rpa != null) {
                    return rpa.toRevision(git);
                }

                Collection<Revision> candidates = buildChooser.getCandidateRevisions(
                        false, singleBranch, git, listener, buildData, context);
                if (candidates.size() == 0) {
                    return null;
                }
                return candidates.iterator().next();
            }
        });


        if (revToBuild == null) {
            // getBuildCandidates should make the last item the last build, so a re-build
            // will build the last built thing.
            listener.error("Couldn't find any revision to build. Verify the repository and branch configuration for this job.");
            return false;
        }
        listener.getLogger().println("Commencing build of " + revToBuild);
        environment.put(GIT_COMMIT, revToBuild.getSha1String());
        Branch branch = revToBuild.getBranches().iterator().next();
        environment.put(GIT_BRANCH, branch.getName());

        BuildData returnedBuildData;
        if (mergeOptions.doMerge() && !revToBuild.containsBranchName(mergeOptions.getRemoteBranchName())) {
            returnedBuildData = workingDirectory.act(new FileCallable<BuildData>() {

                private static final long serialVersionUID = 1L;

                public BuildData invoke(File localWorkspace, VirtualChannel channel)
                        throws IOException, InterruptedException {
                    IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment, reference);

                    // Do we need to merge this revision onto MergeTarget

                    // Only merge if there's a branch to merge that isn't
                    // us..
                    listener.getLogger().println(
                            "Merging " + revToBuild + " onto "
                            + mergeOptions.getMergeTarget());

                    // checkout origin/blah
                    ObjectId target = git.revParse(mergeOptions.getRemoteBranchName());

                    git.checkoutBranch(paramLocalBranch, target.name());

                    try {
                        git.merge(revToBuild.getSha1().name());
                    } catch (Exception ex) {
                        // We still need to tag something to prevent
                        // repetitive builds from happening - tag the
                        // candidate
                        // branch.
                        git.checkoutBranch(paramLocalBranch, revToBuild.getSha1().name());

                        if (!getSkipTag()) {
                            git.tag(buildnumber, "Jenkins Build #"
                                    + buildNumber);
                        }

                        buildData.saveBuild(new Build(revToBuild, buildNumber, Result.FAILURE));
                        throw new AbortException("Branch not suitable for integration as it does not merge cleanly");
                    }

                    if (git.hasGitModules() && !disableSubmodules) {
                        // This ensures we don't miss changes to submodule paths and allows
                        // seamless use of bare and non-bare superproject repositories.
                        git.setupSubmoduleUrls(revToBuild, listener);
                        git.submoduleUpdate(recursiveSubmodules);
                    }

                    if (!getSkipTag()) {
                        // Tag the successful merge
                        git.tag(buildnumber, "Jenkins Build #" + buildNumber);
                    }

                    computeMergeChangeLog(git, revToBuild, mergeOptions.getRemoteBranchName(), listener, changelogFile);

                    Build build = new Build(revToBuild, buildNumber, null);
                    buildData.saveBuild(build);
                    GitUtils gu = new GitUtils(listener, git);
                    build.mergeRevision = gu.getRevisionForSHA1(target);
                    if (getClean()) {
                        listener.getLogger().println("Cleaning workspace");
                        git.clean();
                        if (git.hasGitModules() && !disableSubmodules) {
                            git.submoduleClean(recursiveSubmodules);
                        }
                    }

                    // Fetch the diffs into the changelog file
                    return buildData;
                }
            });
        } else {
            // No merge
            returnedBuildData = workingDirectory.act(new FileCallable<BuildData>() {

                private static final long serialVersionUID = 1L;

                public BuildData invoke(File localWorkspace, VirtualChannel channel)
                        throws IOException, InterruptedException {
                    IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment, reference);

                    // Straight compile-the-branch
                    listener.getLogger().println("Checking out " + revToBuild);

                    if (getClean()) {
                        listener.getLogger().println("Cleaning workspace");
                        git.clean();
                    }

                    git.checkoutBranch(paramLocalBranch, revToBuild.getSha1().name());

                    if (git.hasGitModules() && !disableSubmodules) {
                        // Git submodule update will only 'fetch' from where it
                        // regards as 'origin'. However,
                        // it is possible that we are building from a
                        // RemoteRepository with changes
                        // that are not in 'origin' AND it may be a new module that
                        // we've only just discovered.
                        // So - try updating from all RRs, then use the submodule
                        // Update to do the checkout
                        //
                        // Also, only do this if we're not doing recursive submodules, since that'll
                        // theoretically be dealt with there anyway.
                        if (!recursiveSubmodules) {
                            for (RemoteConfig remoteRepository : paramRepos) {
                                fetchSubmodulesFrom(git, localWorkspace, listener, remoteRepository);
                            }
                        }

                        // This ensures we don't miss changes to submodule paths and allows
                        // seamless use of bare and non-bare superproject repositories.
                        git.setupSubmoduleUrls(revToBuild, listener);
                        git.submoduleUpdate(recursiveSubmodules);

                    }

                    // if(compileSubmoduleCompares)
                    if (doGenerateSubmoduleConfigurations) {
                        SubmoduleCombinator combinator = new SubmoduleCombinator(
                                git, listener, localWorkspace, submoduleCfg);
                        combinator.createSubmoduleCombinations();
                    }

                    if (!getSkipTag()) {
                        // Tag the successful merge
                        git.tag(buildnumber, "Jenkins Build #" + buildNumber);
                    }

                    computeChangeLog(git, revToBuild, listener, buildData,changelogFile);

                    buildData.saveBuild(new Build(revToBuild, buildNumber, null));

                    // Fetch the diffs into the changelog file
                    return buildData;
                }
            });
        }

        build.addAction(returnedBuildData);
        build.addAction(new GitTagAction(build, returnedBuildData));

        return true;
    }

    /**
     * Build up change log from all the branches that we've merged into {@code revToBuild}
     *
     * @param git
     *      Used for invoking Git
     * @param revToBuild
     *      Points to the revision we'll be building. This includes all the branches we've merged.
     * @param listener
     *      Used for writing to build console
     * @param buildData
     *      Information that captures what we did during the last build. We need this for changelog,
     *      or else we won't know where to stop.
     */
    private void computeChangeLog(IGitAPI git, Revision revToBuild, BuildListener listener, BuildData buildData, FilePath changelogFile) throws IOException, InterruptedException {
        int histories = 0;

        PrintStream out = new PrintStream(changelogFile.write());
        try {
            for (Branch b : revToBuild.getBranches()) {
                Build lastRevWas = buildChooser.prevBuildForChangelog(b.getName(), buildData, git);
                if (lastRevWas != null) {
                    if (git.isCommitInRepo(lastRevWas.getSHA1().name())) {
                        putChangelogDiffs(git, b.name, lastRevWas.getSHA1().name(), revToBuild.getSha1().name(), out);
                        histories++;
                    } else {
                        listener.getLogger().println("Could not record history. Previous build's commit, " + lastRevWas.getSHA1().name()
                                + ", does not exist in the current repository.");
                    }
                } else {
                    listener.getLogger().println("No change to record in branch " + b.getName());
                }
            }
        } catch (GitException ge) {
            out.println("Unable to retrieve changeset");
        } finally {
            IOUtils.closeQuietly(out);
        }

        if (histories > 1) {
            listener.getLogger().println("Warning : There are multiple branch changesets here");
        }
    }

    private void computeMergeChangeLog(IGitAPI git, Revision revToBuild, String revFrom, BuildListener listener, FilePath changelogFile) throws IOException, InterruptedException {
        if (!git.isCommitInRepo(revFrom)) {
            listener.getLogger().println("Could not record history. Previous build's commit, " + revFrom
                                         + ", does not exist in the current repository.");
        } else {
            int histories = 0;

            PrintStream out = new PrintStream(changelogFile.write());
            try {
                for (Branch b : revToBuild.getBranches()) {
                    putChangelogDiffs(git, b.name, revFrom, revToBuild.getSha1().name(), out);
                    histories++;
                }
            } catch (GitException ge) {
                out.println("Unable to retrieve changeset");
            } finally {
                IOUtils.closeQuietly(out);
            }

            if (histories > 1) {
                listener.getLogger().println("Warning : There are multiple branch changesets here");
            }
        }
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, java.util.Map<String, String> env) {
        super.buildEnvVars(build, env);
        Revision rev = fixNull(getBuildData(build, false)).getLastBuiltRevision();
        String singleBranch = getSingleBranch(build);
        if (singleBranch != null){
            env.put(GIT_BRANCH, singleBranch);
        } else if (rev != null) {
            Branch branch = rev.getBranches().iterator().next();
            env.put(GIT_BRANCH, branch.getName());
        }
        if (rev != null) {
            String commit = rev.getSha1String();
            if (commit != null) {
                env.put(GIT_COMMIT, commit);
            }
        }

        String confName = getGitConfigNameToUse();
        if ((confName != null) && (!confName.equals(""))) {
            env.put("GIT_COMMITTER_NAME", confName);
            env.put("GIT_AUTHOR_NAME", confName);
        }
        String confEmail = getGitConfigEmailToUse();
        if ((confEmail != null) && (!confEmail.equals(""))) {
            env.put("GIT_COMMITTER_EMAIL", confEmail);
            env.put("GIT_AUTHOR_EMAIL", confEmail);
        }

    }

    private void putChangelogDiffs(IGitAPI git, String branchName, String revFrom,
            String revTo, PrintStream fos) throws IOException {
        fos.println("Changes in branch " + branchName + ", between " + revFrom + " and " + revTo);
        git.changelog(revFrom, revTo, fos);
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new GitChangeLogParser(getAuthorOrCommitter());
    }

    /**
     * @return the scmName
     */
    public String getScmName() {
        return scmName;
    }

    /** Compares the SCM names for equality even if they're null. */
    private boolean sameScm(String scmName1, String scmName2) {
        scmName1 = (scmName1 == null ? "" : scmName1);
        scmName2 = (scmName2 == null ? "" : scmName2);
        return scmName1.equals(scmName2);
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<GitSCM> {

        private String gitExe;
        private String globalConfigName;
        private String globalConfigEmail;

        public DescriptorImpl() {
            super(GitSCM.class, GitRepositoryBrowser.class);
            load();
        }

        public String getDisplayName() {
            return "Git";
        }

        public List<BuildChooserDescriptor> getBuildChooserDescriptors() {
            return BuildChooser.all();
        }

        /**
         * Lists available toolinstallations.
         * @return  list of available git tools
         */
        public List<GitTool> getGitTools() {
            GitTool[] gitToolInstallations = Hudson.getInstance().getDescriptorByType(GitTool.DescriptorImpl.class).getInstallations();
            return Arrays.asList(gitToolInstallations);
        }

        /**
         * Path to git executable.
         * @deprecated
         * @see GitTool
         */
        @Deprecated
        public String getGitExe() {
            return gitExe;
        }

        /**
         * Global setting to be used in call to "git config user.name".
         */
        public String getGlobalConfigName() {
            return globalConfigName;
        }

        public void setGlobalConfigName(String globalConfigName) {
            this.globalConfigName = globalConfigName;
        }

        /**
         * Global setting to be used in call to "git config user.email".
         */
        public String getGlobalConfigEmail() {
            return globalConfigEmail;
        }

        public void setGlobalConfigEmail(String globalConfigEmail) {
            this.globalConfigEmail = globalConfigEmail;
        }

        /**
         * Old configuration of git executable - exposed so that we can
         * migrate this setting to GitTool without deprecation warnings.
         */
        public String getOldGitExe() {
            return gitExe;
        }

        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
            /*
            List<RemoteConfig> remoteRepositories;
            try {
            remoteRepositories = createRepositoryConfigurations(req.getParameterValues("git.repo.url"),
            req.getParameterValues("git.repo.name"),
            req.getParameterValues("git.repo.refspec"));
            }
            catch (IOException e1) {
            throw new GitException("Error creating repositories", e1);
            }
            List<BranchSpec> branches = createBranches(req.getParameterValues("git.branch"));

            // Make up a repo config from the request parameters

            PreBuildMergeOptions mergeOptions = createMergeOptions(req.getParameter("git.doMerge"),
            req.getParameter("git.mergeRemote"), req.getParameter("git.mergeTarget"),
            remoteRepositories);


            String[] urls = req.getParameterValues("git.repo.url");
            String[] names = req.getParameterValues("git.repo.name");
            Collection<SubmoduleConfig> submoduleCfg = new ArrayList<SubmoduleConfig>();

            final GitRepositoryBrowser gitBrowser = getBrowserFromRequest(req, formData);
            String gitTool = req.getParameter("git.gitTool");
            return new GitSCM(
            remoteRepositories,
            branches,
            mergeOptions,
            req.getParameter("git.generate") != null,
            submoduleCfg,
            req.getParameter("git.clean") != null,
            req.getParameter("git.wipeOutWorkspace") != null,
            req.bindJSON(BuildChooser.class,formData.getJSONObject("buildChooser")),
            gitBrowser,
            gitTool,
            req.getParameter("git.authorOrCommitter") != null,
            req.getParameter("git.relativeTargetDir"),
            req.getParameter("git.excludedRegions"),
            req.getParameter("git.excludedUsers"),
            req.getParameter("git.localBranch"),
            req.getParameter("git.recursiveSubmodules") != null,
            req.getParameter("git.pruneBranches") != null,
            req.getParameter("git.gitConfigName"),
            req.getParameter("git.gitConfigEmail"),
            req.getParameter("git.skipTag") != null);
             */
        }

        /**
         * Determine the browser from the scmData contained in the {@link StaplerRequest}.
         *
         * @param scmData
         * @return
         */
        private GitRepositoryBrowser getBrowserFromRequest(final StaplerRequest req, final JSONObject scmData) {
            if (scmData.containsKey("browser")) {
                return req.bindJSON(GitRepositoryBrowser.class, scmData.getJSONObject("browser"));
            } else {
                return null;
            }
        }

        public static List<RemoteConfig> createRepositoryConfigurations(String[] pUrls,
                String[] repoNames,
                String[] refSpecs) throws IOException {

            List<RemoteConfig> remoteRepositories;
            Config repoConfig = new Config();
            // Make up a repo config from the request parameters

            String[] urls = pUrls;
            String[] names = repoNames;

            names = GitUtils.fixupNames(names, urls);

            String[] refs = refSpecs;
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    String name = names[i];
                    name = name.replace(' ', '_');

                    if (refs[i] == null || refs[i].length() == 0) {
                        refs[i] = "+refs/heads/*:refs/remotes/" + name + "/*";
                    }

                    repoConfig.setString("remote", name, "url", urls[i]);
                    repoConfig.setString("remote", name, "fetch", refs[i]);
                }
            }

            try {
                remoteRepositories = RemoteConfig.getAllRemoteConfigs(repoConfig);
            } catch (Exception e) {
                throw new GitException("Error creating repositories", e);
            }
            return remoteRepositories;
        }

        public static PreBuildMergeOptions createMergeOptions(UserMergeOptions mergeOptionsBean,
                List<RemoteConfig> remoteRepositories)
                throws FormException {
            PreBuildMergeOptions mergeOptions = new PreBuildMergeOptions();
            if (mergeOptionsBean != null) {
                RemoteConfig mergeRemote = null;
                String mergeRemoteName = mergeOptionsBean.getMergeRemote().trim();
                if (mergeRemoteName.length() == 0) {
                    mergeRemote = remoteRepositories.get(0);
                } else {
                    for (RemoteConfig remote : remoteRepositories) {
                        if (remote.getName().equals(mergeRemoteName)) {
                            mergeRemote = remote;
                            break;
                        }
                    }
                }
                if (mergeRemote == null) {
                    throw new FormException("No remote repository configured with name '" + mergeRemoteName + "'", "git.mergeRemote");
                }
                mergeOptions.setMergeRemote(mergeRemote);

                mergeOptions.setMergeTarget(mergeOptionsBean.getMergeTarget());
            }

            return mergeOptions;
        }

        public static GitWeb createGitWeb(String url) {
            GitWeb gitWeb = null;
            String gitWebUrl = url;
            if (gitWebUrl != null && gitWebUrl.length() > 0) {
                try {
                    gitWeb = new GitWeb(gitWebUrl);
                } catch (MalformedURLException e) {
                    throw new GitException("Error creating GitWeb", e);
                }
            }
            return gitWeb;
        }

        public FormValidation doGitRemoteNameCheck(StaplerRequest req, StaplerResponse rsp)
                throws IOException, ServletException {
            String mergeRemoteName = req.getParameter("value");
            boolean isMerge = req.getParameter("isMerge") != null;

            // Added isMerge because we don't want to allow empty remote names for tag/branch pushes.
            if (mergeRemoteName.length() == 0 && isMerge) {
                return FormValidation.ok();
            }

            String[] urls = req.getParameterValues("repo.url");
            String[] names = req.getParameterValues("repo.name");
            if (urls != null && names != null)
                for (String name : GitUtils.fixupNames(names, urls))
                    if (name.equals(mergeRemoteName))
                        return FormValidation.ok();

            return FormValidation.error("No remote repository configured with name '" + mergeRemoteName + "'");
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }
    }
    private static final long serialVersionUID = 1L;

    public boolean getDisableSubmodules() {
        return this.disableSubmodules;
    }

    public boolean getRecursiveSubmodules() {
        return this.recursiveSubmodules;
    }

    public boolean getDoGenerate() {
        return this.doGenerateSubmoduleConfigurations;
    }

    @Exported
    public List<BranchSpec> getBranches() {
        return branches;
    }

    @Exported
    public PreBuildMergeOptions getMergeOptions() {
        return mergeOptions;
    }

    public UserMergeOptions getUserMergeOptions() {
        return userMergeOptions;
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
            List<BuildData> buildDataList = build.getActions(BuildData.class);
            for (BuildData bd : buildDataList) {
                if (bd != null && sameScm(bd.getScmName(), scmName)) {
                    buildData = bd;
                    break;
                }
            }
            if (buildData != null) {
                break;
            }
            build = build.getPreviousBuild();
        }

        if (buildData == null) {
            return clone ? new BuildData(getScmName()) : null;
        }

        if (clone) {
            return buildData.clone();
        } else {
            return buildData;
        }
    }

    /**
     * @deprecated
     *      Use {@link #workingDirectory(FilePath, EnvVars)}
     */
    protected FilePath workingDirectory(final FilePath workspace) {
        return workingDirectory(workspace,null);
    }
    
    /**
     * Given the workspace, gets the working directory, which will be the workspace
     * if no relative target dir is specified. Otherwise, it'll be "workspace/relativeTargetDir".
     *
     * @param workspace
     * @return working directory or null if workspace is null
     */
    protected FilePath workingDirectory(final FilePath workspace, EnvVars environment) {
        // JENKINS-10880: workspace can be null
        if (workspace == null) {
            return null;
        }
        if (relativeTargetDir == null || relativeTargetDir.length() == 0 || relativeTargetDir.equals(".")) {
            return workspace;
        }
        return workspace.child(environment.expand(relativeTargetDir));
    }

    public String getLocalBranch() {
        return Util.fixEmpty(localBranch);
    }

    public String getParamLocalBranch(AbstractBuild<?, ?> build) {
        String branch = getLocalBranch();
        // substitute build parameters if available
        return getParameterString(branch, build);
    }

    public String getRelativeTargetDir() {
        return relativeTargetDir;
    }

    /**
     * Given a Revision, check whether it matches any exclusion rules.
     *
     * @param git IGitAPI object
     * @param r Revision object
     * @param listener
     * @return true if any exclusion files are matched, false otherwise.
     */
    private boolean isRevExcluded(IGitAPI git, Revision r, TaskListener listener) {
        try {
            List<String> revShow = git.showRevision(r);

            // If the revision info is empty, something went weird, so we'll just
            // return false.
            if (revShow.size() == 0) {
                return false;
            }

            GitChangeSet change = new GitChangeSet(revShow, authorOrCommitter);

            Pattern[] includedPatterns = getIncludedRegionsPatterns();
            Pattern[] excludedPatterns = getExcludedRegionsPatterns();
            Set<String> excludedUsers = getExcludedUsersNormalized();

            String author = change.getAuthorName();
            if (excludedUsers.contains(author)) {
                // If the author is an excluded user, don't count this entry as a change
                listener.getLogger().println("Ignored commit " + r.getSha1String() + ": Found excluded author: " + author);
                return true;
            }

            List<String> paths = new ArrayList<String>(change.getAffectedPaths());
            if (paths.isEmpty()) {
                // If there weren't any changed files here, we're just going to return false.
                return false;
            }

	    // Assemble the list of included paths
            List<String> includedPaths = new ArrayList<String>();
            if (includedPatterns.length > 0) {
                for (String path : paths) {
                    for (Pattern pattern : includedPatterns) {
                        if (pattern.matcher(path).matches()) {
                            includedPaths.add(path);
                            break;
                        }
                    }
                }
            } else {
		includedPaths = paths;
	    }

	    // Assemble the list of excluded paths
            List<String> excludedPaths = new ArrayList<String>();
            if (excludedPatterns.length > 0) {
                for (String path : includedPaths) {
                    for (Pattern pattern : excludedPatterns) {
                        if (pattern.matcher(path).matches()) {
                            excludedPaths.add(path);
                            break;
                        }
                    }
                }
            }

            // If every affected path is excluded, return true.
            if (includedPaths.size() == excludedPaths.size()) {
                listener.getLogger().println("Ignored commit " + r.getSha1String()
                        + ": Found only excluded paths: "
                        + Util.join(excludedPaths, ", "));
                return true;
            }
        } catch (GitException e) {
            // If an error was hit getting the revision info, assume something
            // else entirely is wrong and we don't care, so return false.
            return false;
        }

        // By default, return false.
        return false;
    }

    @Initializer(before = InitMilestone.JOB_LOADED)
    public static void configureXtream() {
        Run.XSTREAM.registerConverter(new ObjectIdConverter());
        Items.XSTREAM.registerConverter(new RemoteConfigConverter(Items.XSTREAM));
        Items.XSTREAM.alias("org.spearce.jgit.transport.RemoteConfig", RemoteConfig.class);
    }

    private static final Logger LOGGER = Logger.getLogger(GitSCM.class.getName());

    /**
     * Set to true to enable more logging to build's {@link TaskListener}.
     * Used by various classes in this package.
     */
    public static boolean VERBOSE = Boolean.getBoolean(GitSCM.class.getName() + ".verbose");
}
