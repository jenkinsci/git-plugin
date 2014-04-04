package hudson.plugins.git;

import hudson.Functions;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.plugins.git.util.BuiltRevision;
import hudson.plugins.git.util.BuildData;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintain the branch / revision Map for git-based projects.
 * <p>
 * Compared to (legacy) {@link BuildData}, there is a single BuildHistory per project, centralizing build history to
 * reduce memory/disk footprint
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class BuildHistory implements Action {

    /**
     * Map of branch name -> build (Branch name to last built SHA1).
     *
     * <p>
     * This map contains all the branches we've built in the past (including the build that this {@link BuildData}
     * is attached to)
     */
    private Map<String, BuiltRevision> buildsByBranchName = new HashMap<String, BuiltRevision>();

    public BuiltRevision getLastBuildOfBranch(String branch) {
        return buildsByBranchName.get(branch);
    }

    public BuildHistory(AbstractProject p) {
        if (buildsByBranchName.isEmpty()) {
            migrateLegacyData(p.getLastBuild());
        }
    }

    private void migrateLegacyData(AbstractBuild b) {
        while (b != null) {
            BuildData data = b.getAction(BuildData.class);
            if (data != null) {
                buildsByBranchName.putAll(data.buildsByBranchName);
                return;
            }
            b = b.getPreviousBuild();
        }
    }

    public String getIconFileName() {
        return Functions.getResourcePath()+"/plugin/git/icons/git-32x32.png";
    }

    public String getDisplayName() {
        return "branches";
    }

    public String getUrlName() {
        return "branches";
    }
}