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

import hudson.Extension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Exposes {@link CleanCheckout} as a {@link SCMSourceTrait}.
 *
 * @since 3.4.0
 */
public class CleanAfterCheckoutTrait extends GitSCMExtensionTrait<CleanCheckout> {

    /**
     * @deprecated Use constructor that accepts extension instead.
     */
    @Deprecated
    public CleanAfterCheckoutTrait() {
        this(null);
    }

    /**
     * Stapler constructor.
     *
     * @param extension the option to clean subdirectories which contain git repositories.
     */
    @DataBoundConstructor
    public CleanAfterCheckoutTrait(@CheckForNull CleanCheckout extension) {
        super(extension == null ? new CleanCheckout() : extension);
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Clean after checkout";
        }
    }
}
