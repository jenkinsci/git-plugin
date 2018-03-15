package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.SCMHeadMixin;

public class GitRefSCMHead extends SCMHead implements SCMHeadMixin {
    private final String ref;

    /**
     * Constructor.
     *
     * @param name the name.
     */
    public GitRefSCMHead(@NonNull String name, String ref) {
        super(name);
        this.ref = ref;
    }

    public String getRef() {
        return ref;
    }
}
