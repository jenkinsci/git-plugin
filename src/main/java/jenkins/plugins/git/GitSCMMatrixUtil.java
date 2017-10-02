package jenkins.plugins.git;

import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;

import java.util.Collections;
import java.util.Set;

public class GitSCMMatrixUtil {
    public static Set<Revision> populateCandidatesFromMatrixBuild(Run build, GitSCM scm) {
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
        return Collections.emptySet();
    }
}
