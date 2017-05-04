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
import hudson.model.Descriptor;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Exposes {@link GitRepositoryBrowser} configuration of a {@link AbstractGitSCMSource} as a {@link SCMSourceTrait}.
 *
 * @since 3.4.0
 */
public class GitBrowserSCMSourceTrait extends SCMSourceTrait {

    /**
     * The configured {@link GitRepositoryBrowser} or {@code null} to use the "auto" browser.
     */
    @CheckForNull
    private final GitRepositoryBrowser browser;

    /**
     * Stapler constructor.
     *
     * @param browser the {@link GitRepositoryBrowser} or {@code null} to use the "auto" browser.
     */
    @DataBoundConstructor
    public GitBrowserSCMSourceTrait(@CheckForNull GitRepositoryBrowser browser) {
        this.browser = browser;
    }

    /**
     * Gets the {@link GitRepositoryBrowser}..
     *
     * @return the {@link GitRepositoryBrowser} or {@code null} to use the "auto" browser.
     */
    @CheckForNull
    public GitRepositoryBrowser getBrowser() {
        return browser;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        ((GitSCMBuilder<?>) builder).withBrowser(browser);
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
            return "Configure Repository Browser";
        }

        /**
         * Expose the {@link GitRepositoryBrowser} instances to stapler.
         *
         * @return the {@link GitRepositoryBrowser} instances
         */
        @Restricted(NoExternalUse.class) // stapler
        public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
            return ((GitSCM.DescriptorImpl) Jenkins.getActiveInstance().getDescriptor(GitSCM.class))
                    .getBrowserDescriptors();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
            return super.isApplicableToBuilder(builderClass)
                    && GitSCMBuilder.class.isAssignableFrom(builderClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToContext(@NonNull Class<? extends SCMSourceContext> contextClass) {
            return super.isApplicableToContext(contextClass)
                    && GitSCMSourceContext.class.isAssignableFrom(contextClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToSCM(@NonNull Class<? extends SCM> scmClass) {
            return super.isApplicableToSCM(scmClass)
                    && AbstractGitSCMSource.class.isAssignableFrom(scmClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableTo(@NonNull SCMSource source) {
            return super.isApplicableTo(source)
                    && source instanceof AbstractGitSCMSource;
        }
    }
}
