package hudson.plugins.git.util;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitUtils implements Serializable {
    GitClient git;
    TaskListener listener;

    public GitUtils(TaskListener listener, GitClient git) {
        this.git = git;
        this.listener = listener;
    }

    /**
     * Return a list of "Revisions" - where a revision knows about all the branch names that refer to
     * a SHA1.
     * @return list of revisions
     * @throws IOException
     * @throws GitException
     */
    public Collection<Revision> getAllBranchRevisions() throws GitException, IOException, InterruptedException {
        Map<ObjectId, Revision> revisions = new HashMap<ObjectId, Revision>();
        for (Branch b : git.getRemoteBranches()) {
            Revision r = revisions.get(b.getSHA1());
            if (r == null) {
                r = new Revision(b.getSHA1());
                revisions.put(b.getSHA1(), r);
            }
            r.getBranches().add(b);
        }
        return revisions.values();
    }

    /**
     * Return the revision containing the branch name.
     * @param branchName
     * @return revision containing branchName
     * @throws IOException
     * @throws GitException
     */
    public Revision getRevisionContainingBranch(String branchName) throws GitException, IOException, InterruptedException {
        for(Revision revision : getAllBranchRevisions()) {
            for(Branch b : revision.getBranches()) {
                if(b.getName().equals(branchName)) {
                    return revision;
                }
            }
        }
        return null;
    }

    public Revision getRevisionForSHA1(ObjectId sha1) throws GitException, IOException, InterruptedException {
        for(Revision revision : getAllBranchRevisions()) {
            if(revision.getSha1().equals(sha1))
                return revision;
        }
        return new Revision(sha1);
    }

    public Revision sortBranchesForRevision(Revision revision, List<BranchSpec> branchOrder) {
        EnvVars env = new EnvVars();
        return sortBranchesForRevision(revision, branchOrder, env);
    }

    public Revision sortBranchesForRevision(Revision revision, List<BranchSpec> branchOrder, EnvVars env) {
        ArrayList<Branch> orderedBranches = new ArrayList<Branch>(revision.getBranches().size());
        ArrayList<Branch> revisionBranches = new ArrayList<Branch>(revision.getBranches());

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
     * @param revisions
     * @return filtered tip branches
     */
    @WithBridgeMethods(Collection.class)
    public List<Revision> filterTipBranches(final Collection<Revision> revisions) throws InterruptedException {
        // If we have 3 branches that we might want to build
        // ----A--.---.--- B
        //        \-----C

        // we only want (B) and (C), as (A) is an ancestor (old).
        final List<Revision> l = new ArrayList<Revision>(revisions);

        // Bypass any rev walks if only one branch or less
        if (l.size() <= 1)
            return l;

        try {
            return git.withRepository(new RepositoryCallback<List<Revision>>() {
                public List<Revision> invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {

                    // Commit nodes that we have already reached
                    Set<RevCommit> visited = new HashSet<RevCommit>();
                    // Commits nodes that are tips if we don't reach them walking back from
                    // another node
                    Map<RevCommit, Revision> tipCandidates = new HashMap<RevCommit, Revision>();

                    long calls = 0;
                    final long start = System.currentTimeMillis();

                    RevWalk walk = new RevWalk(repo);

                    final boolean log = LOGGER.isLoggable(Level.FINE);

                    if (log)
                        LOGGER.fine(MessageFormat.format(
                                "Computing merge base of {0}  branches", l.size()));

                    try {
                        walk.setRetainBody(false);

                        // Each commit passed in starts as a potential tip.
                        // We walk backwards in the commit's history, until we reach the
                        // beginning or a commit that we have already visited. In that case,
                        // we mark that one as not a potential tip.
                        for (Revision r : revisions) {
                            walk.reset();
                            RevCommit head = walk.parseCommit(r.getSha1());

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

                    } finally {
                        walk.release();
                    }

                    if (log)
                        LOGGER.fine(MessageFormat.format(
                                "Computed merge bases in {0} commit steps and {1} ms", calls,
                                (System.currentTimeMillis() - start)));

                    return new ArrayList<Revision>(tipCandidates.values());
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
     */
    public static EnvVars getPollEnvironment(AbstractProject p, FilePath ws, Launcher launcher, TaskListener listener, boolean reuseLastBuildEnv)
        throws IOException,InterruptedException {
        EnvVars env;
        StreamBuildListener buildListener = new StreamBuildListener((OutputStream)listener.getLogger());
        AbstractBuild b = (AbstractBuild)p.getLastBuild();

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
                env = lastBuiltOn.toComputer().getEnvironment().overrideAll(b.getCharacteristicEnvVars());
                for (NodeProperty nodeProperty: lastBuiltOn.getNodeProperties()) {
                    Environment environment = nodeProperty.setUp(b, launcher, (BuildListener)buildListener);
                    if (environment != null) {
                        environment.buildEnvVars(env);
                    }
                }
            } else {
                env = new EnvVars(System.getenv());
            }

            p.getScm().buildEnvVars(b,env);
        } else {
            env = new EnvVars(System.getenv());
        }

        String rootUrl = Hudson.getInstance().getRootUrl();
        if(rootUrl!=null) {
            env.put("HUDSON_URL", rootUrl); // Legacy.
            env.put("JENKINS_URL", rootUrl);
            env.put("BUILD_URL", rootUrl+b.getUrl());
            env.put("JOB_URL", rootUrl+p.getUrl());
        }

        if(!env.containsKey("HUDSON_HOME")) // Legacy
            env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath() );

        if(!env.containsKey("JENKINS_HOME"))
            env.put("JENKINS_HOME", Hudson.getInstance().getRootDir().getPath() );

        if (ws != null)
            env.put("WORKSPACE", ws.getRemote());

        for (NodeProperty nodeProperty: Hudson.getInstance().getGlobalNodeProperties()) {
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
        List<? extends Action> buildActions = b.getActions();
        if (buildActions != null) {
            for (Action action : buildActions) {
                // most importantly, ParametersAction will be processed here (for parameterized builds)
                if (action instanceof EnvironmentContributingAction) {
                    EnvironmentContributingAction envAction = (EnvironmentContributingAction) action;
                    envAction.buildEnvVars(b, env);
                }
            }
        }
    }

    public static String[] fixupNames(String[] names, String[] urls) {
        String[] returnNames = new String[urls.length];
        Set<String> usedNames = new HashSet<String>();

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

    private static final Logger LOGGER = Logger.getLogger(GitUtils.class.getName());

    private static final long serialVersionUID = 1L;
}
