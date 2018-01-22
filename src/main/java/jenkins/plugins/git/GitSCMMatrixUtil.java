package jenkins.plugins.git;

import hudson.model.AbstractBuild;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Collections;
import java.util.Set;

/**
 * Utility methods for integrating with Matrix Project plugin.
 */
@Restricted(NoExternalUse.class)
public class GitSCMMatrixUtil {
    public static Set<Revision> populateCandidatesFromRootBuild(AbstractBuild build, GitSCM scm) {
        // every MatrixRun should build the same marked commit ID
        AbstractBuild parentBuild = (build).getRootBuild();
        if (parentBuild != null) {
            BuildData parentBuildData = scm.getBuildData(parentBuild);
            if (parentBuildData != null) {
                Build lastBuild = parentBuildData.lastBuild;
                if (lastBuild != null)
                    return Collections.singleton(lastBuild.getMarked());
            }
        }
        return Collections.emptySet();
    }
}
