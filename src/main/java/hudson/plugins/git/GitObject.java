package hudson.plugins.git;
import java.io.Serializable;

public class GitObject implements Serializable {
	String sha1;
	String name;
	
	public GitObject(String name, String sha1) {
		this.name = name;
		this.sha1 = sha1;
	}

	public String getSHA1() {
		return sha1;
	}

	public String getName() {
		return name;
	}
}
