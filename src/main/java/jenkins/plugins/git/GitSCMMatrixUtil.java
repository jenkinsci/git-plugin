package jenkins.plugins.git;

import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;

import java.util.Collection;
import java.util.Collections;

public class GitSCMMatrixUtil {
    public static Collection<Revision> populateCandidatesFromMatrixBuild(Run build, GitSCM scm) {
        try {
            // every MatrixRun should build the same marked commit ID
            if (build instanceof MatrixRun) {
                MatrixBuild parentBuild = ((MatrixRun) build).getParentBuild();
                if (parentBuild != null) {
                    BuildData parentBuildData = scm.getBuildData(parentBuild);
                    if (parentBuildData != null) {
                        Build lastBuild = parentBuildData.lastBuild;
                        if (lastBuild != null)
                            return Collections.singleton(lastBuild.getMarked());
                    }
                }
            }
        } catch (Throwable e) {
            // ignore, empty list below
        }
        // It is weird that the failure case here returns a list while the other returns a singleton
        // set, but that's what the code was previous to making the matrix-project dependency optional.
        return Collections.emptyList();
    }
}
