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
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCM;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class IgnoreOnPushNotificationTrait extends SCMSourceTrait {

    /**
     * Stapler constructor.
     */
    @DataBoundConstructor
    public IgnoreOnPushNotificationTrait() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        ((GitSCMSourceContext<?, ?>) context).withIgnoreOnPushNotifications(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        // this next should be strictly not necessary, but we add it anyway just to be safe
        ((GitSCMBuilder<?>) builder).withExtension(new IgnoreNotifyCommit());
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
            return "Ignore on push notifications";
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
        public Class<? extends SCM> getScmClass() {
            return GitSCM.class;
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
}
