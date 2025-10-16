package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import java.util.Collections;
import java.util.List;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailFactory;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

@Extension
public final class GitDetailFactory extends DetailFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @NonNull
    @Override
    public List<? extends Detail> createFor(@NonNull Run target) {
        SCMSource src = SCMSource.SourceByItem.findSource(target.getParent());

        if (src instanceof AbstractGitSCMSource gitSource) {
            SCMRevisionAction scmRevisionAction = target.getAction(SCMRevisionAction.class);

            GitRepositoryBrowser repositoryBrowser = gitSource.guessBrowser();

            if (scmRevisionAction == null) {
                return Collections.emptyList();
            }

            return List.of(new GitCommitDetail(target, repositoryBrowser));
        } else {
            // Don't add details for non-Git SCM sources
            return Collections.emptyList();
        }
    }
}