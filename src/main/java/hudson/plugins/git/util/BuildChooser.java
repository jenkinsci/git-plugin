package hudson.plugins.git.util;

import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;
import hudson.util.XStream2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.spearce.jgit.lib.ObjectId;

public class BuildChooser implements IBuildChooser
{
    IGitAPI               git;
    GitUtils              utils;
    GitSCM                gitSCM;

    File                  storageFile;

    //-------- Data -----------
    Map<String, ObjectId> lastBuiltIds = new HashMap<String, ObjectId>();
    Revision              lastBuiltRevision;

    //-------- Data -----------

    public BuildChooser(GitSCM gitSCM, IGitAPI git, GitUtils utils, File storageFile)
    {
        this.gitSCM = gitSCM;
        this.git = git;
        this.utils = utils;
        this.storageFile = storageFile;
    }
    /**
     * In order to determine which Revisions to build.
     * 
     * Does the following :
     *  1. Find all the branch revisions
     *  2. Filter out branches that we don't care about from the revisions.
     *     Any Revisions with no interesting branches are dropped.
     *  3. Get rid of any revisions that are wholly subsumed by another
     *     revision we're considering.
     *  4. Get rid of any revisions that we've already built.
     *  
     *  NB: Alternate IBuildChooser implementations are possible - this
     *  may be beneficial if "only 1" branch is to be built, as much of
     *  this work is irrelevant in that usecase.
     */
    public Collection<Revision> getCandidateRevisions()
    {
        // 1. Get all the (branch) revisions that exist 
        Collection<Revision> revs = utils.getAllBranchRevisions();

        // 2. Filter out any revisions that don't contain any branches that we
        // actually care about (spec)
        for (Iterator i = revs.iterator(); i.hasNext();)
        {
            Revision r = (Revision) i.next();

            // filter out uninteresting branches
            for (Iterator j = r.getBranches().iterator(); j.hasNext();)
            {
                Branch b = (Branch) j.next();
                boolean keep = false;
                for (BranchSpec bspec : gitSCM.getBranches())
                {
                    if (bspec.matches(b.getName()))
                    {
                        keep = true;
                        break;
                    }
                }

                if (!keep) j.remove();

            }

            if (r.getBranches().size() == 0) i.remove();

        }

        // 3. We only want 'tip' revisions
        revs = utils.filterTipBranches(revs);

        // 4. Finally, remove any revisions that have already been built.
        for (Iterator i = revs.iterator(); i.hasNext();)
        {
            Revision r = (Revision) i.next();

            if (hasBeenBuilt(r.getSha1())) i.remove();
        }

        return revs;
    }

    private boolean hasBeenBuilt(ObjectId sha1)
    {
        load();
        return lastBuiltIds.containsValue(sha1);
    }

    public void revisionBuilt(Revision revision, boolean success)
    {
        load();
        lastBuiltRevision = revision;
        for (Branch b : revision.getBranches())
        {
            lastBuiltIds.put(b.getName(), b.getSHA1());
        }
        save();

    }

    public ObjectId getLastBuiltRevisionOfBranch(String branch)
    {
        load();
        return lastBuiltIds.get(branch);
    }

    public Revision getLastBuiltRevision()
    {
        load();
        return lastBuiltRevision;
    }

    private void save()
    {
        try
        {
            System.out.println("Save to file " + storageFile.getAbsolutePath());
            if (!storageFile.exists()) storageFile.createNewFile();

            XStream2 xstream = new XStream2();
            FileOutputStream fos = new FileOutputStream(storageFile);
            BuildInfo s = new BuildInfo();
            s.setVersion(1);
            s.setLastBuiltIds(lastBuiltIds);
            s.setLastBuiltRevision(lastBuiltRevision);
            xstream.toXML(s, fos);
            fos.close();
        }
        catch (Exception ex)
        {
            throw new GitException("Couldn't save build state", ex);
        }
    }

    private void load()
    {
        try
        {
            InputStream is = new FileInputStream(storageFile);
            XStream2 xstream = new XStream2();
            BuildInfo buildInfo = (BuildInfo) xstream.fromXML(is);
            lastBuiltIds = buildInfo.getLastBuiltIds();
            lastBuiltRevision = buildInfo.getLastBuiltRevision();
            is.close();
        }
        catch (Exception ex)
        {
            // File not found, defective or corrupt.

            lastBuiltIds = new HashMap<String, ObjectId>();
            lastBuiltRevision = null;

        }
    }

}
