package hudson.plugins.git.util;

import hudson.model.Result;
import hudson.plugins.git.Revision;

import java.io.Serializable;

import org.spearce.jgit.lib.ObjectId;

public class Build implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	/**
	 * Revision marked as being built.
	 */
	public Revision revision;
	
	/**
	 * Revision that was subject to a merge.
	 */
	public Revision mergeRevision;
	
	public int      hudsonBuildNumber;
	public Result   hudsonBuildResult;
	
	// TODO: We don't currently store the result correctly.
	
	public Build(Revision revision, int buildNumber, Result result) {
		this.revision = revision;
		this.hudsonBuildNumber = buildNumber;
		this.hudsonBuildResult = result;
	}
	
	public ObjectId getSHA1()
	{
		return revision.getSha1();
	}
	
	public @Override String toString()
	{
		String str =  "Build #" + hudsonBuildNumber + " of " + revision.toString();
		if( mergeRevision != null )
			str += " merged with " + mergeRevision;
		return str;
	}
}