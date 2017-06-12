/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package jenkins.plugins.git.traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.plugins.git.GitSCM;
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
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.api.trait.SCMTrait;
import org.jvnet.tiger_types.Types;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Base class for the {@link Descriptor} of a {@link GitSCMExtension}.
 *
 * @since 3.4.0
 */
public abstract class GitSCMExtensionTraitDescriptor extends SCMSourceTraitDescriptor {

    /**
     * The type of {@link GitSCMExtension}.
     */
    @NonNull
    private final Class<? extends GitSCMExtension> extension;
    /**
     * The constructor to use in {@link #convertToTrait(GitSCMExtension)} or {@code null} if the implementation
     * class is handling convertion.
     */
    @CheckForNull
    private final Constructor<? extends SCMSourceTrait> constructor;
    /**
     * {@code true} if {@link #constructor} does not take any parameters, {@code false} if it takes a single parameter
     * of type {@link GitSCMExtension}.
     */
    private final boolean noArgConstructor;

    /**
     * Constructor to use when type inferrence using {@link #GitSCMExtensionTraitDescriptor()} does not work.
     *
     * @param clazz     Pass in the type of {@link SCMTrait}
     * @param extension Pass in the type of {@link GitSCMExtension}.
     */
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
                noArgConstructor = constructor.getParameterTypes().length == 0;
            } catch (NoSuchMethodException e) {
                throw new AssertionError("Could not infer how to convert a " + extension + " to a "
                        + clazz + " as there is no obvious constructor. Either provide a simple constructor or "
                        + "override convertToTrait(GitSCMExtension)", e);
            }
        } else {
            constructor = null;
            noArgConstructor = false;
        }
    }

    /**
     * Infers the type of the corresponding {@link GitSCMExtensionTrait} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     */
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
            Constructor<? extends SCMSourceTrait> constructor = null;
            for (Constructor<?> c : clazz.getConstructors()) {
                if (c.getAnnotation(DataBoundConstructor.class) != null) {
                    constructor = (Constructor<? extends SCMSourceTrait>) c;
                    break;
                }
            }
            if (constructor != null) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 0) {
                    this.constructor = constructor;
                    this.noArgConstructor = true;
                } else if (parameterTypes.length == 1 && extension.equals(parameterTypes[0])) {
                    this.constructor = constructor;
                    this.noArgConstructor = false;
                } else {
                    throw new AssertionError("Could not infer how to convert a " + extension + " to a "
                            + clazz + " as the @DataBoundConstructor is neither zero arg nor single arg of type "
                            + extension + ". Either provide a simple constructor or override "
                            + "convertToTrait(GitSCMExtension)");
                }
            } else {
                throw new AssertionError("Could not infer how to convert a " + extension + " to a "
                        + clazz + " as there is no @DataBoundConstructor (which is going to cause other problems)");
            }
        } else {
            constructor = null;
            this.noArgConstructor = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends SCMBuilder> getBuilderClass() {
        return GitSCMBuilder.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends SCMSourceContext> getContextClass() {
        return GitSCMSourceContext.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends SCM> getScmClass() {
        return GitSCM.class;
    }

    /**
     * Returns the {@link GitSCMExtensionDescriptor} for this {@link #getExtensionClass()}.
     *
     * @return the {@link GitSCMExtensionDescriptor} for this {@link #getExtensionClass()}.
     */
    @Restricted(NoExternalUse.class) // intended for use from stapler / jelly only
    public GitSCMExtensionDescriptor getExtensionDescriptor() {
        return (GitSCMExtensionDescriptor) Jenkins.getActiveInstance().getDescriptor(extension);
    }

    /**
     * Returns the type of {@link GitSCMExtension} that the {@link GitSCMExtensionTrait} wraps.
     *
     * @return the type of {@link GitSCMExtension} that the {@link GitSCMExtensionTrait} wraps.
     */
    public Class<? extends GitSCMExtension> getExtensionClass() {
        return extension;
    }

    /**
     * Converts the supplied {@link GitSCMExtension} (which must be of type {@link #getExtensionClass()}) into
     * its corresponding {@link GitSCMExtensionTrait}.
     *
     * The default implementation assumes that the {@link #clazz} has a public constructor taking either no arguments
     * or a single argument of type {@link #getExtensionClass()} and will just call that. Override this method if you
     * need more complex convertion logic, for example {@link LocalBranch} only makes sense for a
     * {@link LocalBranch#getLocalBranch()} value of {@code **} so
     * {@link LocalBranchTrait.DescriptorImpl#convertToTrait(GitSCMExtension)} returns {@code null} for all other
     * {@link LocalBranch} configurations.
     *
     * @param extension the {@link GitSCMExtension} (must be of type {@link #getExtensionClass()})
     * @return the {@link GitSCMExtensionTrait} or {@code null} if the supplied {@link GitSCMExtension} is not
     * appropriate for convertion to a {@link GitSCMExtensionTrait}
     * @throws UnsupportedOperationException if the conversion failed because of a implementation bug.
     */
    @CheckForNull
    public SCMSourceTrait convertToTrait(@NonNull GitSCMExtension extension) {
        if (!this.extension.isInstance(extension)) {
            throw new IllegalArgumentException(
                    "Expected a " + this.extension.getName() + " but got a " + extension.getClass().getName()
            );
        }
        if (constructor == null) {
            if (!Util.isOverridden(GitSCMExtensionTraitDescriptor.class, getClass(), "convertToTrait",
                    GitSCMExtension.class)) {
                throw new IllegalStateException("Should not be able to instantiate a " + getClass().getName()
                        + " without an inferred constructor for " + this.extension.getName());
            }
            throw new UnsupportedOperationException(
                    getClass().getName() + " should not delegate convertToTrait() to " + GitSCMExtension.class
                            .getName());
        }
        try {
            return noArgConstructor
                    ? constructor.newInstance()
                    : constructor.newInstance(this.extension.cast(extension));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassCastException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHelpFile() {
        String primary = super.getHelpFile();
        return primary == null ? getExtensionDescriptor().getHelpFile() : primary;
    }
}
