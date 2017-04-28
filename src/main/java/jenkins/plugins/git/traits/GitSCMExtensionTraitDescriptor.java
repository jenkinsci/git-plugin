package jenkins.plugins.git.traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.scm.SCM;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.jvnet.tiger_types.Types;

public abstract class GitSCMExtensionTraitDescriptor extends SCMSourceTraitDescriptor {

    private final Class<? extends GitSCMExtension> extension;
    private final Constructor<? extends SCMSourceTrait> constructor;

    protected GitSCMExtensionTraitDescriptor(Class<? extends SCMSourceTrait> clazz,
                                             Class<? extends GitSCMExtension> extension) {
        super(clazz);
        this.extension = extension;
        if (!Util.isOverridden(GitSCMExtensionTraitDescriptor.class, getClass(), "convertToTrait",
                GitSCMExtension.class)) {
            // check that the GitSCMExtensionTrait has a constructor that takes a single argument of the type
            // 'extension' so that our default convertToTrait method implementation can be used
            try {
                constructor = clazz.getConstructor(extension);
            } catch (NoSuchMethodException e) {
                throw new AssertionError("Could not infer how to convert a " + extension + " to a "
                        + clazz + " as there is no obvious constructor. Either provide a simple constructor or "
                        + "override convertToTrait(GitSCMExtension)", e);
            }
        } else {
            constructor = null;
        }
    }

    protected GitSCMExtensionTraitDescriptor() {
        super();
        Type bt = Types.getBaseClass(clazz, GitSCMExtensionTrait.class);
        if (bt instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) bt;
            // this 'extension' is the closest approximation of E of GitSCMExtensionTrait<E>.
            extension = Types.erasure(pt.getActualTypeArguments()[0]);
            if (!GitSCMExtension.class.isAssignableFrom(extension) || GitSCMExtension.class == extension) {
                throw new AssertionError("Could not infer GitSCMExtension type for outer class " + clazz
                        + " of " + getClass() + ". Perhaps wrong outer class? (or consider using the explicit "
                        + "class constructor)");
            }
        } else {
            throw new AssertionError("Could not infer GitSCMExtension type. Consider using the explicit "
                    + "class constructor)");
        }
        if (!Util.isOverridden(GitSCMExtensionTraitDescriptor.class, getClass(), "convertToTrait",
                GitSCMExtension.class)) {
            // check that the GitSCMExtensionTrait has a constructor that takes a single argument of the type
            // 'extension' so that our default convertToTrait method implementation can be used
            try {
                constructor = clazz.getConstructor(extension);
            } catch (NoSuchMethodException e) {
                throw new AssertionError("Could not infer how to convert a " + extension + " to a "
                        + clazz + " as there is no obvious constructor. Either provide a simple constructor or "
                        + "override convertToTrait(GitSCMExtension)", e);
            }
        } else {
            constructor = null;
        }
    }

    @Override
    public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
        return super.isApplicableToBuilder(builderClass) && GitSCMBuilder.class.isAssignableFrom(builderClass);
    }

    @Override
    public boolean isApplicableToContext(@NonNull Class<? extends SCMSourceContext> contextClass) {
        return super.isApplicableToContext(contextClass) && GitSCMSourceContext.class.isAssignableFrom(contextClass);
    }

    @Override
    public boolean isApplicableToSCM(@NonNull Class<? extends SCM> scmClass) {
        return super.isApplicableToSCM(scmClass) && AbstractGitSCMSource.class.isAssignableFrom(scmClass);
    }

    @Override
    public boolean isApplicableTo(SCMSource source) {
        return super.isApplicableTo(source) && source instanceof AbstractGitSCMSource;
    }

    public GitSCMExtensionDescriptor getExtensionDescriptor() {
        return (GitSCMExtensionDescriptor) Jenkins.getActiveInstance().getDescriptor(extension);
    }

    public Class<? extends GitSCMExtension> getExtensionClass() {
        return extension;
    }

    /**
     * Converts the supplied {@link GitSCMExtension} (which must be of type {@link #getExtensionClass()}) into
     * its corresponding {@link GitSCMExtensionTrait}.
     *
     * The default implementation assumes that the {@link #getT()} has a public constructor taking a single argument
     * of type {@link #getExtensionClass()} and will just call that. Override this method if you need more complex
     * convertion logic, for example {@link LocalBranch} only makes sense for a {@link LocalBranch#getLocalBranch()}
     * value of {@code **} so {@link LocalBranchTrait.DescriptorImpl#convertToTrait(GitSCMExtension)} returns
     * {@code null} for all other {@link LocalBranch} configurations.
     *
     * @param extension the {@link GitSCMExtension} (must be of type {@link #getExtensionClass()})
     * @return the {@link GitSCMExtensionTrait} or {@code null} if the supplied {@link GitSCMExtension} is not
     * appropriate for convertion to a {@link GitSCMExtensionTrait}
     * @throws UnsupportedOperationException if the conversion failed because of a implementation bug.
     */
    @CheckForNull
    public SCMSourceTrait convertToTrait(GitSCMExtension extension) {
        try {
            return constructor.newInstance(this.extension.cast(extension));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassCastException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public String getHelpFile() {
        return getExtensionDescriptor().getHelpFile();
    }
}
