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
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.LocalBranch;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class LocalBranchTrait extends GitSCMExtensionTrait<LocalBranch> {
    @DataBoundConstructor
    public LocalBranchTrait() {
        super(new LocalBranch("**"));
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
            return "Check out to matching local branch";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SCMSourceTrait convertToTrait(GitSCMExtension extension) {
            LocalBranch ext = (LocalBranch) extension;
            if ("**".equals(StringUtils.defaultIfBlank(ext.getLocalBranch(), "**"))) {
                return new LocalBranchTrait();
            }
            // does not make sense to have any other type of LocalBranch in the context of SCMSource
            return null;
        }
    }
}
