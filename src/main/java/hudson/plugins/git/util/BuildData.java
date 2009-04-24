package hudson.plugins.git.util;

import hudson.model.Action;
import hudson.plugins.git.Revision;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 999)
public class BuildData implements Action, Serializable
{
	private static final long serialVersionUID = 1L;

	/**
	 * Map of branch name -> objectId (Branch name to last built SHA1).
	 */
    public Map<String, Build> lastBuiltIds = new HashMap<String, Build>();
    
    /**
     * The last revision we built.
     */
    public Revision              lastBuiltRevision;
    
    
    public String getDisplayName()
    {
        return "Git Build Data";
    }
    public String getIconFileName()
    {
        return "/plugin/git/icons/git-32x32.png";
    }
    public String getUrlName()
    {
        return "git";
    }
}
