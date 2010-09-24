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
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.IndexEntry;
import hudson.plugins.git.Revision;


import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.spearce.jgit.lib.ObjectId;

public class GitUtils {
    IGitAPI git;
    TaskListener listener;

    public GitUtils(TaskListener listener, IGitAPI git) {
        this.git = git;
        this.listener = listener;
    }

    public List<IndexEntry> getSubmodules(String treeIsh) {
        List<IndexEntry> submodules = git.lsTree(treeIsh);

        // Remove anything that isn't a submodule
        for (Iterator<IndexEntry> it = submodules.iterator(); it.hasNext();) {
            if (!it.next().getMode().equals("160000")) {
                it.remove();
            }
        }
        return submodules;
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

        for (Iterator<Revision> it = revisions.iterator(); it.hasNext();) {
            Revision r = it.next();
            boolean remove = false;

            for (Revision r2 : revisions) {
                if (r != r2) {
                    ObjectId commonAncestor = git.mergeBase(r.getSha1(), r2.getSha1());
                    if (commonAncestor != null && commonAncestor.equals(r.getSha1())) {
                        remove = true;
                        break;
                    }
                }
            }

            if (remove) it.remove();

        }

        return revisions;
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
                env.put("HUDSON_URL", rootUrl);
                env.put("BUILD_URL", rootUrl+b.getUrl());
                env.put("JOB_URL", rootUrl+p.getUrl());
            }
            
            if(!env.containsKey("HUDSON_HOME"))
                env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath() );
            
            if (ws != null)
                env.put("WORKSPACE", ws.getRemote());
            
            
            p.getScm().buildEnvVars(b,env);
            
            for (NodeProperty nodeProperty: Hudson.getInstance().getGlobalNodeProperties()) {
                Environment environment = nodeProperty.setUp(b, launcher, (BuildListener)listener);
                if (environment != null) {
                    environment.buildEnvVars(env);
                }
            }

            if (lastBuiltOn != null) {
                for (NodeProperty nodeProperty: lastBuiltOn.getNodeProperties()) {
                    Environment environment = nodeProperty.setUp(b, launcher, (BuildListener)listener);
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
}