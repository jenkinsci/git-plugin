package hudson.plugins.git.util;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import jenkins.model.Jenkins;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;

public class GitUtils implements Serializable {
    
    @SuppressFBWarnings(value="SE_BAD_FIELD", justification="known non-serializable field")
    @Nonnull
    GitClient git;
    @Nonnull
    TaskListener listener;

    public GitUtils(@Nonnull TaskListener listener, @Nonnull GitClient git) {
        this.git = git;
        this.listener = listener;
    }

    public static Node workspaceToNode(FilePath workspace) { // TODO https://trello.com/c/doFFMdUm/46-filepath-getcomputer
        Jenkins j = Jenkins.getActiveInstance();
        if (workspace != null && workspace.isRemote()) {
            for (Computer c : j.getComputers()) {
                if (c.getChannel() == workspace.getChannel()) {
                    Node n = c.getNode();
                    if (n != null) {
                        return n;
                    }
                }
            }
        }
        return j;
    }

    /**
     * Return a list of "Revisions" - where a revision knows about all the branch names that refer to
     * a SHA1.
     * @return list of revisions
     * @throws IOException on input or output error
     * @throws GitException on git error
     * @throws InterruptedException when interrupted
     */
    public Collection<Revision> getAllBranchRevisions() throws GitException, IOException, InterruptedException {
        return getMatchingRevisions(null, null);
    }

    private boolean branchNameMatches(Branch branch, @NonNull List<BranchSpec> branchSpecs, EnvVars env) {
        boolean matched = false;
        for (BranchSpec branchSpec : branchSpecs) {
            if (branchSpec.matches(branch.getName(), env)) {
                matched = true;
                break;
            }
        }
        return matched;
    }

    private boolean tagNameMatches(String tagName, @NonNull List<BranchSpec> branchSpecs, EnvVars env) {
        boolean matched = false;
        for (BranchSpec branchSpec : branchSpecs) {
            if (branchSpec.matches(tagName, env)) {
                matched = true;
                break;
            }
        }
        return matched;
    }

    /**
     * Return a list of "Revisions" - where a revision knows about all the
     * branch names and tags that refer to a SHA1.
     *
     * @param branchSpecs list of branch specifications used for branch and tag
     * name matching
     * @param env environment used to evaluate branchNameFilter
     * @return list of revisions
     * @throws IOException on input or output error
     * @throws GitException on git error
     * @throws InterruptedException when interrupted
     */
    public Collection<Revision> getMatchingRevisions(List<BranchSpec> branchSpecs, EnvVars env) throws GitException, IOException, InterruptedException {
        Map<ObjectId, Revision> revisions = new HashMap<>();
        for (Branch b : git.getRemoteBranches()) {
            if (branchSpecs != null) {
                if (!branchNameMatches(b, branchSpecs, env)) {
                    continue;
                }
            }
            Revision r = revisions.get(b.getSHA1());
            if (r == null) {
                r = new Revision(b.getSHA1());
                revisions.put(b.getSHA1(), r);
            }
            r.getBranches().add(b);
        }
        for (String tag : git.getTagNames(null)) {
            String tagRef = Constants.R_TAGS + tag;
            if (branchSpecs != null) {
                if (!tagNameMatches(tagRef, branchSpecs, env)) {
                    continue;
                }
            }
            ObjectId objectId = git.revParse(tagRef);
            Revision r = revisions.get(objectId);
            if (r == null) {
                r = new Revision(objectId);
                revisions.put(objectId, r);
            }
            r.getBranches().add(new Branch(tagRef, objectId));
        }
        return revisions.values();
    }

    /**
     * Return the revision containing the branch name.
     * @param branchName name of branch to be searched
     * @return revision containing branchName
     * @throws IOException on input or output error
     * @throws GitException on git error
     * @throws InterruptedException when interrupted
     */
    public Revision getRevisionContainingBranch(String branchName) throws GitException, IOException, InterruptedException {
        List<BranchSpec> branchSpecList = new ArrayList<>();
        branchSpecList.add(new BranchSpec(branchName));
        for (Revision revision : getMatchingRevisions(branchSpecList, new EnvVars())) {
            for(Branch b : revision.getBranches()) {
                if(b.getName().equals(branchName)) {
                    return revision;
                }
            }
        }
        return null;
    }

    public Revision getRevisionForSHA1(ObjectId sha1) throws GitException, IOException, InterruptedException {
        if (git instanceof CliGitAPIImpl) {
            if (gitVersionAtLeast(2, 7)) { // Use command line git optimized implementation
                return cliGitGetRevisionForSHA1(sha1);
            }
        }
        for(Revision revision : getAllBranchRevisions()) {
            if(revision.getSha1().equals(sha1))
                return revision;
        }
        return new Revision(sha1);
    }

    private Revision cliGitGetRevisionForSHA1(ObjectId sha1) throws GitException, IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder("git", "for-each-ref", "--points-at", sha1.getName());
        Launcher launcher = new Launcher.LocalLauncher(listener);
        EnvVars env = new EnvVars();
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ByteArrayOutputStream bytesErr = new ByteArrayOutputStream();
        Launcher.ProcStarter p = launcher.launch().cmds(args).envs(env).stdout(bytesOut).stderr(bytesErr).pwd(git.getRepository().getWorkTree());
        int status = p.start().joinWithTimeout(1, TimeUnit.MINUTES, listener);
        if (status != 0) {
            final boolean log = LOGGER.isLoggable(Level.WARNING);

            if (log) {
                String message = MessageFormat.format(
                        "git for-each-ref --points-at {0} returned: {1}",
                        sha1.getName(), status);
                LOGGER.log(Level.WARNING, message);
            }
        }
        String result = bytesOut.toString("UTF-8");
        if (bytesErr.size() > 0) {
            final boolean log = LOGGER.isLoggable(Level.WARNING);

            if (log) {
                String message = MessageFormat.format(
                        "git for-each-ref --points-at {0} stderr not empty: {1}",
                        sha1.getName(), result);
                LOGGER.log(Level.WARNING, message);
            }
        }
        /*
        Output shows SHA1, type, and ref
        c4029dcfad8d29185d8c114f724f03aaf03687cd commit refs/heads/master
        c4029dcfad8d29185d8c114f724f03aaf03687cd commit refs/remotes/bitbucket/master
        c4029dcfad8d29185d8c114f724f03aaf03687cd commit refs/remotes/origin/HEAD
        c4029dcfad8d29185d8c114f724f03aaf03687cd commit refs/remotes/origin/master
        c4029dcfad8d29185d8c114f724f03aaf03687cd commit refs/remotes/upstream/master
        c4029dcfad8d29185d8c114f724f03aaf03687cd commit refs/tags/xyzzy
         */
        String[] output = result.split("[\\n\\r]");
        Pattern pattern = Pattern.compile(sha1.getName() + "\\s+\\w+\\s+refs/\\w+/(.*)");
        List<Branch> branches = new ArrayList<>();
        if (output.length == 0) {
            return new Revision(sha1);
        }
        if (output.length == 1 && output[0].isEmpty()) {
            return new Revision(sha1);
        }
        for (String line : output) {
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                // Log the surprise and skip the line
                final boolean log = LOGGER.isLoggable(Level.WARNING);

                if (log) {
                    String message = MessageFormat.format(
                            "git for-each-ref --points-at {0} output not matched in line: {1}",
                            sha1.getName(), line);
                    LOGGER.log(Level.WARNING, message);
                }
                continue;
            }
            String branchName = matcher.group(1);
            Branch branch = new Branch(branchName, sha1);
            branches.add(branch);
        }
        return new Revision(sha1, branches);
    }

    public Revision sortBranchesForRevision(Revision revision, List<BranchSpec> branchOrder) {
        EnvVars env = new EnvVars();
        return sortBranchesForRevision(revision, branchOrder, env);
    }

    public Revision sortBranchesForRevision(Revision revision, List<BranchSpec> branchOrder, EnvVars env) {
        ArrayList<Branch> orderedBranches = new ArrayList<>(revision.getBranches().size());
        ArrayList<Branch> revisionBranches = new ArrayList<>(revision.getBranches());

        for(BranchSpec branchSpec : branchOrder) {
            for (Iterator<Branch> i = revisionBranches.iterator(); i.hasNext();) {
                Branch b = i.next();
                if (branchSpec.matches(b.getName(), env)) {
                    i.remove();
                    orderedBranches.add(b);
                }
            }
        }

        orderedBranches.addAll(revisionBranches);
        return new Revision(revision.getSha1(), orderedBranches);
    }

    /**
     * Return a list of 'tip' branches (I.E. branches that aren't included entirely within another branch).
     *
     * @param revisions branches to be included in the search for tip branches
     * @return filtered tip branches
     * @throws InterruptedException when interrupted
     */
    @WithBridgeMethods(Collection.class)
    public List<Revision> filterTipBranches(final Collection<Revision> revisions) throws InterruptedException {
        // If we have 3 branches that we might want to build
        // ----A--.---.--- B
        //        \-----C

        // we only want (B) and (C), as (A) is an ancestor (old).
        final List<Revision> l = new ArrayList<>(revisions);

        // Bypass any rev walks if only one branch or less
        if (l.size() <= 1)
            return l;

        try {
            return git.withRepository(new RepositoryCallback<List<Revision>>() {
                public List<Revision> invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {

                    // Commit nodes that we have already reached
                    Set<RevCommit> visited = new HashSet<>();
                    // Commits nodes that are tips if we don't reach them walking back from
                    // another node
                    Map<RevCommit, Revision> tipCandidates = new HashMap<>();

                    long calls = 0;
                    final long start = System.currentTimeMillis();

                    final boolean log = LOGGER.isLoggable(Level.FINE);

                    if (log)
                        LOGGER.fine(MessageFormat.format(
                                "Computing merge base of {0} branches", l.size()));

                    try (RevWalk walk = new RevWalk(repo)) {
                        walk.setRetainBody(false);

                        // Each commit passed in starts as a potential tip.
                        // We walk backwards in the commit's history, until we reach the
                        // beginning or a commit that we have already visited. In that case,
                        // we mark that one as not a potential tip.
                        for (Revision r : revisions) {
                            walk.reset();
                            RevCommit head = walk.parseCommit(r.getSha1());

                            if (visited.contains(head)) {
                              continue;
                            }

                            tipCandidates.put(head, r);

                            walk.markStart(head);
                            for (RevCommit commit : walk) {
                                calls++;
                                if (visited.contains(commit)) {
                                    tipCandidates.remove(commit);
                                    break;
                                }
                                visited.add(commit);
                            }
                        }
                    }

                    if (log)
                        LOGGER.fine(MessageFormat.format(
                                "Computed merge bases in {0} commit steps and {1} ms", calls,
                                (System.currentTimeMillis() - start)));

                    return new ArrayList<>(tipCandidates.values());
                }
            });
        } catch (IOException e) {
            throw new GitException("Error computing merge base", e);
        }
    }

    public static EnvVars getPollEnvironment(AbstractProject p, FilePath ws, Launcher launcher, TaskListener listener)
        throws IOException, InterruptedException {
        return getPollEnvironment(p, ws, launcher, listener, true);
    }


    /**
     * An attempt to generate at least semi-useful EnvVars for polling calls, based on previous build.
     * Cribbed from various places.
     * @param p abstract project to be considered
     * @param ws workspace to be considered
     * @param launcher launcher to use for calls to nodes
     * @param listener build log
     * @param reuseLastBuildEnv true if last build environment should be considered
     * @return environment variables from previous build to be used for polling
     * @throws IOException on input or output error
     * @throws InterruptedException when interrupted
     */
    public static EnvVars getPollEnvironment(AbstractProject p, FilePath ws, Launcher launcher, TaskListener listener, boolean reuseLastBuildEnv)
        throws IOException,InterruptedException {
        EnvVars env = null;
        StreamBuildListener buildListener = new StreamBuildListener((OutputStream)listener.getLogger());
        AbstractBuild b = p.getLastBuild();

        if (b == null) {
            // If there is no last build, we need to trigger a new build anyway, and
            // GitSCM.compareRemoteRevisionWithImpl() will short-circuit and never call this code
            // ("No previous build, so forcing an initial build.").
            throw new IllegalArgumentException("Last build must not be null. If there really is no last build, " +
                    "a new build should be triggered without polling the SCM.");
        }

        if (reuseLastBuildEnv) {
            Node lastBuiltOn = b.getBuiltOn();

            if (lastBuiltOn != null) {
                Computer lastComputer = lastBuiltOn.toComputer();
                if (lastComputer != null) {
                    env = lastComputer.getEnvironment().overrideAll(b.getCharacteristicEnvVars());
                    for (NodeProperty nodeProperty : lastBuiltOn.getNodeProperties()) {
                        Environment environment = nodeProperty.setUp(b, launcher, (BuildListener) buildListener);
                        if (environment != null) {
                            environment.buildEnvVars(env);
                        }
                    }
                }
            }
            if (env == null) {
                env = p.getEnvironment(workspaceToNode(ws), listener);
            }

            p.getScm().buildEnvVars(b,env);
        } else {
            env = p.getEnvironment(workspaceToNode(ws), listener);
        }

        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            throw new IllegalArgumentException("Jenkins instance is null");
        }
        String rootUrl = jenkinsInstance.getRootUrl();
        if(rootUrl!=null) {
            env.put("HUDSON_URL", rootUrl); // Legacy.
            env.put("JENKINS_URL", rootUrl);
            env.put("BUILD_URL", rootUrl+b.getUrl());
            env.put("JOB_URL", rootUrl+p.getUrl());
        }

        if(!env.containsKey("HUDSON_HOME")) // Legacy
            env.put("HUDSON_HOME", jenkinsInstance.getRootDir().getPath() );

        if(!env.containsKey("JENKINS_HOME"))
            env.put("JENKINS_HOME", jenkinsInstance.getRootDir().getPath() );

        if (ws != null)
            env.put("WORKSPACE", ws.getRemote());

        for (NodeProperty nodeProperty: jenkinsInstance.getGlobalNodeProperties()) {
            Environment environment = nodeProperty.setUp(b, launcher, (BuildListener)buildListener);
            if (environment != null) {
                environment.buildEnvVars(env);
            }
        }

        // add env contributing actions' values from last build to environment - fixes JENKINS-22009
        addEnvironmentContributingActionsValues(env, b);

        EnvVars.resolve(env);

        return env;
    }

    private static void addEnvironmentContributingActionsValues(EnvVars env, AbstractBuild b) {
        List<? extends Action> buildActions = b.getAllActions();
        if (buildActions != null) {
            for (Action action : buildActions) {
                // most importantly, ParametersAction will be processed here (for parameterized builds)
                if (action instanceof ParametersAction) {
                    ParametersAction envAction = (ParametersAction) action;
                    envAction.buildEnvVars(b, env);
                }
            }
        }
        
        // Use the default parameter values (if any) instead of the ones from the last build
        ParametersDefinitionProperty paramDefProp = (ParametersDefinitionProperty) b.getProject().getProperty(ParametersDefinitionProperty.class);
        if (paramDefProp != null) {
            for(ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {
               ParameterValue defaultValue  = paramDefinition.getDefaultParameterValue();
               if (defaultValue != null) {
                   defaultValue.buildEnvironment(b, env);
               }
            }
        }
    }

    public static String[] fixupNames(String[] names, String[] urls) {
        String[] returnNames = new String[urls.length];
        Set<String> usedNames = new HashSet<>();

        for(int i=0; i<urls.length; i++) {
            String name = names[i];

            if(name == null || name.trim().length() == 0) {
                name = "origin";
            }

            String baseName = name;
            int j=1;
            while(usedNames.contains(name)) {
                name = baseName + (j++);
            }

            usedNames.add(name);
            returnNames[i] = name;
        }


        return returnNames;
    }

    /* Ugh - copied from CliGitAPIImpl and GitSampleRepoRule because API not exposed.
       Package protected for testing. */
    boolean gitVersionAtLeast(int neededMajor, int neededMinor) {
        return gitVersionAtLeast(neededMajor, neededMinor, 0);
    }

    /* Ugh - copied from CliGitAPIImpl and GitSampleRepoRule because API not exposed.
       Package protected for testing. */
    boolean gitVersionAtLeast(int neededMajor, int neededMinor, int neededPatch) {
        final boolean log = LOGGER.isLoggable(Level.FINE);
        final TaskListener procListener = StreamTaskListener.fromStderr();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int returnCode = -1;
        try {
            returnCode = new Launcher.LocalLauncher(procListener).launch().cmds("git", "--version").stdout(out).join();
        } catch (IOException | InterruptedException ex) {
            if (log) {
                LOGGER.log(Level.FINE, "Error checking git version", ex);
            }
        }
        if (returnCode != 0) {
            if (log) {
                LOGGER.log(Level.FINE, "Error checking git version", returnCode);
            }
        }
        String versionOutput = "git version 1.0.0";
        try {
            versionOutput = out.toString("UTF-8").trim();
        } catch (UnsupportedEncodingException unsupported) {
            LOGGER.log(Level.SEVERE, "UTF-8 an unsupported encoding reading 'git --version' output", unsupported);
        }
        final String[] fields = versionOutput.split(" ")[2].replaceAll("msysgit.", "").replaceAll("windows.", "").split("\\.");
        final int gitMajor = Integer.parseInt(fields[0]);
        final int gitMinor = Integer.parseInt(fields[1]);
        final int gitPatch = Integer.parseInt(fields[2]);
        if (gitMajor < 1 || gitMajor > 3) {
            if (log) {
                LOGGER.fine(MessageFormat.format(
                        "WARNING: Unexpected git major version {0} parsed from {1}, field[{2}] value: {3}",
                        gitMajor, versionOutput, 0, fields[0]));
            }
        }
        if (gitMinor < 0 || gitMinor > 20) {
            if (log) {
                LOGGER.fine(MessageFormat.format(
                        "WARNING: Unexpected git minor version {0} parsed from {1}, field[{2}] value: {3}",
                        gitMinor, versionOutput, 1, fields[1]));
            }
        }
        if (gitPatch < 0 || gitPatch > 20) {
            if (log) {
                LOGGER.fine(MessageFormat.format(
                        "WARNING: Unexpected git patch version {0} parsed from {1}, field[{2}] value: {3}",
                        gitPatch, versionOutput, 2, fields[2]));
            }
        }

        return gitMajor > neededMajor
                || (gitMajor == neededMajor && gitMinor > neededMinor)
                || (gitMajor == neededMajor && gitMinor == neededMinor && gitPatch >= neededPatch);
    }

    private static final Logger LOGGER = Logger.getLogger(GitUtils.class.getName());

    private static final long serialVersionUID = 1L;
}
