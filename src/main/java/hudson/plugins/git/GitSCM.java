package hudson.plugins.git;

import hudson.*;
import hudson.FilePath.FileCallable;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;

import hudson.model.*;

import static hudson.Util.fixEmptyAndTrim;

import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.opt.PreBuildMergeOptions;

import hudson.plugins.git.util.*;
import hudson.plugins.git.util.Build;

import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.FormValidation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.RepositoryConfig;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;


/**
 * Git SCM.
 *
 * @author Nigel Magnay
 */
public class GitSCM extends SCM implements Serializable {

    // old fields are left so that old config data can be read in, but
    // they are deprecated. transient so that they won't show up in XML
    // when writing back
    @Deprecated transient String source;
    @Deprecated transient String branch;

    /**
     * Store a config version so we're able to migrate config on various
     * functionality upgrades.
     */
    private Long configVersion;

    /**
     * All the remote repositories that we know about.
     */
    @Deprecated private transient List<RemoteConfig> remoteRepositories;

    /**
     * All the branches that we wish to care about building.
     */
    @Deprecated private transient List<BranchSpec> branches;

    /**
     * Optional local branch to work on.
     */
    @Deprecated private transient String localBranch;

    /**
     * Options for merging before a build.
     */
    @Deprecated private transient PreBuildMergeOptions mergeOptions;

    /**
     * Use --recursive flag on submodule commands - requires git>=1.6.5
     */
    @Deprecated private transient boolean recursiveSubmodules;

    @Deprecated private transient boolean doGenerateSubmoduleConfigurations;
    private boolean authorOrCommitter;
    
    private boolean clean;
    private boolean wipeOutWorkspace;
    
    private boolean pruneBranches;
    
    /**
     * @deprecated
     *      Replaced by {@link #buildChooser} instead.
     */
    private transient String choosingStrategy;

    private BuildChooser buildChooser;

    public String gitTool = null;

    private List<GitSCMModule> modules;
    private GitRepositoryBrowser browser;

    @Deprecated private transient Collection<SubmoduleConfig> submoduleCfg;

    public static final String GIT_BRANCH = "GIT_BRANCH";
    public static final String GIT_COMMIT = "GIT_COMMIT";

    private String relativeTargetDir;

    private String excludedRegions;

    private String excludedUsers;

    private String gitConfigName;
    private String gitConfigEmail;

    private boolean skipTag;

    /**
     * A convenience constructor that sets everything to default.
     *
     * @param repositoryUrl
     *      Repository URL to clone from.
     */
    public GitSCM(String repositoryUrl) throws IOException {
        this(
                Arrays.asList(
                        new GitSCMModule(Arrays.asList(new Repository(repositoryUrl, null, null)),
                                Collections.singletonList(new BranchSpec("")),
                                null,
                                new PreBuildMergeOptions(),
                                null
                                )),
                false, false, new DefaultBuildChooser(), null, null, false, null,
                null, false, null, null, false);
    }

    @DataBoundConstructor
    public GitSCM(
                  List<GitSCMModule> modules,
                  boolean clean,
                  boolean wipeOutWorkspace,
                  BuildChooser buildChooser,
                  GitRepositoryBrowser browser,
                  String gitTool,
                  boolean authorOrCommitter,
                  String excludedRegions,
                  String excludedUsers,
                  boolean pruneBranches,
                  String gitConfigName,
                  String gitConfigEmail,
                  boolean skipTag) {
        this.modules = modules;

        this.browser = browser;
        this.clean = clean;
        this.wipeOutWorkspace = wipeOutWorkspace;
        this.configVersion = 2L;
        this.gitTool = gitTool;
        this.authorOrCommitter = authorOrCommitter;
        this.buildChooser = buildChooser;
        this.excludedRegions = excludedRegions;
        this.excludedUsers = excludedUsers;
        this.pruneBranches = pruneBranches;
        this.gitConfigName = gitConfigName;
        this.gitConfigEmail = gitConfigEmail;
        this.skipTag = skipTag;
        buildChooser.gitSCM = this; // set the owner

    }

    public Object readResolve()  {
        // Migrate data

        // Default unspecified to v0
        if(configVersion == null)
            configVersion = 0L;

        if(source!=null) {
            remoteRepositories = new ArrayList<RemoteConfig>();
            branches = new ArrayList<BranchSpec>();
            doGenerateSubmoduleConfigurations = false;
            mergeOptions = new PreBuildMergeOptions();

            recursiveSubmodules = false;
            
            remoteRepositories.add(newRemoteConfig("origin", source, new RefSpec("+refs/heads/*:refs/remotes/origin/*")));
            if(branch != null) {
                branches.add(new BranchSpec(branch));
            }
            else {
                branches.add(new BranchSpec("*/master"));
            }
        }

        if(configVersion < 1 && branches != null) {
            // Migrate the branch specs from
            // single * wildcard, to ** wildcard.
            for(BranchSpec branchSpec : branches) {
                String name = branchSpec.getName();
                name = name.replace("*", "**");
                branchSpec.setName(name);
            }
        }

        if(mergeOptions != null && mergeOptions.doMerge() && mergeOptions.getMergeRemote() == null) {
            mergeOptions.setMergeRemote(remoteRepositories.get(0));
        }

        if (choosingStrategy!=null && buildChooser==null) {
            for (BuildChooserDescriptor d : BuildChooser.all()) {
                if (choosingStrategy.equals(d.getLegacyId()))
                    try {
                        buildChooser = d.clazz.newInstance();
                    } catch (InstantiationException e) {
                        LOGGER.log(Level.WARNING, "Failed to instantiate the build chooser",e);
                    } catch (IllegalAccessException e) {
                        LOGGER.log(Level.WARNING, "Failed to instantiate the build chooser",e);
                    }
            }
        }
        if (buildChooser==null)
            buildChooser = new DefaultBuildChooser();

        buildChooser.gitSCM = this;

        if (configVersion < 2) {
            List<Repository> remoteRepositories = new ArrayList<Repository>();
            for (RemoteConfig rc: this.remoteRepositories) {
                remoteRepositories.add(
                        new Repository(
                                rc.getURIs().get(0).toPrivateString(),
                                rc.getName(),
                                rc.getFetchRefSpecs().get(0).toString()
                        )
                );
            }
            GitSCMModule module = new GitSCMModule(remoteRepositories, branches, localBranch, mergeOptions, relativeTargetDir);
            modules = Arrays.asList(module);
        }

        return this;
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
        if (excluded != null) {
            Pattern[] patterns = new Pattern[excluded.length];

            int i = 0;
            for (String excludedRegion : excluded) {
                patterns[i++] = Pattern.compile(excludedRegion);
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
        if (s==null)
            return Collections.emptySet();

        Set<String> users = new HashSet<String>();
        for (String user : s.split("[\\r\\n]+"))
            users.add(user.trim());
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
    
    public String getGitConfigNameToUse() {
        String confName;
        String globalConfigName = ((DescriptorImpl)getDescriptor()).getGlobalConfigName();
        if ((globalConfigName != null) && (gitConfigName == null) && (!fixEmptyAndTrim(globalConfigName).equals(""))) {
            confName = globalConfigName;
        } else {
            confName = gitConfigName;
        }
        
        return fixEmptyAndTrim(confName);
    }

    public String getGitConfigEmailToUse() {
        String confEmail;
        String globalConfigEmail = ((DescriptorImpl)getDescriptor()).getGlobalConfigEmail();
        if ((globalConfigEmail != null) && (gitConfigEmail == null) && (!fixEmptyAndTrim(globalConfigEmail).equals(""))) {
            confEmail = globalConfigEmail;
        } else {
            confEmail = gitConfigEmail;
        }
        
        return fixEmptyAndTrim(confEmail);
    }

    public boolean getSkipTag() {
        return this.skipTag;
    }
    
    public boolean getPruneBranches() {
        return this.pruneBranches;
    }

    public boolean getWipeOutWorkspace() {
        return this.wipeOutWorkspace;
    }
    
    public boolean getClean() {
        return this.clean;
    }

    public BuildChooser getBuildChooser() {
        return buildChooser;
    }

    public String getGitTool() {
        return gitTool;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> abstractBuild, Launcher launcher, TaskListener taskListener) throws IOException, InterruptedException {
        return SCMRevisionState.NONE;
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, final TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        // Poll for changes. Are there any unbuilt revisions that Hudson ought to build ?

        listener.getLogger().println("Using strategy: " + buildChooser.getDisplayName());

        final AbstractBuild lastBuild = project.getLastBuild();

        if(lastBuild != null) {
            listener.getLogger().println("[poll] Last Build : #" + lastBuild.getNumber());
        } else {
            // If we've never been built before, well, gotta build!
            listener.getLogger().println("[poll] No previous build, so forcing an initial build.");
            return PollingResult.BUILD_NOW;
        }

        final String gitExe;
        {
            //If this project is tied onto a node, it's built always there. On other cases,
            //polling is done on the node which did the last build.
            //
            Label label = project.getAssignedLabel();
            if (label != null && label.isSelfLabel()) {
                if(label.getNodes().iterator().next() != project.getLastBuiltOn()) {
                    listener.getLogger().println("Last build was not on tied node, forcing rebuild.");
                    return PollingResult.BUILD_NOW;
                }
                gitExe = getGitExe(label.getNodes().iterator().next(), listener);
            } else {
                gitExe = getGitExe(project.getLastBuiltOn(), listener);
            }
        }

        final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener);

        boolean pollChangesResult = false;

        for (GitSCMModule module: modules) {

            FilePath workingDirectory = module.workingDirectory(workspace);

            // Rebuild if the working directory doesn't exist
            // I'm actually not 100% sure about this, but I'll leave it in for now.
            // Update 9/9/2010 - actually, I think this *was* needed, since we weren't doing a better check
            // for whether we'd ever been built before. But I'm fixing that right now anyway.
            if (!workingDirectory.exists()) {
                return PollingResult.BUILD_NOW;
            }

            final List<RemoteConfig> paramRepos = module.getParamExpandedRepos(lastBuild);
            final String singleBranch = module.getSingleBranch(lastBuild);

            final BuildData buildData = module.fixNull(module.getBuildData(lastBuild, false));

            if(buildData != null && buildData.lastBuild != null) {
                listener.getLogger().printf("[poll] Last Built Revision for %s: %s%n",
                        module.getRelativeTargetDir(), buildData.lastBuild.revision);
            }

            pollChangesResult |= workingDirectory.act(new FileCallable<Boolean>() {
                    private static final long serialVersionUID = 1L;
                    public Boolean invoke(File localWorkspace, VirtualChannel channel) throws IOException {
                        IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment);

                        if (git.hasGitRepo()) {
                            // Repo is there - do a fetch
                            listener.getLogger().println("Fetching changes from the remote Git repositories");

                            // Fetch updates
                            for (RemoteConfig remoteRepository : paramRepos) {
                                fetchFrom(git, listener, remoteRepository);
                            }

                            listener.getLogger().println("Polling for changes in");

                            Collection<Revision> origCandidates = buildChooser.getCandidateRevisions(
                                    true, singleBranch, git, listener, buildData);

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

        }

        return pollChangesResult ? PollingResult.SIGNIFICANT : PollingResult.NO_CHANGES;
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
                                            listener, parentGit.getEnvironment());
                
                subGit.clean();
            } catch (Exception ex) {
                listener
                    .getLogger()
                    .println(
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
    private boolean fetchFrom( IGitAPI git,
                               TaskListener listener,
                               RemoteConfig remoteRepository ) {
        try {
            git.fetch(remoteRepository);
            return true;
        } catch (GitException ex) {
            listener.error(
                           "Problem fetching from " + remoteRepository.getName()
                           + " / " + remoteRepository.getName()
                           + " - could be unavailable. Continuing anyway");
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
    private boolean fetchSubmodulesFrom( IGitAPI git,
                                         File workspace,
                                         TaskListener listener,
                                         RemoteConfig remoteRepository ) {
        boolean fetched = true;

        try {
            // This ensures we don't miss changes to submodule paths and allows
            // seamless use of bare and non-bare superproject repositories.
            git.setupSubmoduleUrls( remoteRepository.getName(), listener );

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
                        RemoteConfig submoduleRemoteRepository
                            = getSubmoduleRepository( git,
                                                      remoteRepository,
                                                      submodule.getFile() );
                        File subdir = new File(workspace, submodule.getFile());

                        listener.getLogger().println(
                          "Trying to fetch " + submodule.getFile() + " into " + subdir );

                        IGitAPI subGit = new GitAPI( git.getGitExe(),
                                                     new FilePath(subdir),
                                                     listener, git.getEnvironment() );

                        subGit.fetch(submoduleRemoteRepository);
                    } catch (Exception ex) {
                        listener.getLogger().println(
                            "Problem fetching from submodule "
                            + submodule.getFile()
                            + " - could be unavailable. Continuing anyway" );
                    }
                }
            }
        } catch (GitException ex) {
            ex.printStackTrace(listener.error(
                "Problem fetching submodules from a path relative to "
                + remoteRepository.getName()
                + " / " + remoteRepository.getName()
                + " - could be unavailable. Continuing anyway"
            ));
            fetched = false;
        }

        return fetched;
    }

    public RemoteConfig getSubmoduleRepository( IGitAPI parentGit,
                                                RemoteConfig orig,
                                                String name ) throws GitException {
        // The first attempt at finding the URL in this new code relies on
        // submoduleInit, submoduleSync, fixSubmoduleUrls already being executed
        // since the last fetch of the super project.  (This is currently done
        // by calling git.setupSubmoduleUrls(...). )
        String refUrl = parentGit.getSubmoduleUrl( name );
        return newRemoteConfig(name, refUrl, orig.getFetchRefSpecs().get(0));
    }

    static RemoteConfig newRemoteConfig(String name, String refUrl, RefSpec refSpec) {

        File temp = null;
        try {
            temp = File.createTempFile("tmp", "config");
            RepositoryConfig repoConfig = new RepositoryConfig(null, temp);
            // Make up a repo config from the request parameters


            repoConfig.setString("remote", name, "url", refUrl);
            repoConfig.setString("remote", name, "fetch", refSpec.toString());
            repoConfig.save();
            return RemoteConfig.getAllRemoteConfigs(repoConfig).get(0);
        }
        catch(Exception ex) {
            throw new GitException("Error creating temp file");
        }
        finally {
            if(temp != null)
                temp.delete();
        }


    }

    private boolean changeLogResult(String changeLog, File changelogFile) throws IOException {
        if (changeLog == null)
            return false;
        else {
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
        for(GitTool t : gitToolInstallations) {
            //If gitTool is null, use first one.
            if(gitTool == null) {
                gitTool = t.getName();
            }

            if(t.getName().equals(gitTool)) {
                if(builtOn != null){
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

    @Override
    public boolean checkout(final AbstractBuild build, Launcher launcher,
                            final FilePath workspace, final BuildListener listener, File changelogFile)
        throws IOException, InterruptedException {
        CheckoutResult[] returnData = new CheckoutResult[modules.size()];

        listener.getLogger().println("Checkout:" + workspace.getName() + " / " + workspace.getRemote() + " - " + workspace.getChannel());
        listener.getLogger().println("Using strategy: " + buildChooser.getDisplayName());

        final String projectName = build.getProject().getName();
        final int buildNumber = build.getNumber();

        final String gitExe = getGitExe(build.getBuiltOn(), listener);

        final String buildnumber = "jenkins-" + projectName + "-" + buildNumber;

        EnvVars tempEnvironment = build.getEnvironment(listener);

        String confName = getGitConfigNameToUse();
        if ((confName != null) && (!confName.equals(""))) {
            tempEnvironment.put("GIT_COMMITTER_NAME", confName);
            tempEnvironment.put("GIT_AUTHOR_NAME", confName);
        }
        String confEmail = getGitConfigEmailToUse();
        if ((confEmail != null) && (!confEmail.equals(""))) {
            tempEnvironment.put("GIT_COMMITTER_EMAIL", confEmail);
            tempEnvironment.put("GIT_AUTHOR_EMAIL", confEmail);
        }

        final EnvVars environment = tempEnvironment;
        
        Revision tempParentLastBuiltRev = null;

        if (build instanceof MatrixRun) {
            MatrixBuild parentBuild = ((MatrixRun)build).getParentBuild();
            if (parentBuild != null) {
                BuildData parentBuildData = parentBuild.getAction(BuildData.class);
                if (parentBuildData != null) {
                    tempParentLastBuiltRev = parentBuildData.getLastBuiltRevision();
                }
            }
        }

        final String[] singleBranch = new String[modules.size()];
        final String[] paramLocalBranch = new String[modules.size()];
        final List<RemoteConfig>[] paramRepos = new List[modules.size()];
        final FilePath[] workingDirectory = new FilePath[modules.size()];
        final Revision[] revToBuild = new Revision[modules.size()];

        for (int _i = 0; _i < modules.size(); _i++) {
            final int i = _i;
            final GitSCMModule module = modules.get(i);

        workingDirectory[i] = module.workingDirectory(workspace);

        if (!workingDirectory[i].exists()) {
            workingDirectory[i].mkdirs();
        }

            final BuildData buildData = module.getBuildData(build.getPreviousBuild(), true);

            if (buildData.lastBuild != null) {
                listener.getLogger().println("Last Built Revision: " + buildData.lastBuild.revision);
            }


        singleBranch[i] = module.getSingleBranch(build);
        paramLocalBranch[i] = module.getParamLocalBranch(build);
        paramRepos[i] = module.getParamExpandedRepos(build);
        
        final Revision parentLastBuiltRev = tempParentLastBuiltRev;

        final RevisionParameterAction rpa = build.getAction(RevisionParameterAction.class);

        revToBuild[i] = workingDirectory[i].act(new FileCallable<Revision>() {
                private static final long serialVersionUID = 1L;
                public Revision invoke(File localWorkspace, VirtualChannel channel)
                throws IOException {
                    FilePath ws = new FilePath(localWorkspace);
                    listener.getLogger().println("Checkout:" + ws.getName() + " / " + ws.getRemote() + " - " + ws.getChannel());
                    IGitAPI git = new GitAPI(gitExe, ws, listener, environment);

                    if (wipeOutWorkspace) {
                        listener.getLogger().println("Wiping out workspace first.");
                        try {
                            ws.deleteContents();
                        } catch (InterruptedException e) {
                            // I don't really care if this fails.
                        }
                    }
                    
                    if (git.hasGitRepo()) {
                        // It's an update

                        // Do we want to prune first?
                        if (pruneBranches) {
                            listener.getLogger().println("Pruning obsolete local branches");
                            for (RemoteConfig remoteRepository : paramRepos[i]) {
                                git.prune(remoteRepository);
                            }
                        }

                        listener.getLogger().println("Fetching changes from the remote Git repository");

                        boolean fetched = false;
                        
                        for (RemoteConfig remoteRepository : paramRepos[i]) {
                            if ( fetchFrom(git, listener, remoteRepository) ) {
                                fetched = true;
                            }
                        }

                        if (!fetched) {
                            listener.error("Could not fetch from any repository");
                            throw new GitException("Could not fetch from any repository");
                        }

                    } else {
                        
                        listener.getLogger().println("Cloning the remote Git repository");

                        // Go through the repositories, trying to clone from one
                        //
                        boolean successfullyCloned = false;
                        for(RemoteConfig rc : paramRepos[i]) {
                            try {
                                git.clone(rc);
                                successfullyCloned = true;
                                break;
                            }
                            catch(GitException ex) {
                                listener.error("Error cloning remote repo '%s' : %s", rc.getName(), ex.getMessage());
                                if(ex.getCause() != null) {
                                    listener.error("Cause: %s", ex.getCause().getMessage());
                                }
                                // Failed. Try the next one
                                listener.getLogger().println("Trying next repository");
                            }
                        }

                        if(!successfullyCloned) {
                            listener.error("Could not clone repository");
                            throw new GitException("Could not clone");
                        }

                        boolean fetched = false;
                        
                        // Also do a fetch
                        for (RemoteConfig remoteRepository : paramRepos[i]) {
                            try {
                                git.fetch(remoteRepository);
                                fetched = true;
                            } catch (Exception e) {
                                listener.error(
                                               "Problem fetching from " + remoteRepository.getName()
                                               + " / " + remoteRepository.getName()
                                               + " - could be unavailable. Continuing anyway");
                                
                            } 
                        }

                        if (!fetched) {
                            listener.error("Could not fetch from any repository");
                            throw new GitException("Could not fetch from any repository");
                        }

                        if (getClean()) {
                            listener.getLogger().println("Cleaning workspace");
                            git.clean();
                            
                            if (git.hasGitModules()) {
                                git.submoduleClean(module.getRecursiveSubmodules());
                            }
                        }
                    }

                    if (parentLastBuiltRev != null)
                        return parentLastBuiltRev;

                    if (rpa!=null && i == 0) {
                        // Only honor RevisionParameterAction for the first module for now
                        return rpa.toRevision(git);
                    }

                    Collection<Revision> candidates = buildChooser.getCandidateRevisions(
                            false, singleBranch[i], git, listener, buildData);
                    if(candidates.size() == 0)
                        return null;
                    return candidates.iterator().next();
                }
            });

        }

        boolean foundRev = false;
        for (int i = 0; i < revToBuild.length; i++) {
            if (revToBuild[i] != null) {
                foundRev = true;
                listener.getLogger().println("Commencing build of " + revToBuild[i] + " for " + modules.get(i).getRelativeTargetDir());
                if (i == 0) {
                    environment.put(GIT_COMMIT, revToBuild[i].getSha1String());
                }
                environment.put(GIT_COMMIT + i, revToBuild[i].getSha1String());
            }
        }
        if (!foundRev) {
                // getBuildCandidates should make the last item the last build, so a re-build
                // will build the last built thing.
            listener.error("Nothing to do");
            return false;
        }

        for (int _i = 0; _i < modules.size(); _i++) {
            final int i = _i;
            final GitSCMModule module = modules.get(i);

            final PreBuildMergeOptions mergeOptions = module.getMergeOptions();

            final BuildData buildData = module.fixNull(module.getBuildData(build.getPreviousBuild(), true));

            if (buildData.lastBuild != null) {
                listener.getLogger().println("Last Built Revision: " + buildData.lastBuild.revision);
            }
            
        if (mergeOptions.doMerge()) {
            if (!revToBuild[i].containsBranchName(mergeOptions.getRemoteBranchName())) {
                returnData[i] = workingDirectory[i].act(new FileCallable<CheckoutResult>() {
                    private static final long serialVersionUID = 1L;

                    public CheckoutResult invoke(File localWorkspace, VirtualChannel channel)
                            throws IOException {
                        IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment);

                        // Do we need to merge this revision onto MergeTarget

                        // Only merge if there's a branch to merge that isn't
                        // us..
                        listener.getLogger().println(
                                "Merging " + revToBuild + " onto "
                                        + mergeOptions.getMergeTarget());

                        // checkout origin/blah
                        ObjectId target = git.revParse(mergeOptions.getRemoteBranchName());

                        git.checkoutBranch(paramLocalBranch[i], target.name());

                        try {
                            git.merge(revToBuild[i].getSha1().name());
                        } catch (Exception ex) {
                            listener
                                    .getLogger()
                                    .println(
                                            "Branch not suitable for integration as it does not merge cleanly");

                            // We still need to tag something to prevent
                            // repetitive builds from happening - tag the
                            // candidate
                            // branch.
                            git.checkoutBranch(paramLocalBranch[i], revToBuild[i].getSha1().name());

                            if (!getSkipTag()) {
                                git.tag(buildnumber, "Jenkins Build #" + buildNumber);
                            }

                            buildData.saveBuild(new Build(revToBuild[i], buildNumber, Result.FAILURE));
                            return new CheckoutResult(null, buildData);
                        }

                        if (git.hasGitModules()) {
                            // This ensures we don't miss changes to submodule paths and allows
                            // seamless use of bare and non-bare superproject repositories.
                            git.setupSubmoduleUrls(revToBuild[i], listener);
                            git.submoduleUpdate(module.getRecursiveSubmodules());
                        }

                        if (!getSkipTag()) {
                            // Tag the successful merge
                            git.tag(buildnumber, "Jenkins Build #" + buildNumber);
                        }

                        String changeLog = computeChangeLog(git, revToBuild[i], listener, buildData);

                        Build build = new Build(revToBuild[i], buildNumber, null);
                        buildData.saveBuild(build);
                        GitUtils gu = new GitUtils(listener, git);
                        build.mergeRevision = gu.getRevisionForSHA1(target);
                        if (getClean()) {
                            listener.getLogger().println("Cleaning workspace");
                            git.clean();
                            if (git.hasGitModules()) {
                                git.submoduleClean(module.getRecursiveSubmodules());
                            }
                        }

                        // Fetch the diffs into the changelog file
                        return new CheckoutResult(changeLog, buildData);
                    }
                });
                build.addAction(returnData[i].buildData);
                return changeLogResult(returnData[i].changeLog, changelogFile); //TODO combine
            }
        }

        }
        // No merge

        for (int _i = 0; _i < modules.size(); _i++) {
            final int i = _i;
            final GitSCMModule module = modules.get(i);

            final BuildData buildData = module.fixNull(module.getBuildData(build.getPreviousBuild(), true));

            if (buildData.lastBuild != null) {
                listener.getLogger().printf("Last Built Revision for %s : %s%n", module.getRelativeTargetDir(), buildData.lastBuild.revision);
            }
            
        returnData[i] = workingDirectory[i].act(new FileCallable<CheckoutResult>() {
                private static final long serialVersionUID = 1L;
                public CheckoutResult invoke(File localWorkspace, VirtualChannel channel)
                throws IOException {
                    IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment);

                    // Straight compile-the-branch
                    listener.getLogger().println("Checking out " + revToBuild[i] + " for " + module.getRelativeTargetDir());

                    if (getClean()) {
                        listener.getLogger().println("Cleaning workspace");
                        git.clean();
                    }

                    git.checkoutBranch(paramLocalBranch[i], revToBuild[i].getSha1().name());
                        
                    if (git.hasGitModules()) {
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
                        if (!module.getRecursiveSubmodules()) {
                            for (RemoteConfig remoteRepository : paramRepos[i]) {
                                fetchSubmodulesFrom(git, localWorkspace, listener, remoteRepository);
                            }
                        }

                        // This ensures we don't miss changes to submodule paths and allows
                        // seamless use of bare and non-bare superproject repositories.
                        git.setupSubmoduleUrls( revToBuild[i], listener );
                        git.submoduleUpdate(module.getRecursiveSubmodules());

                    }

                    // if(compileSubmoduleCompares)
                    if (module.getDoGenerate()) {
                        SubmoduleCombinator combinator = new SubmoduleCombinator(
                                                                                 git, listener, localWorkspace, module.getSubmoduleCfg());
                        combinator.createSubmoduleCombinations();
                    }

                    if (!getSkipTag()) {
                        // Tag the successful merge
                        git.tag(buildnumber, "Jenkins Build #" + buildNumber);
                    }

                    String moduleChangeLog = computeChangeLog(git, revToBuild[i], listener, buildData);

                    buildData.saveBuild(new Build(revToBuild[i], buildNumber, null));

                    // Fetch the diffs into the changelog file
                    return new CheckoutResult(moduleChangeLog, buildData);
                }
            });
        }

        final StringBuilder changeLog = new StringBuilder();

        List<BuildData> buildDatas = new ArrayList<BuildData>();
        for (CheckoutResult result: returnData) {
            buildDatas.add(result.buildData);
            build.addAction(result.buildData);
            changeLog.append(result.changeLog);
        }
        build.addAction(new MultiBuildData(build));

        return changeLogResult(changeLog.toString(), changelogFile);

    }

    /**
     * Build up change log from all the branches that we've merged into {@code revToBuild}
     *
     * @param git
     *      Used for invoking Git
     * @param revToBuild
     *      Points to the revisiont we'll be building. This includes all the branches we've merged.
     * @param listener
     *      Used for writing to build console
     * @param buildData
     *      Information that captures what we did during the last build. We need this for changelog,
     *      or else we won't know where to stop.
     */
    private String computeChangeLog(IGitAPI git, Revision revToBuild, BuildListener listener, BuildData buildData) throws IOException {
        int histories = 0;

        StringBuilder changeLog = new StringBuilder();
        try {
            for(Branch b : revToBuild.getBranches()) {
                Build lastRevWas = buildChooser.prevBuildForChangelog(b.getName(), buildData, git);
                if(lastRevWas != null) {
                    if (git.isCommitInRepo(lastRevWas.getSHA1().name())) {
                        changeLog.append(putChangelogDiffsIntoFile(git,  b.name, lastRevWas.getSHA1().name(), revToBuild.getSha1().name()));
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
            changeLog.append("Unable to retrieve changeset");
        }

        if(histories > 1)
            listener.getLogger().println("Warning : There are multiple branch changesets here");

        return changeLog.toString();
    }

    public void buildEnvVars(AbstractBuild<?,?> build, java.util.Map<String, String> env) {
        super.buildEnvVars(build, env);
        for (int i = 0; i < modules.size(); i++) {
            String branch = modules.get(i).getSingleBranch(build);
            if(branch != null){
                if (i == 0) env.put(GIT_BRANCH, branch);
                env.put(GIT_BRANCH + i, branch);
            }
        }
    }

    private String putChangelogDiffsIntoFile(IGitAPI git, String branchName, String revFrom,
                                             String revTo) throws IOException {
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        // fos.write("<data><![CDATA[".getBytes());
        String changeset = "Changes in branch " + branchName + ", between " + revFrom + " and " + revTo + "\n";
        fos.write(changeset.getBytes());

        git.changelog(revFrom, revTo, fos);
        // fos.write("]]></data>".getBytes());
        fos.close();
        return fos.toString("UTF-8");
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new GitChangeLogParser(getAuthorOrCommitter());
    }

    public List<GitSCMModule> getModules() {
        return modules;
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

        /**
         * Global setting to be used in call to "git config user.email".
         */
        public String getGlobalConfigEmail() {
            return globalConfigEmail;
        }

        /**
         * Old configuration of git executable - exposed so that we can
         * migrate this setting to GitTool without deprecation warnings.
         */
        public String getOldGitExe() {
            return gitExe;
        }

        public static List<RemoteConfig> createRepositoryConfigurations(List<Repository> repositories) throws IOException {
            File temp = File.createTempFile("tmp", "config");
            try {
                List<RemoteConfig> remoteRepositories;
                RepositoryConfig repoConfig = new RepositoryConfig(null, temp);
                // Make up a repo config from the request parameters

                String[] names = new String[repositories.size()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = repositories.get(i).getRepoName();
                }
                names = GitUtils.fixupNames(names);

                for (int i = 0; i < names.length; i++) {
                    Repository repository = repositories.get(i);
                    String name = names[i];
                    name = name.replace(' ', '_');

                    String refSpec = repository.getRefSpec();
                    if(StringUtils.isEmpty(refSpec)) {
                        refSpec = "+refs/heads/*:refs/remotes/" + name + "/*";
                    }

                    repoConfig.setString("remote", name, "url", repository.getUrl());
                    repoConfig.setString("remote", name, "fetch", refSpec);
                }

                try {
                    repoConfig.save();
                    remoteRepositories = RemoteConfig.getAllRemoteConfigs(repoConfig);
                }
                catch (Exception e) {
                    throw new GitException("Error creating repositories", e);
                }
                return remoteRepositories;
            } finally {
                temp.delete();
            }
        }

        public FormValidation doGitRemoteNameCheck(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
            String mergeRemoteName = req.getParameter("value");
            boolean isMerge = req.getParameter("isMerge") != null;

            // Added isMerge because we don't want to allow empty remote names for tag/branch pushes.
            if (mergeRemoteName.length() == 0 && isMerge)
                return FormValidation.ok();

            String[] urls = req.getParameterValues("git.repo.url");
            String[] names = req.getParameterValues("git.repo.name");

            names = GitUtils.fixupNames(names);

            for (String name : names) {
                if (name.equals(mergeRemoteName))
                    return FormValidation.ok();
            }

            return FormValidation.error("No remote repository configured with name '" + mergeRemoteName + "'");
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

    }

    private static class CheckoutResult implements Serializable {
        final String changeLog;
        final BuildData buildData;

        private CheckoutResult(String changeLog, BuildData buildData) {
            this.changeLog = changeLog;
            this.buildData = buildData;
        }
    }

    private static final long serialVersionUID = 1L;


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

            List<String> excludedPaths = new ArrayList<String>();
            if (excludedPatterns.length > 0) {
                for (String path : paths) {
                    for (Pattern pattern : excludedPatterns) {
                        if (pattern.matcher(path).matches()) {
                            excludedPaths.add(path);
                            break;
                        }
                    }
                }
            }

            // If every affected path is excluded, return true.
            if (paths.size() == excludedPaths.size()) {
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
        
    private static final Logger LOGGER = Logger.getLogger(GitSCM.class.getName());

    /**
     * Set to true to enable more logging to build's {@link TaskListener}.
     * Used by various classes in this package.
     */
    public static boolean VERBOSE = Boolean.getBoolean(GitSCM.class.getName()+".verbose");


    public List<RemoteConfig> getRepositories() {
        return modules.get(0).getRepositories();
    }

    public List<BranchSpec> getBranches() {
        return modules.get(0).getBranches();
    }

}
