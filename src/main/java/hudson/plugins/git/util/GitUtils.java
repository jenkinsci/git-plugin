package hudson.plugins.git.util;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeProperty;
import hudson.model.StreamBuildListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;
import hudson.plugins.git.GitSCM;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.errors.MissingObjectException;

public class GitUtils {
    IGitAPI git;
    TaskListener listener;

    public GitUtils(TaskListener listener, IGitAPI git) {
        this.git = git;
        this.listener = listener;
    }

    /**
     * Return a list of "Revisions" - where a revision knows about all the branch names that refer to
     * a SHA1.
     * @return
     * @throws IOException
     * @throws GitException
     */
    public Collection<Revision> getAllBranchRevisions() throws GitException, IOException {
        Map<ObjectId, Revision> revisions = new HashMap<ObjectId, Revision>();
        List<Branch> branches = git.getRemoteBranches();
        for (Branch b : branches) {
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
     * @return
     * @throws IOException
     * @throws GitException
     */
    public Revision getRevisionContainingBranch(String branchName) throws GitException, IOException {
        for(Revision revision : getAllBranchRevisions()) {
            for(Branch b : revision.getBranches()) {
                if(b.getName().equals(branchName)) {
                    return revision;
                }
            }
        }
        return null;
    }

    public Revision getRevisionForSHA1(ObjectId sha1) throws GitException, IOException {
        for(Revision revision : getAllBranchRevisions()) {
            if(revision.getSha1().equals(sha1))
                return revision;
        }
        return null;
    }

    /**
     * Return a list of 'tip' branches (I.E. branches that aren't included entirely within another branch).
     *
     * @param revisions
     * @return filtered tip branches
     */
    @WithBridgeMethods(Collection.class)
    public List<Revision> filterTipBranches(Collection<Revision> revisions) {
        // If we have 3 branches that we might want to build
        // ----A--.---.--- B
        //        \-----C

        // we only want (B) and (C), as (A) is an ancestor (old).
        final List<Revision> l = new ArrayList<Revision>(revisions);
        
        // Bypass any rev walks if only one branch or less
        if (l.size() <= 1)
            return l;

        final boolean log = LOGGER.isLoggable(Level.FINE);
        Revision revI;
        Revision revJ;
        ObjectId shaI;
        ObjectId shaJ;
        ObjectId commonAncestor;
        RevWalk walk = null;
        final long start = System.currentTimeMillis();
        long calls = 0;
        if (log)
            LOGGER.fine(MessageFormat.format(
                    "Computing merge base of {0}  branches", l.size()));
        try {
            walk = new RevWalk(git.getRepository());
            walk.setRetainBody(false);
            walk.setRevFilter(RevFilter.MERGE_BASE);
            for (int i = 0; i < l.size(); i++)
                for (int j = i + 1; j < l.size(); j++) {
                    revI = l.get(i);
                    revJ = l.get(j);
                    shaI = revI.getSha1();
                    shaJ = revJ.getSha1();

                    walk.reset();
                    walk.markStart(walk.parseCommit(shaI));
                    walk.markStart(walk.parseCommit(shaJ));
                    commonAncestor = walk.next();
                    calls++;

                    if (commonAncestor == null)
                        continue;
                    if (commonAncestor.equals(shaI)) {
                        if (log)
                            LOGGER.fine("filterTipBranches: " + revJ
                                    + " subsumes " + revI);
                        l.remove(i);
                        i--;
                        break;
                    }
                    if (commonAncestor.equals(shaJ)) {
                        if (log)
                            LOGGER.fine("filterTipBranches: " + revI
                                    + " subsumes " + revJ);
                        l.remove(j);
                        j--;
                    }
                }
        } catch (IOException e) {
            throw new GitException("Error computing merge base", e);
        } finally {
            if (walk != null)
                walk.release();
        }
        if (log)
            LOGGER.fine(MessageFormat.format(
                    "Computed {0} merge bases in {1} ms", calls,
                    (System.currentTimeMillis() - start)));

        return l;
    }

    /**
     * Return a list of commits that have non excluded changes since the last build.
     *
     * @param revisions
     * @param gitSCM
     * @param data
     * @return filtered revisions
     */
    @WithBridgeMethods(Collection.class)
    public List<Revision> filterExcludedRevs(Collection<Revision> revisions,
                                             GitSCM gitSCM, BuildData data) {

        final List<Revision> l = new ArrayList<Revision>(revisions);

        // Nothing to exclude, skip the rev walk.
        if (!gitSCM.hasExclusionRule() || l.isEmpty())
            return l;

        Revision revBranch;
        ObjectId shaBranch;
        RevWalk walk = null;
        RevCommit c;
        final long start = System.currentTimeMillis();
        long calls = 0;

        LOGGER.fine(MessageFormat.format(
                    "Computing new revs of {0} branches", l.size()));
        try {
            walk = new RevWalk(git.getRepository());
            walk.setRetainBody(false);
            for (int i = 0; i < l.size(); i++) {
                revBranch = l.get(i);
                shaBranch = revBranch.getSha1();

                LOGGER.finest(MessageFormat.format(
                            "Starting rev-walk from {0}", shaBranch));

                walk.reset();
                walk.markStart(walk.parseCommit(shaBranch));

                // Skip all previously built commits.
                for (Build b : data.getBuildsByBranchName().values()) {
                    try {
                        walk.markUninteresting(walk.parseCommit(b.revision.getSha1()));
                    }
                    catch (MissingObjectException ex) {
                        LOGGER.fine(MessageFormat.format(
                                    "Commit object for build of {0} not found",
                                    b.revision.getSha1()));
                    }
                }

                boolean hasChanges = false;

                while ((c = walk.next()) != null) {
                    calls++;
                    LOGGER.finest(MessageFormat.format(
                                "At revision {0}", c.getId()));

                    Revision r = new Revision(c.getId());
                    if (!gitSCM.isRevExcluded(git, r, listener)) {
                        hasChanges = true;
                        break;
                    }

                    LOGGER.finest(MessageFormat.format(
                                "Revision {0} excluded", c.getId()));
                }

                if (!hasChanges) {
                    LOGGER.fine("filterExcludedRevs: " + revBranch
                                + " does not have any changes");
                    l.remove(i);
                    i--;
                } else {
                    LOGGER.fine("filterExcludedRevs: " + revBranch
                                + " has changes");
                }
            }
        } catch (IOException e) {
            throw new GitException("Error walking revs", e);
        } finally {
            if (walk != null)
                walk.release();
        }

        LOGGER.fine(MessageFormat.format(
                    "Computed {0} revs in {1} ms", calls,
                    (System.currentTimeMillis() - start)));

        return l;
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

        if (b != null) {
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

            if (lastBuiltOn != null) {

            }

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

        EnvVars.resolve(env);

        return env;
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
}
