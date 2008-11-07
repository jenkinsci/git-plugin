package hudson.plugins.git;

public class Tag extends GitObject
{
	public String commitSHA1;
	
	public Tag(String name, String sha1) {
		super(name, sha1);
	}

	/**
	 * Get the sha1 of the commit associated with this tag
	 */
	public String getCommitSHA1() {
		return commitSHA1;
	}

	public void setCommitSHA1(String commitSHA1) {
		this.commitSHA1 = commitSHA1;
	}
}
