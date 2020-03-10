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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Exposes the ref specs of a {@link AbstractGitSCMSource} as a {@link SCMSourceTrait}.
 * The ref specs are stored as templates which are intended to be realised by applying
 * {@link String#replaceAll(String, String)} with the {@link AbstractGitSCMSource#REF_SPEC_REMOTE_NAME_PLACEHOLDER}
 * pattern to inject the remote name (which should default to {@link AbstractGitSCMSource#DEFAULT_REMOTE_NAME}
 *
 * @since 3.4.0
 */
public class RefSpecsSCMSourceTrait extends SCMSourceTrait {
    /**
     * The ref spec templates.
     */
    @NonNull
    private final List<RefSpecTemplate> templates;

    /**
     * Stapler constructor.
     *
     * @param templates the templates.
     */
    @DataBoundConstructor
    public RefSpecsSCMSourceTrait(@CheckForNull List<RefSpecTemplate> templates) {
        this.templates = new ArrayList<>(Util.fixNull(templates));
    }

    /**
     * Utility constructor.
     *
     * @param templates the template strings.
     */
    public RefSpecsSCMSourceTrait(String... templates) {
        this.templates = new ArrayList<>(templates.length);
        for (String t : templates) {
            this.templates.add(new RefSpecTemplate(t));
        }
    }

    /**
     * Gets the templates.
     *
     * @return the templates.
     */
    @NonNull
    public List<RefSpecTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    /**
     * Unwraps the templates.
     *
     * @return the templates.
     */
    public List<String> asStrings() {
        List<String> result = new ArrayList<>(templates.size());
        for (RefSpecTemplate t : templates) {
            result.add(t.getValue());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        for (RefSpecTemplate template : templates) {
            ((GitSCMSourceContext) context).withRefSpec(template.getValue());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        for (RefSpecTemplate template : templates) {
            ((GitSCMBuilder) builder).withRefSpec(template.getValue());
        }
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
            return "Specify ref specs";
        }

        /**
         * Returns the default templates.
         *
         * @return the default templates.
         */
        public List<RefSpecTemplate> getDefaultTemplates() {
            return Collections.singletonList(new RefSpecTemplate(AbstractGitSCMSource.REF_SPEC_DEFAULT));
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

    }

    /**
     * Represents a single wrapped template for easier form binding.
     *
     * @since 3.4.0
     */
    public static class RefSpecTemplate extends AbstractDescribableImpl<RefSpecTemplate> {
        /**
         * The wrapped template value.
         */
        @NonNull
        private final String value;

        /**
         * Stapler constructor.
         *
         * @param value the template to wrap.
         */
        @DataBoundConstructor
        public RefSpecTemplate(@NonNull String value) {
            this.value = StringUtils.trim(value);
        }

        /**
         * Gets the template value.
         *
         * @return the template value.
         */
        @NonNull
        public String getValue() {
            return value;
        }

        /**
         * The {@link Descriptor} for {@link RefSpecTemplate}.
         *
         * @since 3.4.0
         */
        @Extension
        public static class DescriptorImpl extends Descriptor<RefSpecTemplate> {

            /**
             * Form validation for {@link RefSpecTemplate#getValue()}
             *
             * @param value the value to check.
             * @return the validation result.
             */
            @Restricted(NoExternalUse.class) // stapler
            public FormValidation doCheckValue(@QueryParameter String value) {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.error("No ref spec provided");
                }
                value = StringUtils.trim(value);
                try {
                    String spec = value.replaceAll(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER, "origin");
                    if (spec.contains("@{")) {
                        return FormValidation.errorWithMarkup("Invalid placeholder only <code>"
                                + Util.escape(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR)
                                + "</code> is supported as a placeholder for the remote name");
                    }
                    new RefSpec(spec);
                    if (!value.startsWith("+")) {
                        return FormValidation.warningWithMarkup(
                                "It is recommended to ensure references are always updated by prefixing with "
                                        + "<code>+</code>"
                        );
                    }
                    return FormValidation.ok();
                } catch (IllegalArgumentException e) {
                    return FormValidation.error(e.getMessage());
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return "Ref Spec";
            }
        }
    }
}
