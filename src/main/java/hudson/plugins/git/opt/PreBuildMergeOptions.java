package hudson.plugins.git.opt;

import java.io.Serializable;
import org.spearce.jgit.transport.RemoteConfig;

public class PreBuildMergeOptions implements Serializable
{
	private static final long serialVersionUID = 2L;
	public RemoteConfig mergeRemote = null;
	public String mergeTarget = null;

    public RemoteConfig getMergeRemote()
    {
        return mergeRemote;
    }

    public void setMergeRemote(RemoteConfig mergeRemote)
    {
        this.mergeRemote = mergeRemote;
    }

    public String getMergeTarget()
    {
        return mergeTarget;
    }

    public void setMergeTarget(String mergeTarget)
    {
        this.mergeTarget = mergeTarget;
    }

    public String getRemoteBranchName()
    {
        return mergeRemote.getName() + "/" + mergeTarget;
    }

    public boolean doMerge()
    {
        return mergeTarget != null;
    }
}
