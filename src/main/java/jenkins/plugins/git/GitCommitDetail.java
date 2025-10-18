package jenkins.plugins.git;

import hudson.model.Run;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import java.io.IOException;
import java.net.URL;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;

public class GitCommitDetail extends Detail {
    private final GitRepositoryBrowser repositoryBrowser;

    public GitCommitDetail(Run run, GitRepositoryBrowser repositoryBrowser) {
        super(run);
        this.repositoryBrowser = repositoryBrowser;
    }

    public String getIconClassName() {
        return getDisplayName() == null ? null : "symbol-git-commit plugin-ionicons-api";
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

        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision && repositoryBrowser != null) {
            String hash = abstractRevision.getHash();

            try {
                URL changeSetLink = repositoryBrowser.getChangeSetLink(hash);
                return changeSetLink != null ? changeSetLink.toString() : null;
            } catch (IOException e) {
                return null;
            }
        }

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
