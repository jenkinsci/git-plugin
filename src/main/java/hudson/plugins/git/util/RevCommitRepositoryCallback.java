package hudson.plugins.git.util;

import hudson.remoting.VirtualChannel;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

/**
 * Retrieves {@link RevCommit} from given {@link Build} revision.
 */
public final class RevCommitRepositoryCallback implements RepositoryCallback<RevCommit> {
    private static final long serialVersionUID = 1L;
    private final Build revToBuild;

    public RevCommitRepositoryCallback(Build revToBuild) {
        this.revToBuild = revToBuild;
    }

    @Override
    public RevCommit invoke(Repository repository, VirtualChannel virtualChannel)
            throws IOException, InterruptedException {
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(revToBuild.revision.getSha1());
        }
    }
}
