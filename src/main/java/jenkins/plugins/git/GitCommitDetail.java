package jenkins.plugins.git;

import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;

public class GitCommitDetail extends Detail {
    public GitCommitDetail(Run run) {
        super(run);
    }

    public String getIconClassName() {
        return "symbol-git-commit-outline plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        SCMRevision revision = getRevision();

        if (revision == null) {
            return null;
        }

        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
            return abstractRevision.getHash().substring(0, 7);
        }

        return null;
    }

    @Override
    public String getLink() {
        SCMRevision revision = getRevision();

        if (revision == null) {
            return null;
        }

//        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
//            return new GitHubRepositoryDetail(getObject()).getLink() + "/commit/" + abstractRevision.getHash();
//        }

        return null;
    }

    @Override
    public DetailGroup getGroup() {
        return SCMDetailGroup.get();
    }

    private SCMRevision getRevision() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);


        if (scmRevisionAction == null) {
            return null;
        }

        return scmRevisionAction.getRevision();
    }
}