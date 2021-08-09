package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import java.util.ArrayList;
import java.util.List;

public class GitChangeSetSample {

    final GitChangeSet changeSet;

    final String id = "defcc790e89e2f2558d182028cbd4df6602bda2f";
    final String parent = "92ec0aa543f6c871502b0e6f7793a43a4df84519";
    final String authorName = "Mark Waite Author";
    final String committerName = "Mark Waite Committer";
    final String commitTitle = "Revert \"Rename xt.py to my.py, remove xt, add job, modify job\"";
    final String addedFileName = "xt";
    final String deletedFileName = "jobs/JENKINS-20585-busy-changelog-prevents-deletion-a.xml";
    final String modifiedFileName = "jobs/git-plugin-my-multi-2.2.x.xml";
    final String renamedFileSrcName = "mt.py";
    final String renamedFileDstName = "xt.py";

    public GitChangeSetSample(boolean useAuthorName) {
        List<String> gitChangeLog = new ArrayList<>();
        gitChangeLog.add("commit " + id);
        gitChangeLog.add("tree 9538ba330b18d079bf9792e7cd6362fa7cfc8039");
        gitChangeLog.add("parent " + parent);
        gitChangeLog.add("author " + authorName + " <mark.earl.waite@gmail.com> 1415842934 -0700");
        gitChangeLog.add("committer " + committerName + " <markwaite@yahoo.com> 1415842974 -0700");
        gitChangeLog.add("");
        gitChangeLog.add("    " + commitTitle);
        gitChangeLog.add("    ");
        gitChangeLog.add("    This reverts commit 92ec0aa543f6c871502b0e6f7793a43a4df84519.");
        gitChangeLog.add("");
        gitChangeLog.add(":100644 000000 4378b5b0223f0435eb2365a684e6a544c5c537fc 0000000000000000000000000000000000000000 D\t" + deletedFileName);
        gitChangeLog.add(":100644 100644 c305885ca26ad88b0bf96d3bb81e958cf0535194 56aef71694759b71ea76a9dfe377b0e1f8a8388f M\t" + modifiedFileName);
        gitChangeLog.add(":000000 120000 0000000000000000000000000000000000000000 fb9953d5d00cb6307954f6d3bf6cb5d2355f62cd A\t" + addedFileName);
        gitChangeLog.add(":100755 100755 4099f430ffd37d7e5d60aa08f61daffdccb81b2c 4099f430ffd37d7e5d60aa08f61daffdccb81b2c R100	" + renamedFileSrcName + "\t" + renamedFileDstName);
        changeSet = new GitChangeSet(gitChangeLog, useAuthorName);
    }
}
