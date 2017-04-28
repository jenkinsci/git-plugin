package jenkins.plugins.git.traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.SCM;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceTrait;

public abstract class GitSCMExtensionTrait<E extends GitSCMExtension> extends SCMSourceTrait {
    @NonNull
    private final E extension;

    public GitSCMExtensionTrait(@NonNull E extension) {
        this.extension = extension;
    }

    @NonNull
    public E getExtension() {
        return extension;
    }

    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        ((GitSCMBuilder<?>) builder).withExtension(extension);
    }
}
