package jenkins.plugins.git;

public class GitRefSCMRevision extends AbstractGitSCMSource.SCMRevisionImpl {

    public GitRefSCMRevision(GitRefSCMHead head, String hash) {
        super(head, hash);
    }
}
