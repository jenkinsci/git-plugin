/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
 */
package jenkins.plugins.git.traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.SCMHeadMixin;
import jenkins.scm.api.mixin.TagSCMHead;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMHeadAuthorityDescriptor;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link Discovery} trait for Git that will discover branches on the repository.
 *
 * @since 3.4.0
 */
public class BranchDiscoveryTrait extends SCMSourceTrait {
    /**
     * Constructor for stapler.
     */
    @DataBoundConstructor
    public BranchDiscoveryTrait() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitSCMSourceContext<?,?> ctx = (GitSCMSourceContext<?, ?>) context;
        ctx.wantBranches(true);
        ctx.withAuthority(new BranchSCMHeadAuthority());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category.isUncategorized();
    }

    /**
     * BranchDiscoveryTrait descriptor.
     */
    @Symbol("gitBranchDiscovery")
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.BranchDiscoveryTrait_displayName();
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
        public Class<? extends SCMSource> getSourceClass() {
            return GitSCMSource.class;
        }
    }

    /**
     * Trusts branches from the repository.
     */
    public static class BranchSCMHeadAuthority extends SCMHeadAuthority<SCMSourceRequest, SCMHead, SCMRevision> {
        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull SCMHead head) {
            return true;
        }

        /**
         * Out descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {
            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.BranchDiscoveryTrait_authorityDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Default.class.isAssignableFrom(originClass);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToHead(@NonNull Class<? extends SCMHeadMixin> headClass) {
                return super.isApplicableToHead(headClass) && !(TagSCMHead.class.isAssignableFrom(headClass));
            }
        }
    }
}
