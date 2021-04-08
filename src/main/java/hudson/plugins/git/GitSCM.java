package hudson.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.collect.Iterables;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.init.Initializer;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.extensions.impl.AuthorInChangelog;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.BuildSingleRevisionOnly;
import hudson.plugins.git.extensions.impl.ChangelogToBranch;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.plugins.git.extensions.impl.PathRestriction;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.*;
import hudson.remoting.Channel;
import hudson.scm.AbstractScmTagAction;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.SCMTrigger;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMMatrixUtil;
import jenkins.plugins.git.GitToolChooser;
import net.sf.json.JSONObject;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.*;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.collect.Lists.newArrayList;
import static hudson.init.InitMilestone.JOB_LOADED;
import static hudson.init.InitMilestone.PLUGINS_STARTED;
import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.browser.GitLab;
import hudson.plugins.git.browser.GithubWeb;
import static hudson.scm.PollingResult.*;
import hudson.Util;
import hudson.plugins.git.extensions.impl.ScmName;
import hudson.util.LogTaskListener;
import hudson.util.ReflectionUtils;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
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
    private boolean doGenerateSubmoduleConfigurations = false;

    @CheckForNull
    public String gitTool;
    @CheckForNull
    private GitRepositoryBrowser browser;
    private Collection<SubmoduleConfig> submoduleCfg = Collections.<SubmoduleConfig>emptyList();
    public static final String GIT_BRANCH = "GIT_BRANCH";
    public static final String GIT_LOCAL_BRANCH = "GIT_LOCAL_BRANCH";
    public static final String GIT_CHECKOUT_DIR = "GIT_CHECKOUT_DIR";
    public static final String GIT_COMMIT = "GIT_COMMIT";
    public static final String GIT_PREVIOUS_COMMIT = "GIT_PREVIOUS_COMMIT";
    public static final String GIT_PREVIOUS_SUCCESSFUL_COMMIT = "GIT_PREVIOUS_SUCCESSFUL_COMMIT";
    public static final String GIT_URL = "GIT_URL";

    /**
     * All the configured extensions attached to this.
     */
    @SuppressFBWarnings(value="SE_BAD_FIELD", justification="Known non-serializable field")
    private DescribableList<GitSCMExtension,GitSCMExtensionDescriptor> extensions;

    @Whitelisted
    @Deprecated
    public Collection<SubmoduleConfig> getSubmoduleCfg() {
        return submoduleCfg;
    }

    @DataBoundSetter
    public void setSubmoduleCfg(Collection<SubmoduleConfig> submoduleCfg) {
    }

    public static List<UserRemoteConfig> createRepoList(String url, String credentialsId) {
        List<UserRemoteConfig> repoList = new ArrayList<>();
        repoList.add(new UserRemoteConfig(url, null, null, credentialsId));
        return repoList;
    }

    /**
     * A convenience constructor that sets everything to default.
     *
     * @param repositoryUrl git repository URL
     *      Repository URL to clone from.
     */
    public GitSCM(String repositoryUrl) {
        this(
                createRepoList(repositoryUrl, null),
                Collections.singletonList(new BranchSpec("")),
                null, null, Collections.<GitSCMExtension>emptyList());
    }

    @Deprecated
    public GitSCM(
            List<UserRemoteConfig> userRemoteConfigs,
            List<BranchSpec> branches,
            Boolean doGenerateSubmoduleConfigurations,
            Collection<SubmoduleConfig> submoduleCfg,
            @CheckForNull GitRepositoryBrowser browser,
            @CheckForNull String gitTool,
            List<GitSCMExtension> extensions) {
        this(userRemoteConfigs, branches, browser, gitTool, extensions);
    }

    @DataBoundConstructor
    public GitSCM(
            List<UserRemoteConfig> userRemoteConfigs,
            List<BranchSpec> branches,
            @CheckForNull GitRepositoryBrowser browser,
            @CheckForNull String gitTool,
            List<GitSCMExtension> extensions) {

        // moved from createBranches
        this.branches = isEmpty(branches) ? newArrayList(new BranchSpec("*/master")) : branches;

        this.userRemoteConfigs = userRemoteConfigs;
        updateFromUserData();

        this.browser = browser;

        this.configVersion = 2L;
        this.gitTool = gitTool;

        this.extensions = new DescribableList<>(Saveable.NOOP,Util.fixNull(extensions));

        getBuildChooser(); // set the gitSCM field.
    }

    /**
     * All the configured extensions attached to this {@link GitSCM}.
     *
     * Going forward this is primarily how we'll support esoteric use cases.
     *
     * @since 2.0
     */
    @Whitelisted
    public DescribableList<GitSCMExtension, GitSCMExtensionDescriptor> getExtensions() {
        return extensions;
    }

    private void updateFromUserData() throws GitException {
        // do what newInstance used to do directly from the request data
        if (userRemoteConfigs == null) {
            return; /* Prevent NPE when no remote config defined */
        }
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

    @SuppressWarnings("deprecation") // `source` field is deprecated but required
    public Object readResolve() throws IOException {
        // Migrate data

        // Default unspecified to v0
        if (configVersion == null) {
            configVersion = 0L;
        }

        // Deprecated field needed to retain compatibility
        if (source != null) {
            remoteRepositories = new ArrayList<>();
            branches = new ArrayList<>();

            List<RefSpec> rs = new ArrayList<>();
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
            userRemoteConfigs = new ArrayList<>();
            for(RemoteConfig cfg : remoteRepositories) {
                // converted as in config.jelly
                String url = "";
                if (cfg.getURIs().size() > 0 && cfg.getURIs().get(0) != null)
                    url = cfg.getURIs().get(0).toPrivateString();

                String refspec = "";
                if (cfg.getFetchRefSpecs().size() > 0 && cfg.getFetchRefSpecs().get(0) != null)
                    refspec = cfg.getFetchRefSpecs().get(0).toString();

                userRemoteConfigs.add(new UserRemoteConfig(url, cfg.getName(), refspec, null));
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
            extensions = new DescribableList<>(Saveable.NOOP);

        readBackExtensionsFromLegacy();

        if (choosingStrategy != null && getBuildChooser().getClass()==DefaultBuildChooser.class) {
            for (BuildChooserDescriptor d : BuildChooser.all()) {
                if (choosingStrategy.equals(d.getLegacyId())) {
                    try {
                        setBuildChooser(d.clazz.newInstance());
                    } catch (InstantiationException | IllegalAccessException e) {
                        LOGGER.log(Level.WARNING, "Failed to instantiate the build chooser", e);
                    }
                }
            }
        }

        getBuildChooser(); // set the gitSCM field.

        return this;
    }

    @Override
    @Whitelisted
    public GitRepositoryBrowser getBrowser() {
        return browser;
    }

    public void setBrowser(GitRepositoryBrowser browser) {
        this.browser = browser;
    }

    private static final String HOSTNAME_MATCH
            = "([\\w\\d[-.]]+)" // hostname
            ;
    private static final String REPOSITORY_PATH_MATCH
            = "/*" // Zero or more slashes as start of repository path
            + "(.+?)" // repository path without leading slashes
            + "(?:[.]git)?" // optional '.git' suffix
            + "/*" // optional trailing '/'
            ;

    private static final Pattern[] URL_PATTERNS = {
        /* URL style - like https://github.com/jenkinsci/git-plugin */
        Pattern.compile(
        "(?:\\w+://)" // protocol (scheme)
        + "(?:.+@)?" // optional username/password
        + HOSTNAME_MATCH
        + "(?:[:][\\d]+)?" // optional port number (only honored by git for ssh:// scheme)
        + "/" // separator between hostname and repository path - '/'
        + REPOSITORY_PATH_MATCH
        ),
        /* Alternate ssh style - like git@github.com:jenkinsci/git-plugin */
        Pattern.compile(
        "(?:git@)" // required username (only optional if local username is 'git')
        + HOSTNAME_MATCH
        + ":" // separator between hostname and repository path - ':'
        + REPOSITORY_PATH_MATCH
        )
    };

    @Override public RepositoryBrowser<?> guessBrowser() {
        Set<String> webUrls = new HashSet<>();
        if (remoteRepositories != null) {
            for (RemoteConfig config : remoteRepositories) {
                for (URIish uriIsh : config.getURIs()) {
                    String uri = uriIsh.toString();
                    for (Pattern p : URL_PATTERNS) {
                        Matcher m = p.matcher(uri);
                        if (m.matches()) {
                            webUrls.add("https://" + m.group(1) + "/" + m.group(2) + "/");
                        }
                    }
                }
            }
        }
        if (webUrls.isEmpty()) {
            return null;
        }
        if (webUrls.size() == 1) {
            String url = webUrls.iterator().next();
            if (url.startsWith("https://bitbucket.org/")) {
                return new BitbucketWeb(url);
            }
            if (url.startsWith("https://gitlab.com/")) {
                return new GitLab(url);
            }
            if (url.startsWith("https://github.com/")) {
                return new GithubWeb(url);
            }
            return null;
        }
        LOGGER.log(Level.INFO, "Multiple browser guess matches for {0}", remoteRepositories);
        return null;
    }

    public boolean isCreateAccountBasedOnEmail() {
        DescriptorImpl gitDescriptor = getDescriptor();
        return (gitDescriptor != null && gitDescriptor.isCreateAccountBasedOnEmail());
    }

    public boolean isUseExistingAccountWithSameEmail() {
        DescriptorImpl gitDescriptor = getDescriptor();
        return (gitDescriptor != null && gitDescriptor.isUseExistingAccountWithSameEmail());
    }

    public boolean isHideCredentials() {
        DescriptorImpl gitDescriptor = getDescriptor();
        return gitDescriptor != null && gitDescriptor.isHideCredentials();
    }

    public boolean isAllowSecondFetch() {
        DescriptorImpl gitDescriptor = getDescriptor();
        return (gitDescriptor != null && gitDescriptor.isAllowSecondFetch());
    }

    public boolean isDisableGitToolChooser() {
        DescriptorImpl gitDescriptor = getDescriptor();
        return (gitDescriptor != null && gitDescriptor.isDisableGitToolChooser());
    }

    public boolean isAddGitTagAction() {
        DescriptorImpl gitDescriptor = getDescriptor();
        return (gitDescriptor != null && gitDescriptor.isAddGitTagAction());
    }

    @Whitelisted
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

    @Deprecated
    public String getParamLocalBranch(Run<?, ?> build) throws IOException, InterruptedException {
        return getParamLocalBranch(build, new LogTaskListener(LOGGER, Level.INFO));
    }

    /**
     * Gets the parameter-expanded effective value in the context of the current build.
     * @param build run whose local branch name is returned
     * @param listener build log
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @return parameter-expanded local branch name in build.
     */
    public String getParamLocalBranch(Run<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
        LocalBranch localBranch = getExtensions().get(LocalBranch.class);
        // substitute build parameters if available
        return getParameterString(localBranch == null ? null : localBranch.getLocalBranch(), build.getEnvironment(listener));
    }

    @Deprecated
    public List<RemoteConfig> getParamExpandedRepos(Run<?, ?> build) throws IOException, InterruptedException {
        return getParamExpandedRepos(build, new LogTaskListener(LOGGER, Level.INFO));
    }

    /**
     * Expand parameters in {@link #remoteRepositories} with the parameter values provided in the given build
     * and return them.
     *
     * @param build run whose local branch name is returned
     * @param listener build log
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     * @return can be empty but never null.
     */
    public List<RemoteConfig> getParamExpandedRepos(Run<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
        List<RemoteConfig> expandedRepos = new ArrayList<>();

        EnvVars env = build.getEnvironment(listener);

        for (RemoteConfig oldRepo : Util.fixNull(remoteRepositories)) {
            expandedRepos.add(getParamExpandedRepo(env, oldRepo));
        }

        return expandedRepos;
    }

    /**
     * Expand Parameters in the supplied remote repository with the parameter values provided in the given environment variables
     * @param env Environment variables with parameter values
     * @param remoteRepository Remote repository with parameters
     * @return remote repository with expanded parameters
     */
    public RemoteConfig getParamExpandedRepo(EnvVars env, RemoteConfig remoteRepository) {
        List<RefSpec> refSpecs = getRefSpecs(remoteRepository, env);
    	return newRemoteConfig(
                getParameterString(remoteRepository.getName(), env),
                getParameterString(remoteRepository.getURIs().get(0).toPrivateString(), env),
                refSpecs.toArray(new RefSpec[0]));
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
    @Whitelisted
    public List<UserRemoteConfig> getUserRemoteConfigs() {
        if (userRemoteConfigs == null) {
            /* Prevent NPE when no remote config defined */
            userRemoteConfigs = new ArrayList<>();
        }
        return Collections.unmodifiableList(userRemoteConfigs);
    }

    @Whitelisted
    public List<RemoteConfig> getRepositories() {
        // Handle null-value to ensure backwards-compatibility, ie project configuration missing the <repositories/> XML element
        if (remoteRepositories == null) {
            return new ArrayList<>();
        }
        return remoteRepositories;
    }

    /**
     * Derives a local branch name from the remote branch name by removing the
     * name of the remote from the remote branch name.
     * <p>
     * Ex. origin/master becomes master
     * <p>
     * Cycles through the list of user remotes looking for a match allowing user
     * to configure an alternate (not origin) name for the remote.
     *
     * @param remoteBranchName branch name whose remote repository name will be removed
     * @return a local branch name derived by stripping the remote repository
     *         name from the {@code remoteBranchName} parameter. If a matching
     *         remote is not found, the original {@code remoteBranchName} will
     *         be returned.
     */
    public String deriveLocalBranchName(String remoteBranchName) {
        // default remoteName is 'origin' used if list of user remote configs is empty.
        String remoteName = "origin";

        for (final UserRemoteConfig remote : getUserRemoteConfigs()) {
            remoteName = remote.getName();
            if (remoteName == null || remoteName.isEmpty()) {
                remoteName = "origin";
            }
            if (remoteBranchName.startsWith(remoteName + "/")) {
                // found the remote config associated with remoteBranchName
                break;
            }
        }

        // now strip the remote name and return the resulting local branch name.
        String localBranchName = remoteBranchName.replaceFirst("^" + remoteName + "/", "");
        return localBranchName;
    }

    @CheckForNull
    @Whitelisted
    public String getGitTool() {
        return gitTool;
    }

    @NonNull
    public static String getParameterString(@CheckForNull String original, @NonNull EnvVars env) {
        return env.expand(original);
    }

    private List<RefSpec> getRefSpecs(RemoteConfig repo, EnvVars env) {
        List<RefSpec> refSpecs = new ArrayList<>();
        for (RefSpec refSpec : repo.getFetchRefSpecs()) {
            refSpecs.add(new RefSpec(getParameterString(refSpec.toString(), env)));
        }
        return refSpecs;
    }

    /**
     * If the configuration is such that we are tracking just one branch of one repository
     * return that branch specifier (in the form of something like "origin/master" or a SHA1-hash
     *
     * Otherwise return [@code null}.
     */
    @CheckForNull
    private String getSingleBranch(EnvVars env) {
        // if we have multiple branches skip to advanced usecase
        if (getBranches().size() != 1) {
            return null;
        }
        String branch = getBranches().get(0).getName();
        String repository = null;

        if (getRepositories().size() != 1) {
        	for (RemoteConfig repo : getRepositories()) {
        		if (branch.startsWith(repo.getName() + "/")) {
        			repository = repo.getName();
        			break;
        		}
        	}
        } else {
        	repository = getRepositories().get(0).getName();
        }


        // replace repository wildcard with repository name
        if (branch.startsWith("*/") && repository != null) {
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
    public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> abstractBuild, FilePath workspace, Launcher launcher, TaskListener taskListener) throws IOException, InterruptedException {
        return SCMRevisionState.NONE;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        // TODO would need to use hudson.plugins.git.util.GitUtils.getPollEnvironment
        return requiresWorkspaceForPolling(new EnvVars());
    }

    /* Package protected for test access */
    boolean requiresWorkspaceForPolling(EnvVars environment) {
        for (GitSCMExtension ext : getExtensions()) {
            if (ext.requiresWorkspaceForPolling()) return true;
        }
        return getSingleBranch(environment) == null;
    }

    @Override
    public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath workspace, final TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        try {
            return compareRemoteRevisionWithImpl( project, launcher, workspace, listener);
        } catch (GitException e){
            throw new IOException(e);
        }
    }

    public static final Pattern GIT_REF = Pattern.compile("^(refs/[^/]+)/(.+)");

    private PollingResult compareRemoteRevisionWithImpl(Job<?, ?> project, Launcher launcher, FilePath workspace, final @NonNull TaskListener listener) throws IOException, InterruptedException {
        // Poll for changes. Are there any unbuilt revisions that Hudson ought to build ?

        listener.getLogger().println("Using strategy: " + getBuildChooser().getDisplayName());

        final Run lastBuild = project.getLastBuild();
        if (lastBuild == null) {
            // If we've never been built before, well, gotta build!
            listener.getLogger().println("[poll] No previous build, so forcing an initial build.");
            return BUILD_NOW;
        }

        final BuildData buildData = fixNull(getBuildData(lastBuild));
        if (buildData.lastBuild != null) {
            listener.getLogger().println("[poll] Last Built Revision: " + buildData.lastBuild.revision);
        }

        final EnvVars pollEnv = project instanceof AbstractProject ? GitUtils.getPollEnvironment((AbstractProject) project, workspace, launcher, listener, false) : lastBuild.getEnvironment(listener);

        final String singleBranch = getSingleBranch(pollEnv);

        if (!requiresWorkspaceForPolling(pollEnv)) {

            final EnvVars environment = project instanceof AbstractProject ? GitUtils.getPollEnvironment((AbstractProject) project, workspace, launcher, listener, false) : new EnvVars();

            GitClient git = createClient(listener, environment, project, Jenkins.get(), null);

            for (RemoteConfig remoteConfig : getParamExpandedRepos(lastBuild, listener)) {
                String remote = remoteConfig.getName();
                List<RefSpec> refSpecs = getRefSpecs(remoteConfig, environment);

                for (URIish urIish : remoteConfig.getURIs()) {
                    String gitRepo = urIish.toString();
                    Map<String, ObjectId> heads = git.getHeadRev(gitRepo);
                    if (heads==null || heads.isEmpty()) {
                        listener.getLogger().println("[poll] Couldn't get remote head revision");
                        return BUILD_NOW;
                    }

                    listener.getLogger().println("Found "+ heads.size() +" remote heads on " + urIish);

                    Iterator<Entry<String, ObjectId>> it = heads.entrySet().iterator();
                    while (it.hasNext()) {
                        String head = it.next().getKey();
                        boolean match = false;
                        for (RefSpec spec : refSpecs) {
                            if (spec.matchSource(head)) {
                                match = true;
                                break;
                            }
                        }
                        if (!match) {
                            listener.getLogger().println("Ignoring " + head + " as it doesn't match any of the configured refspecs");
                            it.remove();
                        }
                    }

                    for (BranchSpec branchSpec : getBranches()) {
                        for (Entry<String, ObjectId> entry : heads.entrySet()) {
                            final String head = entry.getKey();
                            // head is "refs/(heads|tags|whatever)/branchName

                            // first, check the a canonical git reference is configured
                            if (!branchSpec.matches(head, environment)) {

                                // convert head `refs/(heads|tags|whatever)/branch` into shortcut notation `remote/branch`
                                String name;
                                Matcher matcher = GIT_REF.matcher(head);
                                if (matcher.matches()) name = remote + head.substring(matcher.group(1).length());
                                else name = remote + "/" + head;

                                if (!branchSpec.matches(name, environment)) continue;
                            }

                            final ObjectId sha1 = entry.getValue();
                            Build built = buildData.getLastBuild(sha1);
                            if (built != null) {
                                listener.getLogger().println("[poll] Latest remote head revision on " + head + " is: " + sha1.getName() + " - already built by " + built.getBuildNumber());
                                continue;
                            }

                            listener.getLogger().println("[poll] Latest remote head revision on " + head + " is: " + sha1.getName());
                            return BUILD_NOW;
                        }
                    }
                }
            }
            return NO_CHANGES;
        }

        final Node node = GitUtils.workspaceToNode(workspace);
        final EnvVars environment = project instanceof AbstractProject ? GitUtils.getPollEnvironment((AbstractProject) project, workspace, launcher, listener) : project.getEnvironment(node, listener);

        FilePath workingDirectory = workingDirectory(project,workspace,environment,listener);

        // (Re)build if the working directory doesn't exist
        if (workingDirectory == null || !workingDirectory.exists()) {
            listener.getLogger().println("[poll] Working Directory does not exist");
            return BUILD_NOW;
        }

        GitClient git = createClient(listener, environment, project, node, workingDirectory);

        if (git.hasGitRepo(false)) {
            // Repo is there - do a fetch
            listener.getLogger().println("Fetching changes from the remote Git repositories");

            // Fetch updates
            for (RemoteConfig remoteRepository : getParamExpandedRepos(lastBuild, listener)) {
                fetchFrom(git, null, listener, remoteRepository);
            }

            listener.getLogger().println("Polling for changes in");

            Collection<Revision> candidates = getBuildChooser().getCandidateRevisions(
                    true, singleBranch, git, listener, buildData, new BuildChooserContextImpl(project, null, environment));

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
     * @param listener build log
     * @param environment environment variables to be used
     * @param build run context for the returned GitClient
     * @param workspace client workspace
     * @return git client for additional git operations
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     */
    @NonNull
    public GitClient createClient(TaskListener listener, EnvVars environment, Run<?,?> build, FilePath workspace) throws IOException, InterruptedException {
        FilePath ws = workingDirectory(build.getParent(), workspace, environment, listener);
        /* ws will be null if the node which ran the build is offline */
        if (ws != null) {
            ws.mkdirs(); // ensure it exists
        }
        return createClient(listener,environment, build.getParent(), GitUtils.workspaceToNode(workspace), ws, null);
    }

    /**
     * Allows {@link Publisher} and other post build actions to access a configured {@link GitClient}.
     * The post build action can use the {@code postBuildUnsupportedCommand} argument to control the
     * selection of a git tool by {@link GitToolChooser}.
     * @param listener build log
     * @param environment environment variables to be used
     * @param build run context for the returned GitClient
     * @param workspace client workspace
     * @param postBuildUnsupportedCommand passed by caller to control choice of git tool by GitTooChooser
     * @return git client for additional git operations
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     */
    @NonNull
    public GitClient createClient(TaskListener listener, EnvVars environment, Run<?,?> build, FilePath workspace, UnsupportedCommand postBuildUnsupportedCommand) throws IOException, InterruptedException {
        FilePath ws = workingDirectory(build.getParent(), workspace, environment, listener);
        /* ws will be null if the node which ran the build is offline */
        if (ws != null) {
            ws.mkdirs(); // ensure it exists
        }
        return createClient(listener,environment, build.getParent(), GitUtils.workspaceToNode(workspace), ws, postBuildUnsupportedCommand);

    }

    @NonNull
    /*package*/ GitClient createClient(TaskListener listener, EnvVars environment, Job project, Node n, FilePath ws) throws IOException, InterruptedException {
        return createClient(listener, environment, project, n, ws, null);
    }

    @NonNull
    /*package*/ GitClient createClient(TaskListener listener, EnvVars environment, Job project, Node n, FilePath ws, UnsupportedCommand postBuildUnsupportedCommand) throws IOException, InterruptedException {

        if (postBuildUnsupportedCommand == null) {
            /* UnsupportedCommand supports JGit by default */
            postBuildUnsupportedCommand = new UnsupportedCommand();
        }

        String gitExe = getGitExe(n, listener);

        GitTool gitTool = getGitTool(n, null, listener);

        if (!isDisableGitToolChooser()) {
            UnsupportedCommand unsupportedCommand = new UnsupportedCommand();
            for (GitSCMExtension ext : extensions) {
                ext.determineSupportForJGit(this, unsupportedCommand);
            }
            GitToolChooser chooser = null;
            for (UserRemoteConfig uc : getUserRemoteConfigs()) {
                String ucCredentialsId = uc.getCredentialsId();
                String url = getParameterString(uc.getUrl(), environment);
                /* If any of the extensions do not support JGit, it should not be suggested */
                /* If the post build action does not support JGit, it should not be suggested */
                chooser = new GitToolChooser(url, project, ucCredentialsId, gitTool, n, listener,
                                             unsupportedCommand.determineSupportForJGit() && postBuildUnsupportedCommand.determineSupportForJGit());
            }
            if (chooser != null) {
                listener.getLogger().println("The recommended git tool is: " + chooser.getGitTool());
                String updatedGitExe = chooser.getGitTool();
                
                if (!updatedGitExe.equals("NONE")) {
                    gitExe = updatedGitExe;
                }
            }
        }
        Git git = Git.with(listener, environment).in(ws).using(gitExe);

        GitClient c = git.getClient();
        for (GitSCMExtension ext : extensions) {
            c = ext.decorate(this,c);
        }

        for (UserRemoteConfig uc : getUserRemoteConfigs()) {
            String ucCredentialsId = uc.getCredentialsId();
            if (ucCredentialsId == null) {
                listener.getLogger().println("No credentials specified");
            } else {
                String url = getParameterString(uc.getUrl(), environment);
                StandardUsernameCredentials credentials = lookupScanCredentials(project, url, ucCredentialsId);
                if (credentials != null) {
                    c.addCredentials(url, credentials);
                    if(!isHideCredentials()) {
                        listener.getLogger().println(format("using credential %s", credentials.getId()));
                    }
                    if (project != null && project.getLastBuild() != null) {
                        CredentialsProvider.track(project.getLastBuild(), credentials);
                    }
                } else {
                    if(!isHideCredentials()) {
                        listener.getLogger().println(format("Warning: CredentialId \"%s\" could not be found.", ucCredentialsId));
                    }
                }
            }
        }
        // TODO add default credentials

        return c;
    }

    private static StandardUsernameCredentials lookupScanCredentials(@CheckForNull Item project,
                                                              @CheckForNull String url,
                                                              @CheckForNull String ucCredentialsId) {
        if (Util.fixEmpty(ucCredentialsId) == null) {
            return null;
        } else {
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            project,
                            project instanceof Queue.Task
                                    ? ((Queue.Task) project).getDefaultAuthentication()
                                    : ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(url).build()
                    ),
                    CredentialsMatchers.allOf(CredentialsMatchers.withId(ucCredentialsId), GitClient.CREDENTIALS_MATCHER)
            );
        }
    }

    private static CredentialsMatcher gitScanCredentialsMatcher() {
        return CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
    }

    @NonNull
    private BuildData fixNull(BuildData bd) {
        ScmName sn = getExtensions().get(ScmName.class);
        String scmName = sn == null ? null : sn.getName();
        return bd != null ? bd : new BuildData(scmName, getUserRemoteConfigs());
    }

    /**
     * Fetch information from a particular remote repository.
     *
     * @param git git client
     * @param run run context if it's running for build
     * @param listener build log
     * @param remoteRepository remote git repository
     * @throws InterruptedException when interrupted
     * @throws IOException on input or output error
     */
    private void fetchFrom(GitClient git,
            @CheckForNull Run<?, ?> run,
            TaskListener listener,
            RemoteConfig remoteRepository) throws InterruptedException, IOException {

        boolean first = true;
        for (URIish url : remoteRepository.getURIs()) {
            try {
                if (first) {
                    git.setRemoteUrl(remoteRepository.getName(), url.toPrivateASCIIString());
                    first = false;
                } else {
                    git.addRemoteUrl(remoteRepository.getName(), url.toPrivateASCIIString());
                }

                FetchCommand fetch = git.fetch_().from(url, remoteRepository.getFetchRefSpecs());
                for (GitSCMExtension extension : extensions) {
                    extension.decorateFetchCommand(this, run, git, listener, fetch);
                }
                fetch.execute();
            } catch (GitException ex) {
                throw new GitException("Failed to fetch from "+url.toString(), ex);
            }
        }
    }

    private RemoteConfig newRemoteConfig(String name, String refUrl, RefSpec... refSpec) {

        try {
            Config repoConfig = new Config();
            // Make up a repo config from the request parameters

            repoConfig.setString("remote", name, "url", refUrl);
            List<String> str = new ArrayList<>();
            if(refSpec != null && refSpec.length > 0)
                for (RefSpec rs: refSpec)
                    str.add(rs.toString());
            repoConfig.setStringList("remote", name, "fetch", str);

            return RemoteConfig.getAllRemoteConfigs(repoConfig).get(0);
        } catch (Exception ex) {
            throw new GitException("Error trying to create JGit configuration", ex);
        }
    }

    @CheckForNull
    public GitTool resolveGitTool(TaskListener listener) {
        return GitUtils.resolveGitTool(gitTool, listener);
    }

    public String getGitExe(Node builtOn, TaskListener listener) {
        return getGitExe(builtOn, null, listener);
    }

    /**
     * Exposing so that we can get this from GitPublisher.
     * @param builtOn node where build was performed
     * @param env environment variables used in the build
     * @param listener build log
     * @return git exe for builtOn node, often "Default" or "jgit"
     */
    public String getGitExe(Node builtOn, EnvVars env, TaskListener listener) {
        GitTool tool = GitUtils.resolveGitTool(gitTool, builtOn, env, listener);
        if(tool == null) {
            return null;
        }
        return tool.getGitExe();
    }

    public GitTool getGitTool(Node builtOn, EnvVars env, TaskListener listener) {
        GitTool tool = GitUtils.resolveGitTool(gitTool, builtOn, env, listener);
        return tool;
    }

    /*package*/ static class BuildChooserContextImpl implements BuildChooserContext, Serializable {
        @SuppressFBWarnings(value="SE_BAD_FIELD", justification="known non-serializable field")
        final Job project;
        @SuppressFBWarnings(value="SE_BAD_FIELD", justification="known non-serializable field")
        final Run build;
        final EnvVars environment;

        BuildChooserContextImpl(Job project, Run build, EnvVars environment) {
            this.project = project;
            this.build = build;
            this.environment = environment;
        }

        public <T> T actOnBuild(@NonNull ContextCallable<Run<?,?>, T> callable) throws IOException, InterruptedException {
            return callable.invoke(build, FilePath.localChannel);
        }

        public <T> T actOnProject(@NonNull ContextCallable<Job<?,?>, T> callable) throws IOException, InterruptedException {
            return callable.invoke(project, FilePath.localChannel);
        }

        public Run<?, ?> getBuild() {
            return build;
        }

        public EnvVars getEnvironment() {
            return environment;
        }

        private Object writeReplace() {
            Channel currentChannel = Channel.current();
            if (currentChannel == null) {
                return null;
            }
            return currentChannel.export(BuildChooserContext.class,new BuildChooserContext() {
                public <T> T actOnBuild(@NonNull ContextCallable<Run<?,?>, T> callable) throws IOException, InterruptedException {
                    return callable.invoke(build,Channel.current());
                }

                public <T> T actOnProject(@NonNull ContextCallable<Job<?,?>, T> callable) throws IOException, InterruptedException {
                    return callable.invoke(project,Channel.current());
                }

                public Run<?, ?> getBuild() {
                    return build;
                }

                public EnvVars getEnvironment() {
                    return environment;
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
    private @NonNull Build determineRevisionToBuild(final Run build,
                                              final @NonNull BuildData buildData,
                                              final EnvVars environment,
                                              final @NonNull GitClient git,
                                              final @NonNull TaskListener listener) throws IOException, InterruptedException {
        PrintStream log = listener.getLogger();
        Collection<Revision> candidates = Collections.emptyList();
        final BuildChooserContext context = new BuildChooserContextImpl(build.getParent(), build, environment);
        getBuildChooser().prepareWorkingTree(git, listener, context);

        if (build.getClass().getName().equals("hudson.matrix.MatrixRun")) {
            candidates = GitSCMMatrixUtil.populateCandidatesFromRootBuild((AbstractBuild) build, this);
        }

        // parameter forcing the commit ID to build
        if (candidates.isEmpty() ) {
            final RevisionParameterAction rpa = build.getAction(RevisionParameterAction.class);
            if (rpa != null) {
                // in case the checkout is due to a commit notification on a
                // multiple scm configuration, it should be verified if the triggering repo remote
                // matches current repo remote to avoid JENKINS-26587
                if (rpa.canOriginateFrom(this.getRepositories())) {
                    candidates = Collections.singleton(rpa.toRevision(git));
                } else {
                    log.println("skipping resolution of commit " + rpa.commit + ", since it originates from another repository");
                }
            }
        }

        if (candidates.isEmpty() ) {
            final String singleBranch = environment.expand( getSingleBranch(environment) );

            candidates = getBuildChooser().getCandidateRevisions(
                    false, singleBranch, git, listener, buildData, context);
        }

        if (candidates.isEmpty()) {
            // getBuildCandidates should make the last item the last build, so a re-build
            // will build the last built thing.
            throw new AbortException("Couldn't find any revision to build. Verify the repository and branch configuration for this job.");
        }

        Revision marked = candidates.iterator().next();
        Revision rev = marked;
        // Modify the revision based on extensions
        for (GitSCMExtension ext : extensions) {
            rev = ext.decorateRevisionToBuild(this,build,git,listener,marked,rev);
        }
        Build revToBuild = new Build(marked, rev, build.getNumber(), null);
        buildData.saveBuild(revToBuild);

        if (buildData.getBuildsByBranchName().size() >= 100) {
            log.println("JENKINS-19022: warning: possible memory leak due to Git plugin usage; see: https://wiki.jenkins.io/display/JENKINS/Remove+Git+Plugin+BuildsByBranch+BuildData");
        }
        boolean checkForMultipleRevisions = true;
        BuildSingleRevisionOnly ext = extensions.get(BuildSingleRevisionOnly.class);
        if (ext != null) {
            checkForMultipleRevisions = ext.enableMultipleRevisionDetection();
        }

        if (candidates.size() > 1) {
            log.println("Multiple candidate revisions");
            if (checkForMultipleRevisions) {
                Job<?, ?> job = build.getParent();
                if (job instanceof AbstractProject) {
                    AbstractProject project = (AbstractProject) job;
                    if (!project.isDisabled()) {
                        log.println("Scheduling another build to catch up with " + project.getFullDisplayName());
                        if (!project.scheduleBuild(0, new SCMTrigger.SCMTriggerCause("This build was triggered by build "
                                + build.getNumber() + " because more than one build candidate was found."))) {
                            log.println("WARNING: multiple candidate revisions, but unable to schedule build of " + project.getFullDisplayName());
                        }
                    }
                }
            }
        }
        return revToBuild;
    }

    /**
     * Retrieve Git objects from the specified remotes by doing the likes of clone/fetch/pull/etc.
     *
     * By the end of this method, remote refs are updated to include all the commits found in the remote servers.
     */
    private void retrieveChanges(Run build, GitClient git, TaskListener listener) throws IOException, InterruptedException {
        final PrintStream log = listener.getLogger();

        boolean removeSecondFetch = false;
        List<RemoteConfig> repos = getParamExpandedRepos(build, listener);
        if (repos.isEmpty())    return; // defensive check even though this is an invalid configuration

        if (git.hasGitRepo(false)) {
            // It's an update
            if (repos.size() == 1)
                log.println("Fetching changes from the remote Git repository");
            else
                log.println(MessageFormat.format("Fetching changes from {0} remote Git repositories", repos.size()));
        } else {
            log.println("Cloning the remote Git repository");

            RemoteConfig rc = repos.get(0);
            try {
                CloneCommand cmd = git.clone_().url(rc.getURIs().get(0).toPrivateString()).repositoryName(rc.getName());
                for (GitSCMExtension ext : extensions) {
                    ext.decorateCloneCommand(this, build, git, listener, cmd);
                }
                cmd.execute();
                // determine if second fetch is required
                CloneOption option = extensions.get(CloneOption.class);
                if (!isAllowSecondFetch()) {
                    removeSecondFetch = determineSecondFetch(option, rc);
                }
            } catch (GitException ex) {
                ex.printStackTrace(listener.error("Error cloning remote repo '" + rc.getName() + "'"));
                throw new AbortException("Error cloning remote repo '" + rc.getName() + "'");
            }
        }

        for (RemoteConfig remoteRepository : repos) {
            if (remoteRepository.equals(repos.get(0)) && removeSecondFetch){
                log.println("Avoid second fetch");
                continue;
            }
            try {
                fetchFrom(git, build, listener, remoteRepository);
            } catch (GitException ex) {
                /* Allow retry by throwing AbortException instead of
                 * GitException. See JENKINS-20531. */
                ex.printStackTrace(listener.error("Error fetching remote repo '" + remoteRepository.getName() + "'"));
                throw new AbortException("Error fetching remote repo '" + remoteRepository.getName() + "'");
            }
        }
    }

    private boolean determineSecondFetch(CloneOption option, @NonNull RemoteConfig rc) {
        List<RefSpec> initialFetchRefSpecs = rc.getFetchRefSpecs();
        boolean isDefaultRefspec = true; // default refspec is any refspec with "refs/heads/" mapping
        boolean removeSecondFetch = true;
        if (initialFetchRefSpecs != null) {
            for (RefSpec ref : initialFetchRefSpecs) {
                if (!ref.toString().contains("refs/heads")) {
                    isDefaultRefspec = false; // if refspec is not of default type, preserve second fetch
                }
            }
            if (option == null) {
                removeSecondFetch = isDefaultRefspec;
            } else {
                if (option.isHonorRefspec()) {
                    removeSecondFetch = true; // avoid second fetch call if honor refspec is enabled
                } else {
                    removeSecondFetch = isDefaultRefspec;
                }
            }
        }
        // if initial fetch refspec contains "refs/heads/*" (default refspec), ignore the second fetch call
        return removeSecondFetch;
    }

    @Override
    public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline)
            throws IOException, InterruptedException {

        if (VERBOSE)
            listener.getLogger().println("Using checkout strategy: " + getBuildChooser().getDisplayName());

        BuildData previousBuildData = getBuildData(build.getPreviousBuild());   // read only
        BuildData buildData = copyBuildData(build.getPreviousBuild());

        if (VERBOSE && buildData.lastBuild != null) {
            listener.getLogger().println("Last Built Revision: " + buildData.lastBuild.revision);
        }

        EnvVars environment = build.getEnvironment(listener);
        GitClient git = createClient(listener, environment, build, workspace);

        if (launcher instanceof Launcher.DecoratedLauncher) {
            // We cannot check for git instanceof CliGitAPIImpl vs. JGitAPIImpl here since (when running on an agent) we will actually have a RemoteGitImpl which is opaque.
            listener.getLogger().println("Warning: JENKINS-30600: special launcher " + launcher + " will be ignored (a typical symptom is the Git executable not being run inside a designated container)");
        }

        for (GitSCMExtension ext : extensions) {
            ext.beforeCheckout(this, build, git, listener);
        }

        retrieveChanges(build, git, listener);
        Build revToBuild = determineRevisionToBuild(build, buildData, environment, git, listener);

        // Track whether we're trying to add a duplicate BuildData, now that it's been updated with
        // revision info for this build etc. The default assumption is that it's a duplicate.
        boolean buildDataAlreadyPresent = false;
        List<BuildData> actions = build.getActions(BuildData.class);
        for (BuildData d: actions)  {
            if (d.similarTo(buildData)) {
                buildDataAlreadyPresent = true;
                break;
            }
        }
        if (!actions.isEmpty()) {
            buildData.setIndex(actions.size()+1);
        }

        // If the BuildData is not already attached to this build, add it to the build and mark that
        // it wasn't already present, so that we add the GitTagAction and changelog after the checkout
        // finishes.
        if (!buildDataAlreadyPresent) {
            build.addAction(buildData);
        }

        environment.put(GIT_COMMIT, revToBuild.revision.getSha1String());
        Branch localBranch = Iterables.getFirst(revToBuild.revision.getBranches(),null);
        String localBranchName = getParamLocalBranch(build, listener);
        if (localBranch != null && localBranch.getName() != null) { // null for a detached HEAD
            String remoteBranchName = getBranchName(localBranch);
            environment.put(GIT_BRANCH, remoteBranchName);

            LocalBranch lb = getExtensions().get(LocalBranch.class);
            if (lb != null) {
                String lbn = lb.getLocalBranch();
                if (lbn == null || lbn.equals("**")) {
                  // local branch is configured with empty value or "**" so use remote branch name for checkout
                  localBranchName = deriveLocalBranchName(remoteBranchName);
               }
               environment.put(GIT_LOCAL_BRANCH, localBranchName);
            }
        }

        listener.getLogger().println("Checking out " + revToBuild.revision);

        CheckoutCommand checkoutCommand = git.checkout().branch(localBranchName).ref(revToBuild.revision.getSha1String()).deleteBranchIfExist(true);
        for (GitSCMExtension ext : this.getExtensions()) {
            ext.decorateCheckoutCommand(this, build, git, listener, checkoutCommand);
        }

        try {
          checkoutCommand.execute();
        } catch (GitLockFailedException e) {
            // Rethrow IOException so the retry will be able to catch it
            throw new IOException("Could not checkout " + revToBuild.revision.getSha1String(), e);
        }

        // Needs to be after the checkout so that revToBuild is in the workspace
        try {
            printCommitMessageToLog(listener, git, revToBuild);
        } catch (IOException | ArithmeticException | GitException ge) {
            // JENKINS-45729 reports a git exception when revToBuild cannot be found in the workspace.
            // JENKINS-46628 reports a git exception when revToBuild cannot be found in the workspace.
            // JENKINS-62710 reports a JGit arithmetic exception on an older Java 8 system.
            // Don't let those exceptions block the build, this is an informational message only
            listener.getLogger().println("Exception logging commit message for " + revToBuild + ": " + ge.getMessage());
        }

        // Don't add the tag and changelog if we've already processed this BuildData before.
        if (!buildDataAlreadyPresent) {
            if (build.getActions(AbstractScmTagAction.class).isEmpty() && isAddGitTagAction()) {
                // only add the tag action if we can be unique as AbstractScmTagAction has a fixed UrlName
                // so only one of the actions is addressable by users
                LOGGER.log(Level.FINE, "Adding GitTagAction to build " + build.number);
                build.addAction(new GitTagAction(build, workspace, revToBuild.revision));
            } else {
                LOGGER.log(Level.FINE, "Not adding GitTagAction to build " + build.number);
            }

            if (changelogFile != null) {
                computeChangeLog(git, revToBuild.revision, listener, previousBuildData, new FilePath(changelogFile),
                        new BuildChooserContextImpl(build.getParent(), build, environment));
            }
        }

        for (GitSCMExtension ext : extensions) {
            ext.onCheckoutCompleted(this, build, git,listener);
        }
    }

    private void printCommitMessageToLog(TaskListener listener, GitClient git, final Build revToBuild)
            throws IOException {
        try {
            RevCommit commit = git.withRepository(new RevCommitRepositoryCallback(revToBuild));
            listener.getLogger().println("Commit message: \"" + commit.getShortMessage() + "\"");
        } catch (InterruptedException | MissingObjectException e) {
            e.printStackTrace(listener.error("Unable to retrieve commit message"));
        }
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
     * If Jenkins built B1, C1, B2, C3 in that order, then one'd prefer that the changelog of B2 only shows
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
    private void computeChangeLog(GitClient git, Revision revToBuild, TaskListener listener, BuildData previousBuildData, FilePath changelogFile, BuildChooserContext context) throws IOException, InterruptedException {
        boolean executed = false;
        ChangelogCommand changelog = git.changelog();
        changelog.includes(revToBuild.getSha1());
        try (Writer out = new OutputStreamWriter(changelogFile.write(),"UTF-8")) {
            boolean exclusion = false;
            ChangelogToBranch changelogToBranch = getExtensions().get(ChangelogToBranch.class);
            if (changelogToBranch != null) {
                listener.getLogger().println("Using 'Changelog to branch' strategy.");
                changelog.excludes(changelogToBranch.getOptions().getRef());
                exclusion = true;
            } else {
                for (Branch b : revToBuild.getBranches()) {
                    Build lastRevWas = getBuildChooser().prevBuildForChangelog(b.getName(), previousBuildData, git, context);
                    if (lastRevWas != null && lastRevWas.revision != null && git.isCommitInRepo(lastRevWas.getSHA1())) {
                        changelog.excludes(lastRevWas.getSHA1());
                        exclusion = true;
                    }
                }
            }

            if (!exclusion) {
                // this is the first time we are building this branch, so there's no base line to compare against.
                // if we force the changelog, it'll contain all the changes in the repo, which is not what we want.
                listener.getLogger().println("First time build. Skipping changelog.");
            } else {
                changelog.to(out).max(MAX_CHANGELOG).execute();
                executed = true;
            }
        } catch (GitException ge) {
            ge.printStackTrace(listener.error("Unable to retrieve changeset"));
        } finally {
            if (!executed) changelog.abort();
        }
    }

    @Override
    @Deprecated // Overrides a deprecated implementation, must also be deprecated
    public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
        buildEnvironment(build, env);
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, java.util.Map<String, String> env) {
        Revision rev = fixNull(getBuildData(build)).getLastBuiltRevision();
        if (rev!=null) {
            Branch branch = Iterables.getFirst(rev.getBranches(), null);
            if (branch!=null && branch.getName()!=null) {
               String remoteBranchName = getBranchName(branch);
                env.put(GIT_BRANCH, remoteBranchName);

                // TODO this is unmodular; should rather override LocalBranch.populateEnvironmentVariables
                LocalBranch lb = getExtensions().get(LocalBranch.class);
                if (lb != null) {
                   // Set GIT_LOCAL_BRANCH variable from the LocalBranch extension
                   String localBranchName = lb.getLocalBranch();
                   if (localBranchName == null || localBranchName.equals("**")) {
                      // local branch is configured with empty value or "**" so use remote branch name for checkout
                      localBranchName = deriveLocalBranchName(remoteBranchName);
                   }
                   env.put(GIT_LOCAL_BRANCH, localBranchName);
                }
                RelativeTargetDirectory rtd = getExtensions().get(RelativeTargetDirectory.class);
                if (rtd != null) {
                   String localRelativeTargetDir = rtd.getRelativeTargetDir();
                   if ( localRelativeTargetDir == null ){
                       localRelativeTargetDir = "";
                   }
                   env.put(GIT_CHECKOUT_DIR, localRelativeTargetDir);
                }

                String prevCommit = getLastBuiltCommitOfBranch(build, branch);
                if (prevCommit != null) {
                    env.put(GIT_PREVIOUS_COMMIT, prevCommit);
                }

                String prevSuccessfulCommit = getLastSuccessfulBuiltCommitOfBranch(build, branch);
                if (prevSuccessfulCommit != null) {
                    env.put(GIT_PREVIOUS_SUCCESSFUL_COMMIT, prevSuccessfulCommit);
                }
            }

            String sha1 = Util.fixEmpty(rev.getSha1String());
            if (sha1 != null && !sha1.isEmpty()) {
                env.put(GIT_COMMIT, sha1);
            }
        }

        /* Check all repository URLs are not empty */
        /* JENKINS-38608 reports an unhelpful error message when a repository URL is empty */
        /* Throws an IllegalArgumentException because that exception is thrown by env.put() on a null argument */
        int repoCount = 1;
        for (UserRemoteConfig config:userRemoteConfigs) {
            if (config.getUrl() == null) {
                throw new IllegalArgumentException("Git repository URL " + repoCount + " is an empty string in job definition. Checkout requires a valid repository URL");
            }
            repoCount++;
        }

        if (userRemoteConfigs.size()>0) {
            env.put(GIT_URL, userRemoteConfigs.get(0).getUrl());
        }
        if (userRemoteConfigs.size()>1) {
            int count=1;
            for (UserRemoteConfig config:userRemoteConfigs) {
                env.put(GIT_URL+"_"+count, config.getUrl());
                count++;
            }
        }

        getDescriptor().populateEnvironmentVariables(env);
        for (GitSCMExtension ext : extensions) {
            ext.populateEnvironmentVariables(this, env);
        }
    }

    private String getBranchName(Branch branch)
    {
        String name = branch.getName();
        if(name.startsWith("refs/remotes/")) {
            //Restore expected previous behaviour
            name = name.substring("refs/remotes/".length());
        }
        return name;
    }

    private String getLastBuiltCommitOfBranch(Run<?, ?> build, Branch branch) {
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

    private String getLastSuccessfulBuiltCommitOfBranch(Run<?, ?> build, Branch branch) {
        String prevCommit = null;
        if (build.getPreviousSuccessfulBuild() != null) {
            final Build lastSuccessfulBuildOfBranch = fixNull(getBuildData(build.getPreviousSuccessfulBuild())).getLastBuildOfBranch(branch.getName());
            if (lastSuccessfulBuildOfBranch != null) {
                Revision previousRev = lastSuccessfulBuildOfBranch.getRevision();
                if (previousRev != null) {
                    prevCommit = previousRev.getSha1String();
                }
            }
        }

        return prevCommit;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        try {
            GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).in(new File(".")).using(gitTool).getClient();
            return new GitChangeLogParser(gitClient, getExtensions().get(AuthorInChangelog.class) != null);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Git client using '" + gitTool + "' changelog parser failed, using deprecated changelog parser", e);
        }
        return new GitChangeLogParser(null, getExtensions().get(AuthorInChangelog.class) != null);
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<GitSCM> {

        private String gitExe;
        private String globalConfigName;
        private String globalConfigEmail;
        private boolean createAccountBasedOnEmail;
        private boolean useExistingAccountWithSameEmail;
//        private GitClientType defaultClientType = GitClientType.GITCLI;
        private boolean showEntireCommitSummaryInChanges;
        private boolean hideCredentials;
        private boolean allowSecondFetch;
        private boolean disableGitToolChooser;
        private boolean addGitTagAction;

        public DescriptorImpl() {
            super(GitSCM.class, GitRepositoryBrowser.class);
            load();
        }

        @NonNull
        // TODO: Add @Override when Jenkins core baseline is 2.222+
        public Permission getRequiredGlobalConfigPagePermission() {
            return getJenkinsManageOrAdmin();
        }

        // TODO: remove when Jenkins core baseline is 2.222+
        Permission getJenkinsManageOrAdmin() {
            Permission manage;
            try { // Manage is available starting from Jenkins 2.222 (https://jenkins.io/changelog/#v2.222). See JEP-223 for more info
                manage = (Permission) ReflectionUtils.getPublicProperty(Jenkins.get(), "MANAGE");
            } catch (IllegalArgumentException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                manage = Jenkins.ADMINISTER;
            }
            return manage;
        }

        public boolean isShowEntireCommitSummaryInChanges() {
            return showEntireCommitSummaryInChanges;
        }

        public boolean isHideCredentials() { return hideCredentials; }

        public void setHideCredentials(boolean hideCredentials) {
            this.hideCredentials = hideCredentials;
        }

        public void setShowEntireCommitSummaryInChanges(boolean showEntireCommitSummaryInChanges) {
            this.showEntireCommitSummaryInChanges = showEntireCommitSummaryInChanges;
        }

        public String getDisplayName() {
            return "Git";
        }

        @Override public boolean isApplicable(Job project) {
            return true;
        }

        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            return GitSCMExtensionDescriptor.all();
        }

        public boolean showGitToolOptions() {
            return Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).getInstallations().length>1;
        }

        /**
         * Lists available toolinstallations.
         * @return  list of available git tools
         */
        public List<GitTool> getGitTools() {
            GitTool[] gitToolInstallations = Jenkins.get().getDescriptorByType(GitTool.DescriptorImpl.class).getInstallations();
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
         * @return git executable
         */
        @Deprecated
        public String getGitExe() {
            return gitExe;
        }

        /**
         * Global setting to be used to set GIT_COMMITTER_NAME and GIT_AUTHOR_NAME.
         * @return user.name value
         */
        public String getGlobalConfigName() {
            return Util.fixEmptyAndTrim(globalConfigName);
        }

        /**
         * Global setting to be used to set GIT_COMMITTER_NAME and GIT_AUTHOR_NAME.
         * @param globalConfigName user.name value to be assigned
         */
        public void setGlobalConfigName(String globalConfigName) {
            this.globalConfigName = globalConfigName;
        }

        /**
         * Global setting to be used to set GIT_COMMITTER_EMAIL and GIT_AUTHOR_EMAIL.
         * @return user.email value
         */
        public String getGlobalConfigEmail() {
            return Util.fixEmptyAndTrim(globalConfigEmail);
        }

        /**
         * Global setting to be used to set GIT_COMMITTER_EMAIL and GIT_AUTHOR_EMAIL.
         * @param globalConfigEmail user.email value to be assigned
         */
        public void setGlobalConfigEmail(String globalConfigEmail) {
            this.globalConfigEmail = globalConfigEmail;
        }

        public boolean isCreateAccountBasedOnEmail() {
            return createAccountBasedOnEmail;
        }

        public void setCreateAccountBasedOnEmail(boolean createAccountBasedOnEmail) {
            this.createAccountBasedOnEmail = createAccountBasedOnEmail;
        }

        public boolean isUseExistingAccountWithSameEmail() {
            return useExistingAccountWithSameEmail;
        }

        public void setUseExistingAccountWithSameEmail(boolean useExistingAccountWithSameEmail) {
            this.useExistingAccountWithSameEmail = useExistingAccountWithSameEmail;
        }

        public boolean isAllowSecondFetch() { return allowSecondFetch; }

        public void setAllowSecondFetch(boolean allowSecondFetch) {
            this.allowSecondFetch = allowSecondFetch;
        }

        public boolean isDisableGitToolChooser() { return disableGitToolChooser; }

        public void setDisableGitToolChooser(boolean disableGitToolChooser) { this.disableGitToolChooser = disableGitToolChooser; }

        public boolean isAddGitTagAction() { return addGitTagAction; }

        public void setAddGitTagAction(boolean addGitTagAction) { this.addGitTagAction = addGitTagAction; }

        /**
         * Old configuration of git executable - exposed so that we can
         * migrate this setting to GitTool without deprecation warnings.
         * @return git executable
         */
        public String getOldGitExe() {
            return gitExe;
        }

        public static List<RemoteConfig> createRepositoryConfigurations(String[] urls,
                String[] repoNames,
                String[] refs) throws IOException {

            List<RemoteConfig> remoteRepositories;
            Config repoConfig = new Config();
            // Make up a repo config from the request parameters

            String[] names = repoNames;

            names = GitUtils.fixupNames(names, urls);

            for (int i = 0; i < names.length; i++) {
                String url = urls[i];
                if (url == null) {
                    continue;
                }
                String name = names[i];
                name = name.replace(' ', '_');

                if (isBlank(refs[i])) {
                    refs[i] = "+refs/heads/*:refs/remotes/" + name + "/*";
                }

                repoConfig.setString("remote", name, "url", url);
                repoConfig.setStringList("remote", name, "fetch", new ArrayList<>(Arrays.asList(refs[i].split("\\s+"))));
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
                mergeOptions.setFastForwardMode(mergeOptionsBean.getFastForwardMode());
            }

            return mergeOptions;
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
         * @param env base environment variables
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

    @Whitelisted
    @Deprecated
    public boolean isDoGenerateSubmoduleConfigurations() {
        return false;
    }

    @Exported
    @Whitelisted
    public List<BranchSpec> getBranches() {
        return branches;
    }

    @Override public String getKey() {
        ScmName scmName = getExtensions().get(ScmName.class);
        if (scmName != null) {
            return scmName.getName();
        }
        StringBuilder b = new StringBuilder("git");
        for (RemoteConfig cfg : getRepositories()) {
            for (URIish uri : cfg.getURIs()) {
                b.append(' ').append(uri.toString());
            }
        }
        return b.toString();
    }

    /**
     * @deprecated Use {@link PreBuildMerge}.
     * @return pre-build merge options
     * @throws FormException on form error
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
     * @param build run whose build data is returned
     * @param clone true if returned build data should be copied rather than referenced
     * @return build data for build run
     */
    public BuildData getBuildData(Run build, boolean clone) {
        return clone ? copyBuildData(build) : getBuildData(build);
    }

    /**
     * Like {@link #getBuildData(Run)}, but copy the data into a new object,
     * which is used as the first step for updating the data for the next build.
     * @param build run whose BuildData is returned
     * @return copy of build data for build
     */
    public BuildData copyBuildData(Run build) {
        BuildData base = getBuildData(build);
        ScmName sn = getExtensions().get(ScmName.class);
        String scmName = sn == null ? null : sn.getName();
        if (base==null)
            return new BuildData(scmName, getUserRemoteConfigs());
        else {
           BuildData buildData = base.clone();
           buildData.setScmName(scmName);
           return buildData;
        }
    }

    /**
     * Find the build log (BuildData) recorded with the last build that completed. BuildData
     * may not be recorded if an exception occurs in the plugin logic.
     *
     * @param build run whose build data is returned
     * @return the last recorded build data
     */
    public @CheckForNull BuildData getBuildData(Run build) {
        BuildData buildData = null;
        while (build != null) {
            List<BuildData> buildDataList = build.getActions(BuildData.class);
            // We need to get the latest recorded build data. It may happen
            // that the build has more than one checkout of the same repo.
            List<BuildData> buildDataListReverted = reversedView(buildDataList);
            for (BuildData bd : buildDataListReverted) {
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
     * Gets a reversed view of an unmodifiable list without using increasing space or time.
     * @param list The list to revert.
     * @param <T> The type of the elements of the list.
     * @return The list <i>reverted</i>.
     */
    private <T> List<T> reversedView(final List<T> list) {
        return new AbstractList<T>() {
            @Override
            public T get(int index) {
                return list.get(list.size() - 1 - index);
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    /**
     * Given the workspace, gets the working directory, which will be the workspace
     * if no relative target dir is specified. Otherwise, it'll be "workspace/relativeTargetDir".
     *
     * @param context job context for working directory
     * @param workspace initial FilePath of job workspace
     * @param environment environment variables used in job context
     * @param listener build log
     * @return working directory or null if workspace is null
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     */
    protected FilePath workingDirectory(Job<?,?> context, FilePath workspace, EnvVars environment, TaskListener listener) throws IOException, InterruptedException {
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
     * @param listener build log
     * @return true if any exclusion files are matched, false otherwise.
     */
    private boolean isRevExcluded(GitClient git, Revision r, TaskListener listener, BuildData buildData) throws IOException, InterruptedException {
        try {
            List<String> revShow;
            if (buildData != null && buildData.lastBuild != null) {
                if (getExtensions().get(PathRestriction.class) != null) {
                    revShow  = git.showRevision(buildData.lastBuild.revision.getSha1(), r.getSha1());
                } else {
                    revShow  = git.showRevision(buildData.lastBuild.revision.getSha1(), r.getSha1(), false);
                }
            } else {
                revShow  = git.showRevision(r.getSha1());
            }

            revShow.add("commit "); // sentinel value

            int start=0, idx=0;
            for (String line : revShow) {
                if (line.startsWith("commit ") && idx!=0) {
                    boolean showEntireCommitSummary = GitChangeSet.isShowEntireCommitSummaryInChanges() || !(git instanceof CliGitAPIImpl);
                    GitChangeSet change = new GitChangeSet(revShow.subList(start,idx), getExtensions().get(AuthorInChangelog.class)!=null, showEntireCommitSummary);

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

    /**
     * Data bound setter for doGenerateSubmoduleConfigurations that
     * intentionally ignores the value passed by the caller.
     * Submodule configuration generation was untested and unlikely to
     * work prior to git plugin 4.6.0.  It was removed from git plugin
     * 4.6.0 to improve the experience for Pipeline Syntax users.
     *
     * @param ignoredValue ignored because submodule configuration
     * generation is no longer supported
     */
    @DataBoundSetter
    public void setDoGenerateSubmoduleConfigurations(boolean ignoredValue) {
    }

    /**
     * Returns false, the constant value of doGenerateSubmoduleConfigurations.
     * @return false, the constant value of doGenerateSubmoduleConfigurations.
     */
    @Deprecated
    public boolean getDoGenerateSubmoduleConfigurations() {
        return doGenerateSubmoduleConfigurations;
    }

    @Initializer(after=PLUGINS_STARTED)
    public static void onLoaded() {
        Jenkins jenkins = Jenkins.get();
        DescriptorImpl desc = jenkins.getDescriptorByType(DescriptorImpl.class);

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
    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL", justification="Not final so users can adjust log verbosity")
    public static boolean VERBOSE = Boolean.getBoolean(GitSCM.class.getName() + ".verbose");

    /**
     * To avoid pointlessly large changelog, we'll limit the number of changes up to this.
     */
    public static final int MAX_CHANGELOG = Integer.getInteger(GitSCM.class.getName()+".maxChangelog",1024);
}
