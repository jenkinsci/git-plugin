package hudson.plugins.git.util;

import hudson.plugins.git.Revision;

import java.util.Map;

import org.spearce.jgit.lib.ObjectId;

public class BuildInfo {
	private long version;
	private Map <String,ObjectId> lastBuiltIds;
	private Revision lastBuiltRevision;

	public void setVersion(long version) {
		this.version = version;
	}
	public long getVersion() {
		return version;
	}
	public void setLastBuiltIds(Map <String,ObjectId> lastBuiltIds) {
		this.lastBuiltIds = lastBuiltIds;
	}
	public Map <String,ObjectId> getLastBuiltIds() {
		return lastBuiltIds;
	}
	public void setLastBuiltRevision(Revision lastBuiltRevision) {
		this.lastBuiltRevision = lastBuiltRevision;
	}
	public Revision getLastBuiltRevision() {
		return lastBuiltRevision;
	}
}
