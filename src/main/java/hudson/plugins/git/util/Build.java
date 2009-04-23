package hudson.plugins.git.util;

import hudson.model.Result;
import hudson.plugins.git.Branch;

import java.io.Serializable;

public class Build implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	// TODO: We don't currently store the result correctly.
	
	public Build(Branch b, int buildNumber, Result result) {
		this.branch = b;
		this.hudsonBuildNumber = buildNumber;
		this.hudsonBuildResult = result;
	}
	
	public Branch 	branch;
	public int      hudsonBuildNumber;
	public Result   hudsonBuildResult;
}