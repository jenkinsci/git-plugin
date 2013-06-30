package hudson.plugins.git.rebuild;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.plugins.git.GitSCM;

import com.sonyericsson.rebuild.RebuildValidator;

/**
 * Implementing this extension causes our custom GitRebuild action
 * to be preferred over the original Rebuild plugin action 
 */
@Extension
public class GitRebuildValidator extends RebuildValidator {

	private static final long serialVersionUID = 2247671466222931174L;

	@Override
	public boolean isApplicable(AbstractBuild build) {
		return build.getProject().getScm() instanceof GitSCM;
	}
	
}
