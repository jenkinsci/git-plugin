package hudson.plugins.git.util;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.Action;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;


import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;
import hudson.util.XStream2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.stapler.export.ExportedBean;
import org.spearce.jgit.lib.ObjectId;

@ExportedBean(defaultVisibility = 999)
public class BuildData implements Action, Serializable
{
	private static final long serialVersionUID = 1L;

	/**
	 * Map of branch name -> build (Branch name to last built SHA1).
	 */
    public Map<String, Build> buildsByBranchName = new HashMap<String, Build>();
    
    /**
     * The last build that we did.
     */
    public Build              lastBuild;
    
    
    public String getDisplayName()
    {
        return "Git Build Data";
    }
    public String getIconFileName()
    {
        return "/plugin/git/icons/git-32x32.png";
    }
    public String getUrlName()
    {
        return "git";
    }
    
    /**
     * Return true if the history shows this SHA1 has been built.
     * False otherwise.
     * @param sha1
     * @return
     */
    public boolean hasBeenBuilt(ObjectId sha1)
    {
    	try
    	{
    		for(Build b : buildsByBranchName.values())
    		{
    			if( b.revision.getSha1().equals(sha1) )
    				return true;
    		}
    		
    		return false;
    	}
    	catch(Exception ex)
    	{
    		return false;
    	}
    }
    
    public void saveBuild(Build build)
    {
    	lastBuild = build;
    	for( Branch branch : build.revision.getBranches() )
    	{
    		buildsByBranchName.put(branch.getName(), build);
    	}
    }
    
    public Build getLastBuildOfBranch(String branch)
    {
    	try
    	{
    		return buildsByBranchName.get(branch);
    	}
    	catch(Exception ex)
    	{
    		return null;
    	}
    }
    
	public Revision getLastBuiltRevision() {
		return lastBuild==null?null:lastBuild.revision;
	}
    
}
