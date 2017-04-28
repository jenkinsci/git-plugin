package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import org.eclipse.jgit.transport.RefSpec;

/**
 * @author Stephen Connolly
 */
public class GitSCMSourceRequest extends SCMSourceRequest {

    private List<RefSpec> refSpecs = new ArrayList<>();
    private final String remoteName;
    private final String gitTool;

    /**
     * Constructor.
     *
     * @param source   the source.
     * @param context  the context.
     * @param listener the (optional) {@link TaskListener}.
     */
    public GitSCMSourceRequest(@NonNull SCMSource source, @NonNull GitSCMSourceContext<?, ?> context, TaskListener listener) {
        super(source, context, listener);
        remoteName = context.remoteName();
        gitTool = context.gitTool();
        refSpecs = Collections.unmodifiableList(context.asRefSpecs());
    }

    public String remoteName() {
        return remoteName;
    }

    public String gitTool() {
        return gitTool;
    }

    public List<RefSpec> refSpecs() {
        return refSpecs;
    }
}
