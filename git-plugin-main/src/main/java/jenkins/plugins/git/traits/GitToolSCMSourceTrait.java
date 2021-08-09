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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Exposes {@link GitTool} configuration of a {@link AbstractGitSCMSource} as a {@link SCMSourceTrait}.
 *
 * @since 3.4.0
 */
public class GitToolSCMSourceTrait extends SCMSourceTrait {
    /**
     * The {@link GitTool#getName()} or {@code null} to use the "system" default.
     */
    @CheckForNull
    private final String gitTool;

    /**
     * Stapler constructor.
     *
     * @param gitTool the {@link GitTool#getName()} or {@code null} to use the "system" default.
     */
    @DataBoundConstructor
    public GitToolSCMSourceTrait(@CheckForNull String gitTool) {
        this.gitTool = Util.fixEmpty(gitTool);
    }

    /**
     * Returns the {@link GitTool#getName()}.
     *
     * @return the {@link GitTool#getName()} or {@code null} to use the "system" default.
     */
    @CheckForNull
    public String getGitTool() {
        return gitTool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        ((GitSCMSourceContext<?,?>)context).withGitTool(gitTool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        ((GitSCMBuilder<?>) builder).withGitTool(gitTool);
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Select Git executable";
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
        public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
            return super.isApplicableToBuilder(builderClass) && getSCMDescriptor().showGitToolOptions();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToContext(@NonNull Class<? extends SCMSourceContext> contextClass) {
            return super.isApplicableToContext(contextClass) && getSCMDescriptor().showGitToolOptions();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCM> getScmClass() {
            return GitSCM.class;
        }

        /**
         * Gets the {@link GitSCM.DescriptorImpl} so that we can delegate some decisions to it.
         *
         * @return the {@link GitSCM.DescriptorImpl}.
         */
        private GitSCM.DescriptorImpl getSCMDescriptor() {
            return (GitSCM.DescriptorImpl) Jenkins.get().getDescriptor(GitSCM.class);
        }

        /**
         * Returns the list of {@link GitTool} items.
         *
         * @return the list of {@link GitTool} items.
         */
        @Restricted(NoExternalUse.class) // stapler
        public ListBoxModel doFillGitToolItems() {
            return getSCMDescriptor().doFillGitToolItems();
        }

    }
}
