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
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.SCM;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceTrait;

/**
 * Base class for exposing a {@link GitSCMExtension} as a {@link SCMSourceTrait}.
 *
 * @param <E> the {@link GitSCMExtension} that is being exposed
 * @since 3.4.0
 */
public abstract class GitSCMExtensionTrait<E extends GitSCMExtension> extends SCMSourceTrait {
    /**
     * The extension.
     */
    @NonNull
    private final E extension;

    /**
     * Constructor.
     *
     * @param extension the extension.
     */
    public GitSCMExtensionTrait(@NonNull E extension) {
        this.extension = extension;
    }

    /**
     * Gets the extension.
     *
     * @return the extension.
     */
    @NonNull
    public E getExtension() {
        return extension;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        ((GitSCMBuilder<?>) builder).withExtension(extension);
    }
}
