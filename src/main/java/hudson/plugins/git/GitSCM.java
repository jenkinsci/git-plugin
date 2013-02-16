package hudson.plugins.git;

import hudson.*;
import hudson.FilePath.FileCallable;
import hudson.init.Initializer;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson.MasterComputer;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.*;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.*;
import hudson.triggers.SCMTrigger;
import hudson.util.FormValidation;
import hudson.util.IOException2;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static hudson.Util.fixEmptyAndTrim;
import static hudson.init.InitMilestone.JOB_LOADED;
import static hudson.init.InitMilestone.PLUGINS_STARTED;

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
    private boolean useShallowClone;

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
    public static final String GIT_PREVIOUS_COMMIT = "GIT_PREVIOUS_COMMIT";
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
                null, null, null, false, false, false, false, null, null, false, null, false, false);
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
            boolean ignoreNotifyCommit,
            boolean useShallowClone) {

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
        this.useShallowClone = useShallowClone;
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

    public boolean isCreateAccountBasedOnEmail() {
        DescriptorImpl gitDescriptor = ((DescriptorImpl) getDescriptor());
        return (gitDescriptor != null && gitDescriptor.isCreateAccountBasedOnEmail());
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
    
    public boolean getUseShallowClone() {
    	return useShallowClone;
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
    public List<RemoteConfig> getParamExpandedRepos(AbstractBuild<?, ?> build) throws IOException, InterruptedException {
        List<RemoteConfig> expandedRepos = new ArrayList<RemoteConfig>();

        EnvVars env = build.getEnvironment();

        for (RemoteConfig oldRepo : Util.fixNull(remoteRepositories)) {
            expandedRepos.add(newRemoteConfig(getParameterString(oldRepo.getName(), env),
                    getParameterString(oldRepo.getURIs().get(0).toPrivateString(), env),
                    new RefSpec(getRefSpec(oldRepo, env))));
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

    private String getParameterString(String original, EnvVars env) {
        return env.expand(original);
    }

    private String getRefSpec(RemoteConfig repo, EnvVars env) {
        String refSpec = repo.getFetchRefSpecs().get(0).toString();

        return getParameterString(refSpec, env);
    }

    /**
     * If the configuration is such that we are tracking just one branch of one repository
     * return that branch specifier (in the form of something like "origin/master"
     *
     * Otherwise return null.
     */
    private String getSingleBranch(EnvVars env) {
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
        branch = getParameterString(branch, env);

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
        return !remotePoll;
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, final TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        try {
            return compareRemoteRevisionWithImpl( project, launcher, workspace, listener, baseline);
        } catch (GitException e){
            throw new IOException2(e);
        }
    }

    private PollingResult compareRemoteRevisionWithImpl(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, final TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        // Poll for changes. Are there any unbuilt revisions that Hudson ought to build ?

        listener.getLogger().println("Using strategy: " + buildChooser.getDisplayName());

        final AbstractBuild lastBuild = project.getLastBuild();
        if (lastBuild == null) {
            // If we've never been built before, well, gotta build!
            listener.getLogger().println("[poll] No previous build, so forcing an initial build.");
            return PollingResult.BUILD_NOW;
        }

        final BuildData buildData = fixNull(getBuildData(lastBuild, false));
        if (buildData.lastBuild != null) {
            listener.getLogger().println("[poll] Last Built Revision: " + buildData.lastBuild.revision);
        }

        final String singleBranch = getSingleBranch(lastBuild.getEnvironment());

        // fast remote polling needs a single branch and an existing last build
        if (this.remotePoll && singleBranch != null && buildData.lastBuild != null && buildData.lastBuild.getRevision() != null) {
            final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener, false);

            GitClient git = Git.with(listener, environment)
                               .using(getGitExe(Jenkins.getInstance(), environment, listener))
                               .getClient();

            String gitRepo = getParamExpandedRepos(lastBuild).get(0).getURIs().get(0).toString();
            ObjectId head = git.getHeadRev(gitRepo, getBranches().get(0).getName());

            if (head != null && buildData.lastBuild.getRevision().getSha1().name().equals(head.name())) {
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

                GitClient git = Git.with(listener, environment)
                        .in(localWorkspace)
                        .using(gitExe)
                        .getClient();

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
                        if (!isRevExcluded(git, c, listener, buildData)) {
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
        return bd != null ? bd : new BuildData(getScmName(), getUserRemoteConfigs()) /*dummy*/;
    }

    private void cleanSubmodules(GitClient parentGit,
            File workspace,
            TaskListener listener,
            RemoteConfig remoteRepository) throws InterruptedException {

        List<IndexEntry> submodules = parentGit.getSubmodules("HEAD");

        for (IndexEntry submodule : submodules) {
            String subdir = submodule.getFile();
            try {
                listener.getLogger().println("Trying to clean submodule in " + subdir);
                GitClient subGit = parentGit.subGit(subdir);
                subGit.clean();
            } catch (Exception ex) {
                listener.getLogger().println(
                        "Problem cleaning submodule in "
                        + subdir
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
    private boolean fetchFrom(GitClient git,
            TaskListener listener,
            RemoteConfig remoteRepository) throws InterruptedException {
        String name = remoteRepository.getName();
        try {
            // Assume there is only 1 URL / refspec for simplicity
            String url = remoteRepository.getURIs().get(0).toPrivateString();
            git.setRemoteUrl(name, url);
            git.fetch(name, remoteRepository.getFetchRefSpecs().get(0));
            return true;
        } catch (GitException ex) {
            ex.printStackTrace(listener.error(
                    "Problem fetching from " + name
                    + " / " + name
                    + " - could be unavailable. Continuing anyway"));
        }
        return false;
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

    public GitTool resolveGitTool(TaskListener listener) {
        if (gitTool == null) return GitTool.getDefaultInstallation();
        GitTool git =  Hudson.getInstance().getDescriptorByType(GitTool.DescriptorImpl.class).getInstallation(gitTool);
        if (git == null) {
            listener.getLogger().println("selected Git installation does not exists. Using Default");
            git = GitTool.getDefaultInstallation();
        }
        return git;
    }

    public String getGitExe(Node builtOn, TaskListener listener) {
        return getGitExe(builtOn, null, listener);
    }

    /**
     * Exposing so that we can get this from GitPublisher.
     */
    public String getGitExe(Node builtOn, EnvVars env, TaskListener listener) {
        GitTool tool = resolveGitTool(listener);
        if (builtOn != null) {
            try {
                tool = tool.forNode(builtOn, listener);
            } catch (IOException e) {
                listener.getLogger().println("Failed to get git executable");
            } catch (InterruptedException e) {
                listener.getLogger().println("Failed to get git executable");
            }
        }
        if (env != null) {
            tool = tool.forEnvironment(env);
        }

        return tool.getGitExe();
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
                if (lb.isFor(sha1)) return b;
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

        public AbstractBuild<?, ?> getBuild() {
            return build;
        }

        private Object writeReplace() {
            return Channel.current().export(BuildChooserContext.class,new BuildChooserContext() {
                public <T> T actOnBuild(ContextCallable<AbstractBuild<?,?>, T> callable) throws IOException, InterruptedException {
                    return callable.invoke(build,Channel.current());
                }

                public <T> T actOnProject(ContextCallable<AbstractProject<?,?>, T> callable) throws IOException, InterruptedException {
                    return callable.invoke(project,Channel.current());
                }

                public AbstractBuild<?, ?> getBuild() {
                    return build;
                }
            });
        }
    }

    private Revision determineRevisionToBuild(final AbstractBuild build,
                                              final BuildData buildData,
                                              final List<RemoteConfig> repos,
                                              final FilePath workingDirectory,
                                              final EnvVars environment,
                                              final String gitExe,
                                              final BuildListener listener) throws IOException, InterruptedException {
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

        final Revision parentLastBuiltRev = tempParentLastBuiltRev;

        final String singleBranch = environment.expand( getSingleBranch(environment) );

        final RevisionParameterAction rpa = build.getAction(RevisionParameterAction.class);
        final BuildChooserContext context = new BuildChooserContextImpl(build.getProject(), build);

        final PrintStream logger = listener.getLogger();
        if(useShallowClone) {
        //	if(build.getProject().getPublishersList().get(GitPublisher.class) == null) {
        		logger.println("Using shallow clone");
        //	} else {
        //		useShallowClone = false;
        //	}
        }

        Collection<Revision> candidates = workingDirectory.act(new FileCallable<Collection<Revision>>() {

            private static final long serialVersionUID = 1L;

            public Collection<Revision> invoke(File localWorkspace, VirtualChannel channel)
                    throws IOException, InterruptedException {
                FilePath ws = new FilePath(localWorkspace);
                final PrintStream log = listener.getLogger();
                GitClient git = Git.with(listener, environment)
                        .in(localWorkspace)
                        .using(gitExe)
                        .getClient();

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

                    if (repos.size() == 1)
                        log.println("Fetching changes from 1 remote Git repository");
                    else
                        log.println(MessageFormat
                                .format("Fetching changes from {0} remote Git repositories",
                                        repos.size()));

                    boolean fetched = false;
                    for (RemoteConfig remoteRepository : repos) {
                        try {
                            fetched |= fetchFrom(git, listener, remoteRepository);
                        } catch (GitException e) {
                            fetched |= false;
                        }
                    }

                    if (!fetched) {
                        listener.error("Could not fetch from any repository");
                        // Throw IOException so the retry will be able to catch it
                        throw new IOException("Could not fetch from any repository");
                    }
                    // Do we want to prune first?
                    if (pruneBranches) {
                        log.println("Pruning obsolete local branches");
                        for (RemoteConfig remoteRepository : repos) {
                            git.prune(remoteRepository);
                        }
                    }

                } else {

                    log.println("Cloning the remote Git repository");
                    
                    // Go through the repositories, trying to clone from one
                    //
                    boolean successfullyCloned = false;
                    for (RemoteConfig rc : repos) {
                    	final String expandedReference = environment.expand(reference);
                        try {
                            git.clone(rc.getURIs().get(0).toPrivateString(), rc.getName(), useShallowClone, expandedReference);
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
                        // Throw IOException so the retry will be able to catch it
                        throw new IOException("Could not clone");
                    }

                    boolean fetched = false;
                    for (RemoteConfig remoteRepository : repos) {
                        fetched |= fetchFrom(git, listener, remoteRepository);
                    }

                    if (!fetched) {
                        listener.error("Could not fetch from any repository");
                        // Throw IOException so the retry will be able to catch it
                        throw new IOException("Could not fetch from any repository");
                    }

                    if (clean) {
                        log.println("Cleaning workspace");
                        git.clean();

                        if (!disableSubmodules && git.hasGitModules()) {
                            git.submoduleClean(recursiveSubmodules);
                        }
                    }
                }

                if (parentLastBuiltRev != null) {
                    return Collections.singleton(parentLastBuiltRev);
                }

                if (rpa != null) {
                    return Collections.singleton(rpa.toRevision(git));
                }

                return buildChooser.getCandidateRevisions(
                        false, singleBranch, git, listener, buildData, context);

            }
        });

        if (candidates.size() == 0) {
            logger.println("No candidate revisions");
            return null;
        }
        if (candidates.size() > 1) {
            logger.println("Multiple candidate revisions");
            AbstractProject<?, ?> project = build.getProject();
            if (!project.isDisabled()) {
                logger.println("Scheduling another build to catch up with " + project.getFullDisplayName());
                if (!project.scheduleBuild(0, new SCMTrigger.SCMTriggerCause())) {
                    logger.println("WARNING: multiple candidate revisions, but unable to schedule build of " + project.getFullDisplayName());
                }
            }
        }
        return candidates.iterator().next();
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

        final String buildnumber = "jenkins-" + projectName.replace(" ", "_") + "-" + buildNumber;

        final BuildData buildData = getBuildData(build.getPreviousBuild(), true);

        if (buildData.lastBuild != null) {
            listener.getLogger().println("Last Built Revision: " + buildData.lastBuild.revision);
        }

        final String paramLocalBranch = getParamLocalBranch(build);
        final List<RemoteConfig> paramRepos = getParamExpandedRepos(build);

        final Revision revToBuild = determineRevisionToBuild(build, buildData, paramRepos, workingDirectory,
                environment, gitExe, listener);

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

        final BuildChooserContext context = new BuildChooserContextImpl(build.getProject(),build);

        final String remoteBranchName = getParameterString(mergeOptions.getRemoteBranchName(), environment);
        final String mergeTarget = getParameterString(mergeOptions.getMergeTarget(), environment);

        Build returnedBuildData;
        if (mergeOptions.doMerge() && !revToBuild.containsBranchName(remoteBranchName)) {
            returnedBuildData = workingDirectory.act(new FileCallable<Build>() {

                private static final long serialVersionUID = 1L;

                public Build invoke(File localWorkspace, VirtualChannel channel)
                        throws IOException, InterruptedException {

                    GitClient git = Git.with(listener, environment)
                            .in(localWorkspace)
                            .using(gitExe)
                            .getClient();

                    // Do we need to merge this revision onto MergeTarget

                    // Only merge if there's a branch to merge that isn't us..
                    listener.getLogger().println("Merging " + revToBuild + " onto " + mergeTarget);

                    // checkout origin/blah
                    ObjectId target = git.revParse(remoteBranchName);

                    git.checkoutBranch(paramLocalBranch, remoteBranchName);

                    try {
                        git.merge(revToBuild.getSha1());
                    } catch (Exception ex) {
                        // We still need to tag something to prevent
                        // repetitive builds from happening - tag the
                        // candidate branch.
                        git.checkoutBranch(paramLocalBranch, revToBuild.getSha1String());

                        if (!getSkipTag()) {
                            git.tag(buildnumber, "Jenkins Build #"
                                    + buildNumber);
                        }

                        // return a failed build, so that it can be properly registered before throwing (else serialization error on slave)
                        return new Build(revToBuild, buildNumber, Result.FAILURE);
                    }

                    if (!disableSubmodules && git.hasGitModules()) {
                        // This ensures we don't miss changes to submodule paths and allows
                        // seamless use of bare and non-bare superproject repositories.
                        git.setupSubmoduleUrls(revToBuild, listener);
                        git.submoduleUpdate(recursiveSubmodules);
                    }

                    if (!getSkipTag()) {
                        // Tag the successful merge
                        git.tag(buildnumber, "Jenkins Build #" + buildNumber);
                    }

                    computeMergeChangeLog(git, revToBuild, remoteBranchName, listener, changelogFile);

                    GitUtils gu = new GitUtils(listener, git);
                    Revision mergeRevision = gu.getRevisionForSHA1(target);
                    MergeBuild build = new MergeBuild(revToBuild, buildNumber, mergeRevision, null);

                    if (clean) {
                        listener.getLogger().println("Cleaning workspace");
                        git.clean();
                        if (!disableSubmodules && git.hasGitModules()) {
                            git.submoduleClean(recursiveSubmodules);
                        }
                    }

                    // Fetch the diffs into the changelog file
                    return build;
                }
            });
            if (returnedBuildData.getBuildResult() != null && returnedBuildData.getBuildResult().equals(Result.FAILURE)) {
                buildData.saveBuild(returnedBuildData);
                build.addAction(buildData);
                throw new AbortException("Branch not suitable for integration as it does not merge cleanly");
            }
        } else {
            // No merge
            returnedBuildData = workingDirectory.act(new FileCallable<Build>() {

                private static final long serialVersionUID = 1L;

                public Build invoke(File localWorkspace, VirtualChannel channel)
                        throws IOException, InterruptedException {

                    GitClient git = Git.with(listener, environment)
                            .in(localWorkspace)
                            .using(gitExe)
                            .getClient();

                    // Straight compile-the-branch
                    listener.getLogger().println("Checking out " + revToBuild);

                    if (clean) {
                        listener.getLogger().println("Cleaning workspace");
                        git.clean();

                        if (!disableSubmodules && git.hasGitModules()) {
                            git.submoduleClean(recursiveSubmodules);
                        }
                    }

                    git.checkoutBranch(paramLocalBranch, revToBuild.getSha1String());

                    if (!disableSubmodules && git.hasGitModules()) {
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

                    computeChangeLog(git, revToBuild, listener, buildData,changelogFile, context);

                    return new Build(revToBuild, buildNumber, null);
                }
            });
        }

        buildData.saveBuild(returnedBuildData);
        build.addAction(buildData);
        build.addAction(new GitTagAction(build, buildData));

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
    private void computeChangeLog(GitClient git, Revision revToBuild, BuildListener listener, BuildData buildData, FilePath changelogFile, BuildChooserContext context) throws IOException, InterruptedException {
        int histories = 0;
        PrintStream out = null;
        listener.getLogger().println("Attempting to write changelog to local file " +
                                     changelogFile.getName() + ", remotely " + changelogFile.getRemote());
        try {
            out = new PrintStream(changelogFile.write(), false, "UTF-8");
        } catch (IOException io) {
            listener.getLogger().println("Failed to open changelog file for write");
            return;
        }

        try {
            for (Branch b : revToBuild.getBranches()) {
                Build lastRevWas = buildChooser.prevBuildForChangelog(b.getName(), buildData, git, context);
                if (lastRevWas != null) {
                    if (git.isCommitInRepo(lastRevWas.getSHA1())) {
                        putChangelogDiffs(git, b.getName(), lastRevWas.getSHA1().name(), revToBuild.getSha1().name(), out);
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

    private void computeMergeChangeLog(GitClient git, Revision revToBuild, String remoteBranch, BuildListener listener, FilePath changelogFile) throws IOException, InterruptedException {
        ObjectId objectId = git.getRepository().resolve(remoteBranch);
        if (!git.isCommitInRepo(objectId)) {
            listener.getLogger().println("Could not record history. Previous build's commit, " + remoteBranch
                                         + ", does not exist in the current repository.");
        } else {
            int histories = 0;
            PrintStream out = null;
            listener.getLogger().println("Attempting to write merge changelog to local file " +
                                         changelogFile.getName() + ", remotely " + changelogFile.getRemote());
            try {
                out = new PrintStream(changelogFile.write(), false, "UTF-8");
            } catch (IOException io) {
                listener.getLogger().println("Failed to open changelog file for write");
                return;
            }

            try {
                for (Branch b : revToBuild.getBranches()) {
                    putChangelogDiffs(git, b.getName(), remoteBranch, revToBuild.getSha1().name(), out);
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
        String singleBranch = getSingleBranch(new EnvVars(env));
        if (singleBranch != null){
            env.put(GIT_BRANCH, singleBranch);
        } else if (rev != null) {
            Branch branch = rev.getBranches().iterator().next();
            env.put(GIT_BRANCH, branch.getName());
        }
        if (rev != null) {
            Branch branch = rev.getBranches().iterator().next();
            String prevCommit = getLastBuiltCommitOfBranch(build, branch);
            if (prevCommit != null) {
                env.put(GIT_PREVIOUS_COMMIT, prevCommit);
            }
            String commit = rev.getSha1String();
            if (commit != null) {
                env.put(GIT_COMMIT, commit);
            }
        }
       
      if(userRemoteConfigs.size()==1){
    	  env.put("GIT_URL", userRemoteConfigs.get(0).getUrl());
      }else{
    	  int count=1;
    	  for(UserRemoteConfig config:userRemoteConfigs)   {
      		env.put("GIT_URL_"+count, config.getUrl());
      		count++;
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

    private String getLastBuiltCommitOfBranch(AbstractBuild<?, ?> build, Branch branch) {
        String prevCommit = null;
        if (build.getPreviousBuiltBuild() != null) {
            final Build lastBuildOfBranch = fixNull(getBuildData(build.getPreviousBuiltBuild(), false)).getLastBuildOfBranch(branch.getName());
            if (lastBuildOfBranch != null) {
                Revision previousRev = lastBuildOfBranch.getRevision();
                if (previousRev != null) {
                    prevCommit = previousRev.getSha1String();
                }
            }
        }
        return prevCommit;
    }


    private void putChangelogDiffs(GitClient git, String branchName, String revFrom,
            String revTo, PrintStream fos) throws IOException, InterruptedException {
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
    
    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<GitSCM> {

        private String gitExe;
        private String globalConfigName;
        private String globalConfigEmail;
        private boolean createAccountBasedOnEmail;

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

        public boolean isCreateAccountBasedOnEmail() {
            return createAccountBasedOnEmail;
        }

        public void setCreateAccountBasedOnEmail(boolean createAccountBasedOnEmail) {
            this.createAccountBasedOnEmail = createAccountBasedOnEmail;
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

    private boolean isRelevantBuildData(BuildData bd) {
        for(UserRemoteConfig c : getUserRemoteConfigs()) {
            if(bd.hasBeenReferenced(c.getUrl())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the build log (BuildData) recorded with the last build that completed. BuildData
     * may not be recorded if an exception occurs in the plugin logic.
     *
     * @param build
     * @param clone
     * @return the last recorded build data
     */
    public BuildData getBuildData(Run build, boolean clone) {
        BuildData buildData = null;
        while (build != null) {
            List<BuildData> buildDataList = build.getActions(BuildData.class);
            for (BuildData bd : buildDataList) {
                if (bd != null && isRelevantBuildData(bd)) {
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
            return clone ? new BuildData(getScmName(), getUserRemoteConfigs()) : null;
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

    public String getParamLocalBranch(AbstractBuild<?, ?> build) throws IOException, InterruptedException {
        String branch = getLocalBranch();
        // substitute build parameters if available
        return getParameterString(branch, build.getEnvironment());
    }

    public String getRelativeTargetDir() {
        return relativeTargetDir;
    }

    /**
     * Given a Revision, check whether it matches any exclusion rules.
     *
     * @param git GitClient object
     * @param r Revision object
     * @param listener
     * @return true if any exclusion files are matched, false otherwise.
     */
    private boolean isRevExcluded(GitClient git, Revision r, TaskListener listener, BuildData buildData) throws InterruptedException {
        try {
            Pattern[] includedPatterns = getIncludedRegionsPatterns();
            Pattern[] excludedPatterns = getExcludedRegionsPatterns();
            Set<String> excludedUsers = getExcludedUsersNormalized();

            // If there are no excluded users, no excluded patterns,
            // and no included patterns, then the revision cannot be
            // excluded.
            //
            // Assumes it is cheaper to obtain the excluded patterns,
            // the included patterns and the excluded users than to
            // call git.
            if (includedPatterns.length == 0 && excludedPatterns.length == 0 && excludedUsers.isEmpty()) {
                return false;
            }

            List<String> revShow;
            if (buildData != null && buildData.lastBuild != null) {
                revShow  = git.showRevision(buildData.lastBuild.revision.getSha1(), r.getSha1());
            } else {
                revShow  = git.showRevision(r.getSha1());
            }

            // If the revision info is empty, something went weird, so we'll just
            // return false.
            if (revShow.size() == 0) {
                return false;
            }

            GitChangeSet change = new GitChangeSet(revShow, authorOrCommitter);

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


    @Initializer(after=PLUGINS_STARTED)
    public static void onLoaded() {
        DescriptorImpl desc = Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);

        if (desc.getOldGitExe() != null) {
            String exe = desc.getOldGitExe();
            String defaultGit = GitTool.getDefaultInstallation().getGitExe();
            if (exe.equals(defaultGit)) {
                return;
            }
            System.err.println("[WARNING] you're using deprecated gitexe attribute to configure git plugin. Use Git installations");
        }
    }

    @Initializer(before=JOB_LOADED)
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
