package hudson.plugins.git;

import org.spearce.jgit.lib.ObjectId;

public class Branch extends GitObject
{

	public Branch(String name, ObjectId sha1) {
		super(name, sha1);
		// TODO Auto-generated constructor stub
	}
	
	public @Override String toString()
	{
		return "Branch " + name + "(" + sha1 + ")";
	}

}
