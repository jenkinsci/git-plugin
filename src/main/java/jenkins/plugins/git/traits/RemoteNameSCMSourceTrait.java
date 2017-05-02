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
import hudson.Extension;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class RemoteNameSCMSourceTrait extends SCMSourceTrait {

    private final String remoteName;

    @DataBoundConstructor
    public RemoteNameSCMSourceTrait(String remoteName) {
        this.remoteName = StringUtils
                .defaultIfBlank(StringUtils.trimToEmpty(remoteName), AbstractGitSCMSource.DEFAULT_REMOTE_NAME);
    }

    public String getRemoteName() {
        return remoteName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        ((GitSCMBuilder<?>) builder).withRemoteName(remoteName);
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
            return "Configure remote name";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
            return super.isApplicableToBuilder(builderClass) && GitSCMBuilder.class.isAssignableFrom(builderClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToContext(@NonNull Class<? extends SCMSourceContext> contextClass) {
            return super.isApplicableToContext(contextClass) && GitSCMSourceContext.class
                    .isAssignableFrom(contextClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToSCM(@NonNull Class<? extends SCM> scmClass) {
            return super.isApplicableToSCM(scmClass) && AbstractGitSCMSource.class.isAssignableFrom(scmClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableTo(SCMSource source) {
            return super.isApplicableTo(source) && source instanceof AbstractGitSCMSource;
        }

        public FormValidation doCheckRemoteName(@QueryParameter String value) {
            value = StringUtils.trimToEmpty(value);
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("You must specify a remote name");
            }
            if (AbstractGitSCMSource.DEFAULT_REMOTE_NAME.equals(value)) {
                return FormValidation.warning("There is no need to configure a remote name of '%s' as "
                        + "this is the default remote name.", AbstractGitSCMSource.DEFAULT_REMOTE_NAME);
            }
            // see https://github.com/git/git/blob/027a3b943b444a3e3a76f9a89803fc10245b858f/refs.c#L61-L68
            /*
             * - any path component of it begins with ".", or
             * - it has double dots "..", or
             * - it has ASCII control characters, or
             * - it has ":", "?", "[", "\", "^", "~", SP, or TAB anywhere, or
             * - it has "*" anywhere unless REFNAME_REFSPEC_PATTERN is set, or
             * - it ends with a "/", or
             * - it ends with ".lock", or
             * - it contains a "@{" portion
             */
            if (value.contains("..")) {
                return FormValidation.error("Remote name cannot contain '..'");
            }
            if (value.contains("//")) {
                return FormValidation.error("Remote name cannot contain empty path segments");
            }
            if (value.endsWith("/")) {
                return FormValidation.error("Remote name cannot end with '/'");
            }
            if (value.startsWith("/")) {
                return FormValidation.error("Remote name cannot start with '/'");
            }
            if (value.endsWith(".lock")) {
                return FormValidation.error("Remote name cannot end with '.lock'");
            }
            if (value.contains("@{")) {
                return FormValidation.error("Remote name cannot contain '@{'");
            }
            for (String component : StringUtils.split(value, '/')) {
                if (component.startsWith(".")) {
                    return FormValidation.error("Remote name cannot contain path segments starting with '.'");
                }
                if (component.endsWith(".lock")) {
                    return FormValidation.error("Remote name cannot contain path segments ending with '.lock'");
                }
            }
            for (char c : value.toCharArray()) {
                if (c < 32) {
                    return FormValidation.error("Remote name cannot contain ASCII control characters");
                }
                switch (c) {
                    case ':':
                        return FormValidation.error("Remote name cannot contain ':'");
                    case '?':
                        return FormValidation.error("Remote name cannot contain '?'");
                    case '[':
                        return FormValidation.error("Remote name cannot contain '['");
                    case '\\':
                        return FormValidation.error("Remote name cannot contain '\\'");
                    case '^':
                        return FormValidation.error("Remote name cannot contain '^'");
                    case '~':
                        return FormValidation.error("Remote name cannot contain '~'");
                    case ' ':
                        return FormValidation.error("Remote name cannot contain SPACE");
                    case '\t':
                        return FormValidation.error("Remote name cannot contain TAB");
                    case '*':
                        return FormValidation.error("Remote name cannot contain '*'");
                }
            }
            return FormValidation.ok();
        }

    }
}
