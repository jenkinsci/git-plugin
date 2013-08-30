package hudson.plugins.git;

import com.google.common.collect.Iterables;
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
import hudson.plugins.git.extensions.GitClientConflictException;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.extensions.impl.AuthorInChangelog;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.plugins.git.extensions.impl.RemotePoll;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.*;
import hudson.remoting.Channel;
import hudson.scm.*;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.SCMTrigger;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.IOException2;
import hudson.util.IOUtils;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.ChangelogCommand;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitTool;
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

import static hudson.Util.*;
import static hudson.init.InitMilestone.JOB_LOADED;
import static hudson.init.InitMilestone.PLUGINS_STARTED;
import static hudson.scm.PollingResult.*;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Git SCM.
 *
 * @author Nigel Magnay
 * @author Andrew Bayer
 * @author Nicolas Deloof
 * @author Kohsuke Kawaguchi
 * ... and many others
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
    private boolean doGenerateSubmoduleConfigurations;

    public String gitTool = null;
    private GitRepositoryBrowser browser;
    private Collection<SubmoduleConfig> submoduleCfg;
    public static final String GIT_BRANCH = "GIT_BRANCH";
    public static final String GIT_COMMIT = "GIT_COMMIT";
    public static final String GIT_PREVIOUS_COMMIT = "GIT_PREVIOUS_COMMIT";

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
                createRepoList(repositoryUrl),
                Collections.singletonList(new BranchSpec("")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, null);
    }

//    @Restricted(NoExternalUse.class) // because this keeps changing
    @DataBoundConstructor
    public GitSCM(
            List<UserRemoteConfig> userRemoteConfigs,
            List<BranchSpec> branches,
            Boolean doGenerateSubmoduleConfigurations,
            Collection<SubmoduleConfig> submoduleCfg,
            GitRepositoryBrowser browser,
            String gitTool,
            List<GitSCMExtension> extensions) {

        // moved from createBranches
        if (branches == null) {
            branches = new ArrayList<BranchSpec>();
        }
        if (branches.isEmpty()) {
            branches.add(new BranchSpec("*/master"));
        }
        this.branches = branches;

        this.userRemoteConfigs = userRemoteConfigs;
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

        this.configVersion = 2L;
        this.gitTool = gitTool;

        this.extensions = new DescribableList<GitSCMExtension, GitSCMExtensionDescriptor>(Saveable.NOOP,Util.fixNull(extensions));

        getBuildChooser(); // set the gitSCM field.
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
    }

    public Object readResolve() throws IOException {
        // Migrate data

        // Default unspecified to v0
        if (configVersion == null) {
            configVersion = 0L;
        }


        if (source != null) {
            remoteRepositories = new ArrayList<RemoteConfig>();
            branches = new ArrayList<BranchSpec>();
            doGenerateSubmoduleConfigurations = false;

            List<RefSpec> rs = new ArrayList<RefSpec>();
            rs.add(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
            remoteRepositories.add(newRemoteConfig("origin", source, rs.toArray(new RefSpec[0])));
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
        if (remoteRepositories == null) {
            // if we don't catch GitException here, the whole job fails to load
            try {
                updateFromUserData();
            } catch (GitException e) {
                LOGGER.log(Level.WARNING, "Failed to load SCM data", e);
            }
        }

        if (extensions==null)
            extensions = new DescribableList<GitSCMExtension, GitSCMExtensionDescriptor>(Saveable.NOOP);

        readBackExtensionsFromLegacy();

        if (choosingStrategy != null && getBuildChooser().getClass()==DefaultBuildChooser.class) {
            for (BuildChooserDescriptor d : BuildChooser.all()) {
                if (choosingStrategy.equals(d.getLegacyId())) {
                    try {
                        setBuildChooser(d.clazz.newInstance());
                    } catch (InstantiationException e) {
                        LOGGER.log(Level.WARNING, "Failed to instantiate the build chooser", e);
                    } catch (IllegalAccessException e) {
                        LOGGER.log(Level.WARNING, "Failed to instantiate the build chooser", e);
                    }
                }
            }
        }

        getBuildChooser(); // set the gitSCM field.

        return this;
    }

    @Override
    public GitRepositoryBrowser getBrowser() {
        return browser;
    }

    public boolean isCreateAccountBasedOnEmail() {
        DescriptorImpl gitDescriptor = getDescriptor();
        return (gitDescriptor != null && gitDescriptor.isCreateAccountBasedOnEmail());
    }

    public BuildChooser getBuildChooser() {
        BuildChooser bc;

        BuildChooserSetting bcs = getExtensions().get(BuildChooserSetting.class);
        if (bcs!=null)  bc = bcs.getBuildChooser();
        else            bc = new DefaultBuildChooser();
        bc.gitSCM = this;
        return bc;
    }

    public void setBuildChooser(BuildChooser buildChooser) throws IOException {
        if (buildChooser.getClass()==DefaultBuildChooser.class) {
            getExtensions().remove(BuildChooserSetting.class);
        } else {
            getExtensions().replace(new BuildChooserSetting(buildChooser));
        }
    }

    /**
     * Gets the parameter-expanded effective value in the context of the current build.
     */
    public String getParamLocalBranch(AbstractBuild<?, ?> build) throws IOException, InterruptedException {
        String branch = getLocalBranch();
        // substitute build parameters if available
        return getParameterString(branch != null ? branch : null, build.getEnvironment());
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
            expandedRepos.add(
                newRemoteConfig(
                    getParameterString(oldRepo.getName(), env),
                    getParameterString(oldRepo.getURIs().get(0).toPrivateString(), env),
                    getRefSpecs(oldRepo, env).toArray(new RefSpec[0])));
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

    public static String getParameterString(String original, EnvVars env) {
        return env.expand(original);
    }

    private List<RefSpec> getRefSpecs(RemoteConfig repo, EnvVars env) {
        List<RefSpec> refSpecs = new ArrayList<RefSpec>();
        for (RefSpec refSpec : repo.getFetchRefSpecs()) {
            refSpecs.add(new RefSpec(getParameterString(refSpec.toString(), env)));
        }
        return refSpecs;
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
        return getExtensions().get(RemotePoll.class)==null;
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

        listener.getLogger().println("Using strategy: " + getBuildChooser().getDisplayName());

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

        final String singleBranch = getSingleBranch(lastBuild.getEnvironment());

        // fast remote polling needs a single branch and an existing last build
        if (getExtensions().get(RemotePoll.class)!=null && singleBranch != null && buildData.lastBuild != null && buildData.lastBuild.getMarked() != null) {
            final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener, false);

            GitClient git = createClient(listener, environment, Jenkins.getInstance(), null);

            String gitRepo = getParamExpandedRepos(lastBuild).get(0).getURIs().get(0).toString();
            ObjectId head = git.getHeadRev(gitRepo, getBranches().get(0).getName());

            if (head != null && buildData.lastBuild.getMarked().getSha1().equals(head)) {
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

            Collection<Revision> candidates = getBuildChooser().getCandidateRevisions(
                    true, singleBranch, git, listener, buildData, new BuildChooserContextImpl(project, null));

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

    /**
     * Allows {@link Builder}s and {@link Publisher}s to access a configured {@link GitClient} object to
     * perform additional git operations.
     */
    public GitClient createClient(BuildListener listener, EnvVars environment, AbstractBuild<?,?> build) throws IOException, InterruptedException {
        FilePath ws = workingDirectory(build.getProject(), build.getWorkspace(), environment, listener);
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
     * @throws
     */
    private void fetchFrom(GitClient git,
            TaskListener listener,
            RemoteConfig remoteRepository) throws InterruptedException {
        String name = remoteRepository.getName();
        // Assume there is only 1 URL for simplicity
        String url = remoteRepository.getURIs().get(0).toPrivateString();

        try {
            git.setRemoteUrl(name, url);
            git.fetch(name, remoteRepository.getFetchRefSpecs().toArray(new RefSpec[0]));
        } catch (GitException ex) {
            throw new GitException("Failed to fetch from "+name+": "+url,ex);
        }
    }

    private RemoteConfig newRemoteConfig(String name, String refUrl, RefSpec... refSpec) {

        try {
            Config repoConfig = new Config();
            // Make up a repo config from the request parameters

            repoConfig.setString("remote", name, "url", refUrl);
            List<String> str = new ArrayList<String>();
            if(refSpec != null && refSpec.length > 0)
                for (RefSpec rs: refSpec)
                    str.add(rs.toString());
            repoConfig.setStringList("remote", name, "fetch", str);

            return RemoteConfig.getAllRemoteConfigs(repoConfig).get(0);
        } catch (Exception ex) {
            throw new GitException("Error trying to create JGit configuration", ex);
        }
    }

    public GitTool resolveGitTool(TaskListener listener) {
        if (gitTool == null) return GitTool.getDefaultInstallation();
        GitTool git =  Jenkins.getInstance().getDescriptorByType(GitTool.DescriptorImpl.class).getInstallation(gitTool);
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

        GitClientType client = GitClientType.ANY;
        for (GitSCMExtension ext : extensions) {
            try {
                client = client.combine(ext.getRequiredClient());
            } catch (GitClientConflictException e) {
                throw new RuntimeException(ext.getDescriptor().getDisplayName() + " extended Git behavior is incompatible with other behaviors");
            }
        }
        if (client == GitClientType.JGIT) return JGitTool.MAGIC_EXENAME;

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
    private @NonNull Build determineRevisionToBuild(final AbstractBuild build,
                                              final BuildData buildData,
                                              final EnvVars environment,
                                              final GitClient git,
                                              final BuildListener listener) throws IOException, InterruptedException {
        PrintStream log = listener.getLogger();

        // every MatrixRun should build the exact same commit ID
        if (build instanceof MatrixRun) {
            MatrixBuild parentBuild = ((MatrixRun) build).getParentBuild();
            if (parentBuild != null) {
                BuildData parentBuildData = getBuildData(parentBuild);
                if (parentBuildData != null) {
                    Build lastBuild = parentBuildData.lastBuild;
                    if (lastBuild!=null)
                        return lastBuild;
                }
            }
        }

        // parameter forcing the commit ID to build
        final RevisionParameterAction rpa = build.getAction(RevisionParameterAction.class);
        if (rpa != null)
            return new Build(rpa.toRevision(git), build.getNumber(), null);

        final String singleBranch = environment.expand( getSingleBranch(environment) );

        final BuildChooserContext context = new BuildChooserContextImpl(build.getProject(), build);
        Collection<Revision> candidates = getBuildChooser().getCandidateRevisions(
                false, singleBranch, git, listener, buildData, context);

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
        Revision rev = candidates.iterator().next();
        Revision marked = rev;
        for (GitSCMExtension ext : extensions) {
            rev = ext.decorateRevisionToBuild(this,build,git,listener,rev);
        }
        return new Build(marked, rev, build.getNumber(), null);
    }

    /**
     * Retrieve Git objects from the specified remotes by doing the likes of clone/fetch/pull/etc.
     *
     * By the end of this method, remote refs are updated to include all the commits found in the remote servers.
     */
    private void retrieveChanges(AbstractBuild build, GitClient git, BuildListener listener) throws IOException, InterruptedException {
        final PrintStream log = listener.getLogger();

        List<RemoteConfig> repos = getParamExpandedRepos(build);
        if (repos.isEmpty())    return; // defensive check even though this is an invalid configuration

        if (git.hasGitRepo()) {
            // It's an update
            if (repos.size() == 1)
                log.println("Fetching changes from the remote Git repository");
            else
                log.println(MessageFormat.format("Fetching changes from {0} remote Git repositories", repos.size()));
        } else {
            log.println("Cloning the remote Git repository");

            // Clone from the first and then fetch from the rest
            RemoteConfig rc = repos.get(0);
            repos = repos.subList(1,repos.size());
            try {
                CloneCommand cmd = git.clone_().url(rc.getURIs().get(0).toPrivateString()).repositoryName(rc.getName());
                for (GitSCMExtension ext : extensions) {
                    ext.decorateCloneCommand(this, build, git, listener, cmd);
                }
                cmd.execute();
            } catch (GitException ex) {
                ex.printStackTrace(listener.error("Error cloning remote repo '%s'", rc.getName()));
                throw new AbortException();
            }
        }

        for (RemoteConfig remoteRepository : repos) {
            fetchFrom(git, listener, remoteRepository);
        }
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {

        if (VERBOSE)
            listener.getLogger().println("Using strategy: " + getBuildChooser().getDisplayName());

        BuildData previousBuildData = getBuildData(build.getPreviousBuild());   // read only
        BuildData buildData = copyBuildData(build.getPreviousBuild());
        build.addAction(buildData);
        if (VERBOSE && buildData.lastBuild != null) {
            listener.getLogger().println("Last Built Revision: " + buildData.lastBuild.revision);
        }

        EnvVars environment = build.getEnvironment(listener);
        GitClient git = createClient(listener,environment,build);

        for (GitSCMExtension ext : extensions) {
            ext.beforeCheckout(this, build, git, listener);
        }

        retrieveChanges(build, git, listener);
        Build revToBuild = determineRevisionToBuild(build, buildData, environment, git, listener);

        environment.put(GIT_COMMIT, revToBuild.revision.getSha1String());
        Branch branch = Iterables.getFirst(revToBuild.revision.getBranches(),null);
        if (branch!=null)   // null for a detached HEAD
            environment.put(GIT_BRANCH, branch.getName());

        listener.getLogger().println("Checking out " + revToBuild.revision);
        git.checkoutBranch(getParamLocalBranch(build), revToBuild.revision.getSha1String());

        buildData.saveBuild(revToBuild);
        build.addAction(new GitTagAction(build, buildData));

        computeChangeLog(git, revToBuild.revision, listener, previousBuildData, new FilePath(changelogFile),
                new BuildChooserContextImpl(build.getProject(), build));

        for (GitSCMExtension ext : extensions) {
            ext.onCheckoutCompleted(this, build, git,listener);
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
     * @param previousBuildData
     *      Information that captures what we did during the last build. We need this for changelog,
     *      or else we won't know where to stop.
     */
    private void computeChangeLog(GitClient git, Revision revToBuild, BuildListener listener, BuildData previousBuildData, FilePath changelogFile, BuildChooserContext context) throws IOException, InterruptedException {
        Writer out = new OutputStreamWriter(changelogFile.write(),"UTF-8");

        ChangelogCommand changelog = git.changelog();
        changelog.includes(revToBuild.getSha1());
        try {
            boolean exclusion = false;
            for (Branch b : revToBuild.getBranches()) {
                Build lastRevWas = getBuildChooser().prevBuildForChangelog(b.getName(), previousBuildData, git, context);
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
        if (rev!=null) {
            Branch branch = Iterables.getFirst(rev.getBranches(), null);
            if (branch!=null) {
                env.put(GIT_BRANCH, branch.getName());

                String prevCommit = getLastBuiltCommitOfBranch(build, branch);
                if (prevCommit != null) {
                    env.put(GIT_PREVIOUS_COMMIT, prevCommit);
                }
            }

            env.put(GIT_COMMIT, fixEmpty(rev.getSha1String()));
        }

       
        if (userRemoteConfigs.size()==1){
            env.put("GIT_URL", userRemoteConfigs.get(0).getUrl());
        } else {
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
        return new GitChangeLogParser(getExtensions().get(AuthorInChangelog.class)!=null);
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<GitSCM> {

        private String gitExe;
        private String globalConfigName;
        private String globalConfigEmail;
        private boolean createAccountBasedOnEmail;
//        private GitClientType defaultClientType = GitClientType.GITCLI;

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

        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            return GitSCMExtensionDescriptor.all();
        }

        public boolean showGitToolOptions() {
            return Jenkins.getInstance().getDescriptorByType(GitTool.DescriptorImpl.class).getInstallations().length>1;
        }

        /**
         * Lists available toolinstallations.
         * @return  list of available git tools
         */
        public List<GitTool> getGitTools() {
            GitTool[] gitToolInstallations = Hudson.getInstance().getDescriptorByType(GitTool.DescriptorImpl.class).getInstallations();
            return Arrays.asList(gitToolInstallations);
        }

        public ListBoxModel doFillGitToolItems() {
            ListBoxModel r = new ListBoxModel();
            for (GitTool git : getGitTools()) {
                r.add(git.getName());
            }
            return r;
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

        public static List<RemoteConfig> createRepositoryConfigurations(String[] urls,
                String[] repoNames,
                String[] refs) throws IOException {

            List<RemoteConfig> remoteRepositories;
            Config repoConfig = new Config();
            // Make up a repo config from the request parameters

            String[] names = repoNames;

            names = GitUtils.fixupNames(names, urls);

            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    String name = names[i];
                    name = name.replace(' ', '_');

                    if (isBlank(refs[i])) {
                        refs[i] = "+refs/heads/*:refs/remotes/" + name + "/*";
                    }

                    repoConfig.setString("remote", name, "url", urls[i]);
                    repoConfig.setStringList("remote", name, "fetch", new ArrayList<String>(Arrays.asList(refs[i].split("\\s+"))));
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
                mergeOptions.setMergeStrategy(mergeOptionsBean.getMergeStrategy());
            }

            return mergeOptions;
        }

        public static GitWeb createGitWeb(String url) {
            if (!isBlank(url)) {
                try {
                    return  new GitWeb(url);
                } catch (MalformedURLException e) {
                    throw new GitException("Error creating GitWeb", e);
                }
            }
            return null;
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

//        public GitClientType getDefaultClientType() {
//            return defaultClientType;
//        }
//
//        public void setDefaultClientType(String defaultClientType) {
//            this.defaultClientType = GitClientType.valueOf(defaultClientType);
//        }
    }

    private static final long serialVersionUID = 1L;

    public boolean isDoGenerateSubmoduleConfigurations() {
        return this.doGenerateSubmoduleConfigurations;
    }

    @Exported
    public List<BranchSpec> getBranches() {
        return branches;
    }

    /**
     * Use {@link PreBuildMerge}.
     */
    @Exported
    @Deprecated
    public PreBuildMergeOptions getMergeOptions() throws FormException {
        return DescriptorImpl.createMergeOptions(getUserMergeOptions(), remoteRepositories);
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

    /**
     * Given a Revision "r", check whether the list of revisions "COMMITS_WE_HAVE_BUILT..r" are to be entirely excluded given the exclusion rules
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

            revShow.add("commit "); // sentinel value

            int start=0, idx=0;
            for (String line : revShow) {
                if (line.startsWith("commit ") && idx!=0) {
                    GitChangeSet change = new GitChangeSet(revShow.subList(start,idx), getExtensions().get(AuthorInChangelog.class)!=null);

                    Boolean excludeThisCommit=null;
                    for (GitSCMExtension ext : extensions) {
                        excludeThisCommit = ext.isRevExcluded(this, git, change, listener, buildData);
                        if (excludeThisCommit!=null)
                            break;
                    }
                    if (excludeThisCommit==null || !excludeThisCommit)
                        return false;    // this sequence of commits have one commit that we want to build
                    start = idx;
                }

                idx++;
            }

            assert start==revShow.size()-1;

            // every commit got excluded
            return true;
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

    /**
     * To avoid pointlessly large changelog, we'll limit the number of changes up to this.
     */
    public static final int MAX_CHANGELOG = Integer.getInteger(GitSCM.class.getName()+".maxChangelog",1024);
}
