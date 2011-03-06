package hudson.plugins.git.util;

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
import hudson.plugins.git.IndexEntry;
import hudson.plugins.git.Revision;


import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.spearce.jgit.lib.ObjectId;

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
     * @param git
     * @return
     */
    public Collection<Revision> filterTipBranches(Collection<Revision> revisions) {
        // If we have 3 branches that we might want to build
        // ----A--.---.--- B
        //        \-----C

        // we only want (B) and (C), as (A) is an ancestor (old).

        List<Revision> l = new ArrayList<Revision>(revisions);

        OUTER:
        for (int i=0; i<l.size(); i++) {
            for (int j=i+1; j<l.size(); j++) {
                Revision ri = l.get(i);
                Revision rj = l.get(j);
                ObjectId commonAncestor = git.mergeBase(ri.getSha1(), rj.getSha1());
                if (commonAncestor==null)   continue;

                if (commonAncestor.equals(ri.getSha1())) {
                    LOGGER.fine("filterTipBranches: "+rj+" subsumes "+ri);
                    l.remove(i);
                    i--;
                    continue OUTER;
                }
                if (commonAncestor.equals(rj.getSha1())) {
                    LOGGER.fine("filterTipBranches: "+ri+" subsumes "+rj);
                    l.remove(j);
                    j--;
                }
            }
        }

        return l;
    }

    /**
     * An attempt to generate at least semi-useful EnvVars for polling calls, based on previous build.
     * Cribbed from various places.
     */
    public static EnvVars getPollEnvironment(AbstractProject p, FilePath ws, Launcher launcher, TaskListener listener)
        throws IOException,InterruptedException {
        EnvVars env;

        AbstractBuild b = (AbstractBuild)p.getLastBuild();

        if (b != null) {
            Node lastBuiltOn = b.getBuiltOn();

            if (lastBuiltOn != null) {
                env = lastBuiltOn.toComputer().getEnvironment().overrideAll(b.getCharacteristicEnvVars());
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
            
            
            p.getScm().buildEnvVars(b,env);

            StreamBuildListener buildListener = new StreamBuildListener((OutputStream)listener.getLogger());
            
            for (NodeProperty nodeProperty: Hudson.getInstance().getGlobalNodeProperties()) {
                Environment environment = nodeProperty.setUp(b, launcher, (BuildListener)buildListener);
                if (environment != null) {
                    environment.buildEnvVars(env);
                }
            }

            if (lastBuiltOn != null) {
                for (NodeProperty nodeProperty: lastBuiltOn.getNodeProperties()) {
                    Environment environment = nodeProperty.setUp(b, launcher, (BuildListener)buildListener);
                    if (environment != null) {
                        environment.buildEnvVars(env);
                    }
                }
            }

            EnvVars.resolve(env);
        } else {
            env = new EnvVars(System.getenv());
        }

        return env;
    }

    public static String[] fixupNames(String[] names) {
        String[] returnNames = new String[names.length];
        Set<String> usedNames = new HashSet<String>();

        for(int i=0; i<names.length; i++) {
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
