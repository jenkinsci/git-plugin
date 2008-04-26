package hudson.plugins.git;

public class Branch extends GitObject
{

	public Branch(String name, String sha1) {
		super(name, sha1);
		// TODO Auto-generated constructor stub
	}
	
	public @Override String toString()
	{
		return "Branch " + name + "(" + sha1 + ")";
	}

}
