package hudson.plugins.git;
import java.io.Serializable;

import org.spearce.jgit.lib.ObjectId;

public class GitObject implements Serializable {

	private static final long serialVersionUID = 1L;

	ObjectId sha1;
	String name;
	
	public GitObject(String name, ObjectId sha1) {
		this.name = name;
		this.sha1 = sha1;
	}

	public ObjectId getSHA1() {
		return sha1;
	}

	public String getName() {
		return name;
	}
}
