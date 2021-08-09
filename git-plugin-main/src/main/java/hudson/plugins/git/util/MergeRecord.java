package hudson.plugins.git.util;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.plugins.git.GitSCM;

/**
 * When {@link GitSCM} is configured to track another branch by merging it to the
 * current commit for every build, this {@link Action} on {@link AbstractBuild}
 * remembers the state of the branch that was merged.
 *
 * @author Kohsuke Kawaguchi
 */
public class MergeRecord extends InvisibleAction {
    /**
     * The branch that was merged prefixed by the repository name. For example, "origin/master"
     */
    private final String branch;
    /**
     * The SHA1 of the commit that the branch was pointing to when this was built.
     * This is the commit that got merged by Jenkins for a build.
     */
    private final String sha1;

    public MergeRecord(String branch, String sha1) {
        this.branch = branch;
        this.sha1 = sha1;
    }

    public String getBranch() {
        return branch;
    }

    public String getSha1() {
        return sha1;
    }
}
