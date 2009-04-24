package hudson.plugins.git.util;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.Action;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;
import hudson.util.XStream2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.spearce.jgit.lib.ObjectId;

public class BuildChooser implements IBuildChooser {  
    
    IGitAPI               git;
    GitUtils              utils;
    GitSCM                gitSCM;

    //-------- Data -----------
    BuildData             data;

    public BuildChooser(GitSCM gitSCM, IGitAPI git, GitUtils utils, BuildData data)
    {
        this.gitSCM = gitSCM;
        this.git = git;
        this.utils = utils;
        this.data = data;
        if( data == null )
            this.data = new BuildData();
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
    	try
    	{
    		for(Build b : data.lastBuiltIds.values())
    		{
    			if( b.branch.getSHA1().equals(sha1) )
    				return true;
    		}
    		
    		return false;
    	}
    	catch(Exception ex)
    	{
    		return false;
    	}
    }

    public void revisionBuilt(Revision revision, int buildNumber, Result result )
    {    	
        data.lastBuiltRevision = revision;
        for (Branch b : revision.getBranches())
        {
        	Build build = new Build(b, buildNumber, result);
        	
            data.lastBuiltIds.put(b.getName(), build);
        }
    }

    public Build getLastBuiltRevisionOfBranch(String branch)
    {
    	try
    	{
    		return data.lastBuiltIds.get(branch);
    	}
    	catch(Exception ex)
    	{
    		return null;
    	}
    }

    public Revision getLastBuiltRevision()
    {
    	try
    	{
    		return data.lastBuiltRevision;
    	}
    	catch(Exception ex)
    	{
    		return null;
    	}
    }
    public Action getData()
    {
        return data;
    }

}
