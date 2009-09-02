package hudson.plugins.git.opt;

import java.io.Serializable;

public class PreBuildMergeOptions implements Serializable
{
	private static final long serialVersionUID = 1L;
	public String mergeTarget = null;

    public String getMergeTarget()
    {
        return mergeTarget;
    }

    public void setMergeTarget(String mergeTarget)
    {
        this.mergeTarget = mergeTarget;
    }

    public boolean doMerge()
    {
        return mergeTarget != null;
    }
}
