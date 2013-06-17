package hudson.plugins.git;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.*;
import hudson.init.Initializer;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson.MasterComputer;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.*;
import hudson.remoting.Channel;
import hudson.scm.*;
import hudson.triggers.SCMTrigger;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.IOException2;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.ChangelogCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.Writer;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.Util.fixEmptyAndTrim;
import static hudson.init.InitMilestone.JOB_LOADED;
import static hudson.init.InitMilestone.PLUGINS_STARTED;
import static hudson.scm.PollingResult.*;

/**
 * Git SCM.
 *
 * @author Nigel Magnay
 */
public class GitSCM extends GitSCMBackwardCompatibility {

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
    private boolean doGenerateSubmoduleConfigurations;
    private boolean authorOrCommitter;
    private boolean clean;
    private boolean wipeOutWorkspace;
    private boolean remotePoll;
    private boolean ignoreNotifyCommit;
    private boolean useShallowClone;

    private BuildChooser buildChooser;
    public String gitTool = null;
    private GitRepositoryBrowser browser;
    private Collection<SubmoduleConfig> submoduleCfg;
    public static final String GIT_BRANCH = "GIT_BRANCH";
    public static final String GIT_COMMIT = "GIT_COMMIT";
    public static final String GIT_PREVIOUS_COMMIT = "GIT_PREVIOUS_COMMIT";
    private String reference;
    private String scmName;

    /**
     * All the configured extensions attached to this.
     */
    private DescribableList<GitSCMExtension,GitSCMExtensionDescriptor> extensions;

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
                false, new DefaultBuildChooser(), null, null, false,
                null,
                null, false, false, false, null);
    }

//    @Restricted(NoExternalUse.class) // because this keeps changing
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
            String reference,
            String localBranch,
            boolean remotePoll,
            boolean ignoreNotifyCommit,
            boolean useShallowClone,
            List<GitSCMExtension> extensions) {

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
            this.doGenerateSubmoduleConfigurations = doGenerateSubmoduleConfigurations;
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
        this.reference = reference;
        this.ignoreNotifyCommit = ignoreNotifyCommit;
        this.useShallowClone = useShallowClone;
        if (remotePoll
            && (branches.size() != 1
            || branches.get(0).getName().contains("*")
            || userRemoteConfigs.size() != 1
// FIXME:   || (excludedRegions != null && excludedRegions.length() > 0)
            || (submoduleCfg.size() != 0)
// FIXME:   || (excludedUsers != null && excludedUsers.length() > 0)
        )) {
            LOGGER.log(Level.WARNING, "Cannot poll remotely with current configuration.");
            this.remotePoll = false;
        } else {
            this.remotePoll = remotePoll;
        }

        this.extensions = new DescribableList<GitSCMExtension, GitSCMExtensionDescriptor>(Saveable.NOOP,Util.fixNull(extensions));
        buildChooser.gitSCM = this; // set the owner
    }

    /**
     * All the configured extensions attached to this {@link GitSCM}.
     *
     * Going forward this is primarily how we'll support esoteric use cases.
     *
     * @since 1.EXTENSION
     */
    public DescribableList<GitSCMExtension, GitSCMExtensionDescriptor> getExtensions() {
        return extensions;
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

        if (extensions==null)
            extensions = new DescribableList<GitSCMExtension, GitSCMExtensionDescriptor>(Saveable.NOOP);

        readBackExtensionsFromLegacy();
        return this;
    }

    @Override
    public GitRepositoryBrowser getBrowser() {
        return browser;
    }

    public String getReference() {
        return reference;
    }

    public boolean isCreateAccountBasedOnEmail() {
        DescriptorImpl gitDescriptor = getDescriptor();
        return (gitDescriptor != null && gitDescriptor.isCreateAccountBasedOnEmail());
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
            return BUILD_NOW;
        }

        final BuildData buildData = fixNull(getBuildData(lastBuild));
        if (buildData.lastBuild != null) {
            listener.getLogger().println("[poll] Last Built Revision: " + buildData.lastBuild.revision);
        }

        final String singleBranch = getSingleBranch(lastBuild);

        // fast remote polling needs a single branch and an existing last build
        if (this.remotePoll && singleBranch != null && buildData.lastBuild != null && buildData.lastBuild.getRevision() != null) {
            final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener, false);

            GitClient git = createClient(listener, environment, Jenkins.getInstance(), null);

            String gitRepo = getParamExpandedRepos(lastBuild).get(0).getURIs().get(0).toString();
            ObjectId head = git.getHeadRev(gitRepo, getBranches().get(0).getName());

            if (head != null && buildData.lastBuild.getRevision().getSha1().name().equals(head.name())) {
                return NO_CHANGES;
            } else {
                return BUILD_NOW;
            }
        }

        final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener);

        FilePath workingDirectory = workingDirectory(project,workspace,environment,listener);

        // Rebuild if the working directory doesn't exist
        // I'm actually not 100% sure about this, but I'll leave it in for now.
        // Update 9/9/2010 - actually, I think this *was* needed, since we weren't doing a better check
        // for whether we'd ever been built before. But I'm fixing that right now anyway.
        
        // JENKINS-10880: workingDirectory can be null
        if (workingDirectory == null || !workingDirectory.exists()) {
            return BUILD_NOW;
        }


        // which node is this workspace from?
        Node n = Jenkins.getInstance();
        if (workspace.isRemote()) {
            // there should be always one match, but just in case we initialize n to a non-null value
            for (Computer c : Jenkins.getInstance().getComputers()) {
                if (c.getChannel()==workspace.getChannel()) {
                    n =  c.getNode();
                    break;
                }
            }
        }

        GitClient git = createClient(listener, environment, n, workingDirectory);

        if (git.hasGitRepo()) {
            // Repo is there - do a fetch
            listener.getLogger().println("Fetching changes from the remote Git repositories");

            // Fetch updates
            for (RemoteConfig remoteRepository : getParamExpandedRepos(lastBuild)) {
                fetchFrom(git, listener, remoteRepository);
            }

            listener.getLogger().println("Polling for changes in");

            Collection<Revision> candidates = buildChooser.getCandidateRevisions(
                    true, singleBranch, git, listener, buildData, new BuildChooserContextImpl(project,null));

            for (Revision c : candidates) {
                if (!isRevExcluded(git, c, listener, buildData)) {
                    return PollingResult.SIGNIFICANT;
                }
            }

            return NO_CHANGES;
        } else {
            listener.getLogger().println("No Git repository yet, an initial checkout is required");
            return PollingResult.SIGNIFICANT;
        }
    }

    /*package*/ GitClient createClient(BuildListener listener, EnvVars environment, AbstractBuild<?,?> build) throws IOException, InterruptedException {
        FilePath ws = workingDirectory(build.getProject(),build.getWorkspace(), environment, listener);
        ws.mkdirs(); // ensure it exists
        return createClient(listener,environment,build.getBuiltOn(),ws);
    }

    /*package*/ GitClient createClient(TaskListener listener, EnvVars environment, Node n, FilePath ws) throws IOException, InterruptedException {
        String gitExe = getGitExe(n, listener);
        Git git = Git.with(listener, environment).in(ws).using(gitExe);

        GitClient c = git.getClient();
        for (GitSCMExtension ext : extensions) {
            c = ext.decorate(this,c);
        }
        return c;
    }

    private BuildData fixNull(BuildData bd) {
        return bd != null ? bd : new BuildData(getScmName(), getUserRemoteConfigs()) /*dummy*/;
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
            RemoteConfig remoteRepository) {
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

    /**
     * Determines the commit to be built in this round, updating the working tree accordingly,
     * and return the information about the selected commit.
     *
     * <p>
     * For robustness, this method shouldn't assume too much about the state of the working tree when this method
     * is called. In a general case, a working tree is a left-over from the previous build, so it can be quite
     * messed up (such as HEAD pointing to a random branch.) It is expected that this method brings it back
     * to the predictable clean state by the time this method returns.
     */
    private @NonNull Revision  determineRevisionToBuild(final AbstractBuild build,
                                              final BuildData buildData,
                                              final EnvVars environment,
                                              final GitClient git,
                                              final BuildListener listener) throws IOException, InterruptedException {
        Revision parentLastBuiltRev = null;

        if (build instanceof MatrixRun) {
            MatrixBuild parentBuild = ((MatrixRun) build).getParentBuild();
            if (parentBuild != null) {
                BuildData parentBuildData = getBuildData(parentBuild);
                if (parentBuildData != null) {
                    parentLastBuiltRev = parentBuildData.getLastBuiltRevision();
                }
            }
        }

        final List<RemoteConfig> repos = getParamExpandedRepos(build);
        final PrintStream log = listener.getLogger();

        if (wipeOutWorkspace) {
            log.println("Wiping out workspace first.");
            git.getWorkTree().deleteContents();
        }

        if (git.hasGitRepo()) {
            // It's an update
            if (repos.size() == 1)
                log.println("Fetching changes from the remote Git repository");
            else
                log.println(MessageFormat.format("Fetching changes from {0} remote Git repositories", repos.size()));

            for (RemoteConfig remoteRepository : repos) {
                try {
                    fetchFrom(git, listener, remoteRepository);
                } catch (GitException e) {
                    throw new IOException2("Failed to fetch from "+remoteRepository.getName(),e);
                }
            }
        } else {
            log.println("Cloning the remote Git repository");
            if(useShallowClone) {
                log.println("Using shallow clone");
            }

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
                    ex.printStackTrace(listener.error("Error cloning remote repo '%s'", rc.getName()));
                    // Failed. Try the next one
                    log.println("Trying next repository");
                }
            }

            if (!successfullyCloned)
                throw new AbortException("Could not clone repository");
        }

        Collection<Revision> candidates;

        if (parentLastBuiltRev != null) {
            candidates = Collections.singleton(parentLastBuiltRev);
        } else {
            final RevisionParameterAction rpa = build.getAction(RevisionParameterAction.class);
            if (rpa != null) {
                candidates = Collections.singleton(rpa.toRevision(git));
            } else {
                final BuildChooserContext context = new BuildChooserContextImpl(build.getProject(), build);
                candidates = buildChooser.getCandidateRevisions(
                        false, environment.expand( getSingleBranch(build) ), git, listener, buildData, context);
            }
        }

        if (candidates.size() == 0) {
            // getBuildCandidates should make the last item the last build, so a re-build
            // will build the last built thing.
            throw new AbortException("Couldn't find any revision to build. Verify the repository and branch configuration for this job.");
        }

        if (candidates.size() > 1) {
            log.println("Multiple candidate revisions");
            AbstractProject<?, ?> project = build.getProject();
            if (!project.isDisabled()) {
                log.println("Scheduling another build to catch up with " + project.getFullDisplayName());
                if (!project.scheduleBuild(0, new SCMTrigger.SCMTriggerCause())) {
                    log.println("WARNING: multiple candidate revisions, but unable to schedule build of " + project.getFullDisplayName());
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

        if (VERBOSE)
            listener.getLogger().println("Using strategy: " + buildChooser.getDisplayName());

        final BuildData buildData = copyBuildData(build.getPreviousBuild());

        if (VERBOSE && buildData.lastBuild != null) {
            listener.getLogger().println("Last Built Revision: " + buildData.lastBuild.revision);
        }

        final String paramLocalBranch = getParamLocalBranch(build);

        final GitClient git = createClient(listener,environment,build);

        final Revision revToBuild = determineRevisionToBuild(build, buildData, environment, git, listener);

        listener.getLogger().println("Commencing build of " + revToBuild);
        environment.put(GIT_COMMIT, revToBuild.getSha1String());
        Branch branch = revToBuild.getBranches().iterator().next();
        environment.put(GIT_BRANCH, branch.getName());

        final String remoteBranchName = getParameterString(mergeOptions.getRemoteBranchName(), build);

        if (clean) {
            listener.getLogger().println("Cleaning workspace");
            git.clean();
            // TODO: revisit how to hand off to SubmoduleOption
            for (GitSCMExtension ext : extensions) {
                ext.onClean(this, git);
            }
        }

        if (mergeOptions.doMerge() && !revToBuild.containsBranchName(remoteBranchName)) {
            // Do we need to merge this revision onto MergeTarget

            // Only merge if there's a branch to merge that isn't us..
            listener.getLogger().println("Merging " + revToBuild + " onto " + getParameterString(mergeOptions.getMergeTarget(), build));

            // checkout origin/blah
            ObjectId target = git.revParse(remoteBranchName);

            git.checkoutBranch(paramLocalBranch, remoteBranchName);

            try {
                git.merge(revToBuild.getSha1());
            } catch (GitException ex) {
                // We still need to tag something to prevent
                // repetitive builds from happening - tag the
                // candidate branch.
                git.checkoutBranch(paramLocalBranch, revToBuild.getSha1String());

                // return a failed build, so that it can be properly registered before throwing (else serialization error on slave)
                buildData.saveBuild(new Build(revToBuild, build.getNumber(), Result.FAILURE));
                build.addAction(buildData);
                throw new AbortException("Branch not suitable for integration as it does not merge cleanly");
            }

            build.addAction(new MergeRecord(remoteBranchName,target.getName()));
        } else {
            // No merge
            // Straight compile-the-branch
            listener.getLogger().println("Checking out " + revToBuild);

            git.checkoutBranch(paramLocalBranch, revToBuild.getSha1String());

        }

        buildData.saveBuild(new Build(revToBuild, build.getNumber(), null));
        build.addAction(buildData);
        build.addAction(new GitTagAction(build, buildData));

        computeChangeLog(git, revToBuild, listener, buildData, changelogFile,
                new BuildChooserContextImpl(build.getProject(),build));

        for (GitSCMExtension ext : extensions) {
            ext.onCheckoutCompleted(this, build,launcher,git,listener);
        }

        return true;
    }

    /**
     * Build up change log from all the branches that we've merged into {@code revToBuild}.
     *
     * <p>
     * Intuitively, a changelog is a list of commits that's added since the "previous build" to the current build.
     * However, because of the multiple branch support in Git, this notion is ambiguous. For example, consider the
     * following commit graph where M1...M4 belongs to branch M, B1..B2 belongs to branch B, and so on:
     *
     * <pre>
     *    M1 -> M2 -> M3 -> M4
     *  /   \     \     \
     * S ->  B1 -> B2    \
     *  \                 \
     *   C1 ---------------> C2
     * </pre>
     *
     * <p>
     * If Jenkin built B1, C1, B2, C3 in that order, then one'd prefer that the changelog of B2 only shows
     * just B1..B2, not C1..B2. To do this, we attribute every build to specific branches, and when we say
     * "since the previous build", what we really mean is "since the last build that built the same branch".
     *
     * <p>
     * TODO: if a branch merge is configured, then the first build will end up listing all the changes
     * in the upstream branch, which may be too many. To deal with this nicely, BuildData needs to remember
     * when we started merging this branch so that we can properly detect if the current build is the
     * first build that's merging a new branch.
     *
     * Another possibly sensible option is to always exclude all the commits that are happening in the remote branch.
     * Picture yourself developing a feature branch that closely tracks a busy mainline, then you might
     * not really care the changes going on in the main line. In this way, the changelog only lists your changes,
     * so "notify those who break the build" will not spam upstream developers, too.
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
        Writer out = new OutputStreamWriter(changelogFile.write(),"UTF-8");

        ChangelogCommand changelog = git.changelog();
        changelog.includes(revToBuild.getSha1());
        try {
            boolean exclusion = false;
            for (Branch b : revToBuild.getBranches()) {
                Build lastRevWas = buildChooser.prevBuildForChangelog(b.getName(), buildData, git, context);
                if (lastRevWas != null && git.isCommitInRepo(lastRevWas.getSHA1())) {
                    changelog.excludes(lastRevWas.getSHA1());
                    exclusion = true;
                }
            }
            if (!exclusion) {
                // this is the first time we are building this branch, so there's no base line to compare against.
                // if we force the changelog, it'll contain all the changes in the repo, which is not what we want.
                listener.getLogger().println("First time build. Skipping changelog.");
            } else {
                changelog.to(out).max(MAX_CHANGELOG).execute();
            }
        } catch (GitException ge) {
            ge.printStackTrace(listener.error("Unable to retrieve changeset"));
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, java.util.Map<String, String> env) {
        super.buildEnvVars(build, env);
        Revision rev = fixNull(getBuildData(build)).getLastBuiltRevision();
        String singleBranch = getSingleBranch(build);
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

        getDescriptor().populateEnvironmentVariables(env);
        for (GitSCMExtension ext : extensions) {
            ext.populateEnvironmentVariables(this, env);
        }
    }

    private String getLastBuiltCommitOfBranch(AbstractBuild<?, ?> build, Branch branch) {
        String prevCommit = null;
        if (build.getPreviousBuiltBuild() != null) {
            final Build lastBuildOfBranch = fixNull(getBuildData(build.getPreviousBuiltBuild())).getLastBuildOfBranch(branch.getName());
            if (lastBuildOfBranch != null) {
                Revision previousRev = lastBuildOfBranch.getRevision();
                if (previousRev != null) {
                    prevCommit = previousRev.getSha1String();
                }
            }
        }
        return prevCommit;
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
            return fixEmptyAndTrim(globalConfigName);
        }

        public void setGlobalConfigName(String globalConfigName) {
            this.globalConfigName = globalConfigName;
        }

        /**
         * Global setting to be used in call to "git config user.email".
         */
        public String getGlobalConfigEmail() {
            return fixEmptyAndTrim(globalConfigEmail);
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

        public FormValidation doGitRemoteNameCheck(StaplerRequest req)
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

        /**
         * Fill in the environment variables for launching git
         */
        public void populateEnvironmentVariables(Map<String,String> env) {
            String name = getGlobalConfigName();
            if (name!=null) {
                env.put("GIT_COMMITTER_NAME", name);
                env.put("GIT_AUTHOR_NAME", name);
            }
            String email = getGlobalConfigEmail();
            if (email!=null) {
                env.put("GIT_COMMITTER_EMAIL", email);
                env.put("GIT_AUTHOR_EMAIL", email);
            }
        }
    }
    private static final long serialVersionUID = 1L;

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
     * @deprecated
     */
    public BuildData getBuildData(Run build, boolean clone) {
        return clone ? copyBuildData(build) : getBuildData(build);
    }

    /**
     * Like {@link #getBuildData(Run)}, but copy the data into a new object,
     * which is used as the first step for updating the data for the next build.
     */
    public BuildData copyBuildData(Run build) {
        BuildData base = getBuildData(build);
        if (base==null)
            return new BuildData(getScmName(), getUserRemoteConfigs());
        else
            return base.clone();
    }

    /**
     * Find the build log (BuildData) recorded with the last build that completed. BuildData
     * may not be recorded if an exception occurs in the plugin logic.
     *
     * @param build
     * @return the last recorded build data
     */
    public @CheckForNull BuildData getBuildData(Run build) {
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

        return buildData;
    }

    /**
     * Given the workspace, gets the working directory, which will be the workspace
     * if no relative target dir is specified. Otherwise, it'll be "workspace/relativeTargetDir".
     *
     * @param workspace
     * @return working directory or null if workspace is null
     */
    protected FilePath workingDirectory(AbstractProject<?,?> context, FilePath workspace, EnvVars environment, TaskListener listener) throws IOException, InterruptedException {
        // JENKINS-10880: workspace can be null
        if (workspace == null) {
            return null;
        }

        for (GitSCMExtension ext : extensions) {
            FilePath r = ext.getWorkingDirectory(this, context, workspace, environment, listener);
            if (r!=null)    return r;
        }
        return workspace;
    }

    public String getLocalBranch() {
        return Util.fixEmpty(localBranch);
    }

    public String getParamLocalBranch(AbstractBuild<?, ?> build) {
        String branch = getLocalBranch();
        // substitute build parameters if available
        return getParameterString(branch, build);
    }

    /**
     * Given a Revision, check whether it matches any exclusion rules.
     *
     * @param git GitClient object
     * @param r Revision object
     * @param listener
     * @return true if any exclusion files are matched, false otherwise.
     */
    private boolean isRevExcluded(GitClient git, Revision r, TaskListener listener, BuildData buildData) throws IOException, InterruptedException {
        try {
            List<String> revShow;
            if (buildData != null && buildData.lastBuild != null) {
                revShow  = git.showRevision(buildData.lastBuild.revision.getSha1(), r.getSha1());
            } else {
                revShow  = git.showRevision(r.getSha1());
            }

            // If the revision info is empty, something went weird, so we'll just bail out
            if (revShow.isEmpty()) {
                return false;
            }

            if (!extensions.isEmpty()) {
                GitChangeSet change = new GitChangeSet(revShow, authorOrCommitter);

                for (GitSCMExtension ext : extensions) {
                    Boolean b = ext.isRevExcluded(this, git, change, listener, buildData);
                    if (b!=null)
                        return b;
                }
            }

            return false;
        } catch (GitException e) {
            e.printStackTrace(listener.error("Failed to determine if we want to exclude " + r.getSha1String()));
            return false;   // for historical reason this is not considered a fatal error.
        }
    }


    @Initializer(after=PLUGINS_STARTED)
    public static void onLoaded() {
        DescriptorImpl desc = Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);

        if (desc.getOldGitExe() != null) {
            String exe = desc.getOldGitExe();
            String defaultGit = GitTool.getDefaultInstallation().getGitExe();
            if (defaultGit.equals(exe)) {
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

    /**
     * To avoid pointlessly large changelog, we'll limit the number of changes up to this.
     */
    public static final int MAX_CHANGELOG = Integer.getInteger(GitSCM.class.getName()+".maxChangelog",1024);
}
