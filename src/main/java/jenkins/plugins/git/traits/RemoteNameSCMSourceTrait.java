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
import hudson.scm.SCM;
import hudson.util.FormValidation;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Exposes the remote name used for the fetch in a {@link AbstractGitSCMSource} as a {@link SCMSourceTrait}.
 * When not provided in the {@link AbstractGitSCMSource#getTraits()} the remote name should default to
 * {@link AbstractGitSCMSource#DEFAULT_REMOTE_NAME}
 *
 * @since 3.4.0
 */
public class RemoteNameSCMSourceTrait extends SCMSourceTrait {

    /**
     * The remote name.
     */
    @NonNull
    private final String remoteName;

    /**
     * Stapler constructor.
     *
     * @param remoteName the remote name.
     */
    @DataBoundConstructor
    public RemoteNameSCMSourceTrait(@CheckForNull String remoteName) {
        this.remoteName = validate(StringUtils.defaultIfBlank(
                StringUtils.trimToEmpty(remoteName),
                AbstractGitSCMSource.DEFAULT_REMOTE_NAME
        ));
    }

    /**
     * Gets the remote name.
     *
     * @return the remote name.
     */
    @NonNull
    public String getRemoteName() {
        return remoteName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <B extends SCMSourceContext<B, R>, R extends SCMSourceRequest> void decorateContext(B context) {
        ((GitSCMSourceContext<?, ?>) context).withRemoteName(remoteName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        ((GitSCMBuilder<?>) builder).withRemoteName(remoteName);
    }

    /**
     * Validate a remote name.
     *
     * @param value the name.
     * @return the name.
     * @throws IllegalArgumentException if the name is not valid.
     */
    @NonNull
    private static String validate(@NonNull String value) {
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
            throw new IllegalArgumentException("Remote name cannot contain '..'");
        }
        if (value.contains("//")) {
            throw new IllegalArgumentException("Remote name cannot contain empty path segments");
        }
        if (value.endsWith("/")) {
            throw new IllegalArgumentException("Remote name cannot end with '/'");
        }
        if (value.startsWith("/")) {
            throw new IllegalArgumentException("Remote name cannot start with '/'");
        }
        if (value.endsWith(".lock")) {
            throw new IllegalArgumentException("Remote name cannot end with '.lock'");
        }
        if (value.contains("@{")) {
            throw new IllegalArgumentException("Remote name cannot contain '@{'");
        }
        for (String component : StringUtils.split(value, '/')) {
            if (component.startsWith(".")) {
                throw new IllegalArgumentException("Remote name cannot contain path segments starting with '.'");
            }
            if (component.endsWith(".lock")) {
                throw new IllegalArgumentException("Remote name cannot contain path segments ending with '.lock'");
            }
        }
        for (char c : value.toCharArray()) {
            if (c < 32) {
                throw new IllegalArgumentException("Remote name cannot contain ASCII control characters");
            }
            switch (c) {
                case ':':
                    throw new IllegalArgumentException("Remote name cannot contain ':'");
                case '?':
                    throw new IllegalArgumentException("Remote name cannot contain '?'");
                case '[':
                    throw new IllegalArgumentException("Remote name cannot contain '['");
                case '\\':
                    throw new IllegalArgumentException("Remote name cannot contain '\\'");
                case '^':
                    throw new IllegalArgumentException("Remote name cannot contain '^'");
                case '~':
                    throw new IllegalArgumentException("Remote name cannot contain '~'");
                case ' ':
                    throw new IllegalArgumentException("Remote name cannot contain SPACE");
                case '\t':
                    throw new IllegalArgumentException("Remote name cannot contain TAB");
                case '*':
                    throw new IllegalArgumentException("Remote name cannot contain '*'");
            }
        }
        return value;
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

        /**
         * Performs form validation for a proposed
         *
         * @param value the value to check.
         * @return the validation results.
         */
        @Restricted(NoExternalUse.class) // stapler
        public FormValidation doCheckRemoteName(@QueryParameter String value) {
            value = StringUtils.trimToEmpty(value);
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("You must specify a remote name");
            }
            if (AbstractGitSCMSource.DEFAULT_REMOTE_NAME.equals(value)) {
                return FormValidation.warning("There is no need to configure a remote name of '%s' as "
                        + "this is the default remote name.", AbstractGitSCMSource.DEFAULT_REMOTE_NAME);
            }
            try {
                validate(value);
                return FormValidation.ok();
            } catch (IllegalArgumentException e) {
                return FormValidation.error(e.getMessage());
            }
        }

    }
}
