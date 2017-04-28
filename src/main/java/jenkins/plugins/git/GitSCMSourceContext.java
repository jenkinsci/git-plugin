package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RefSpec;

public class GitSCMSourceContext<C extends GitSCMSourceContext<C, R>, R extends GitSCMSourceRequest>
        extends SCMSourceContext<C, R> {


    private String remoteName = AbstractGitSCMSource.DEFAULT_REMOTE_NAME;
    private String gitTool;
    private boolean ignoreOnPushNotifications;
    private List<String> refSpecs = new ArrayList<>();

    public GitSCMSourceContext(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer) {
        super(criteria, observer);
    }

    public final C withRemoteName(String remoteName) {
        this.remoteName = StringUtils.defaultIfBlank(remoteName, AbstractGitSCMSource.DEFAULT_REMOTE_NAME);
        return (C) this;
    }

    public final String remoteName() {
        return remoteName;
    }

    public final C withGitTool(String gitTool) {
        this.gitTool = gitTool;
        return (C) this;
    }

    public final String gitTool() {
        return gitTool;
    }

    @Override
    public R newRequest(@NonNull SCMSource source, TaskListener listener) {
        return (R) new GitSCMSourceRequest(source, this, listener);
    }

    public C withIgnoreOnPushNotifications(boolean ignoreOnPushNotifications) {
        this.ignoreOnPushNotifications = ignoreOnPushNotifications;
        return (C) this;
    }

    public boolean ignoreOnPushNotifications() {
        return ignoreOnPushNotifications;
    }

    public C withRefSpecs(List<String> refSpecs) {
        this.refSpecs.clear();
        this.refSpecs.addAll(refSpecs);
        return (C) this;
    }

    public C withRefSpec(String refSpec) {
        this.refSpecs.clear();
        this.refSpecs.add(refSpec);
        return (C) this;
    }

    public List<String> refSpecs() {
        if (refSpecs.isEmpty()) {
            return Collections.singletonList(AbstractGitSCMSource.REF_SPEC_DEFAULT);
        }
        return new ArrayList<>(refSpecs);
    }

    public List<RefSpec> asRefSpecs() {
        List<RefSpec> result = new ArrayList<>(Math.max(refSpecs.size(), 1));
        for (String template : refSpecs()) {
            result.add(new RefSpec(
                    template.replaceAll(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER, remoteName())
            ));
        }
        return result;
    }

}
