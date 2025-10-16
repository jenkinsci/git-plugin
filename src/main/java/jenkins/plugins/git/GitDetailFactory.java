package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailFactory;
import jenkins.scm.api.SCMRevision;
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

        // Don't add details for non-Git SCM sources
        if (!(src instanceof AbstractGitSCMSource)) {
            return Collections.emptyList();
        }

        SCMRevisionAction scmRevisionAction = target.getAction(SCMRevisionAction.class);

        if (scmRevisionAction == null) {
            return Collections.emptyList();
        }

        return List.of(new GitCommitDetail(target));
    }
}