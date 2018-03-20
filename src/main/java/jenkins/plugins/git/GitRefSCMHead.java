package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;

public class GitRefSCMHead extends SCMHead implements GitSCMHeadMixin {
    private final String ref;

    /**
     * Constructor.
     *
     * @param name the name.
     */
    public GitRefSCMHead(@NonNull String name, @NonNull String ref) {
        super(name);
        this.ref = ref;
    }

    /**
     * Constructor where ref and name is the same.
     *
     * @param name the name (and the ref).
     */
    public GitRefSCMHead(@NonNull String name) {
        this(name, name);
    }

    @Override
    public String getRef() {
        return ref;
    }
}
