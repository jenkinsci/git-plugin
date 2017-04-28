package jenkins.plugins.git.traits;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import java.util.Collections;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class RefSpecsSCMSourceTrait extends SCMSourceTrait {
    private final List<RefSpecTemplate> templates;

    @DataBoundConstructor
    public RefSpecsSCMSourceTrait(List<RefSpecTemplate> templates) {
        this.templates = templates;
    }

    public List<RefSpecTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    @Override
    protected <B extends SCMSourceContext<B, R>, R extends SCMSourceRequest> void decorateContext(B context) {
        for (RefSpecTemplate template : templates) {
            ((GitSCMSourceContext) context).withRefSpec(template.getValue());
        }
    }

    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        for (RefSpecTemplate template : templates) {
            ((GitSCMBuilder) builder).withRefSpec(template.getValue());
        }
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Specify ref specs";
        }

        public List<RefSpecTemplate> getDefaultTemplates() {
            return Collections.singletonList(new RefSpecTemplate(AbstractGitSCMSource.REF_SPEC_DEFAULT));
        }
    }

    public static class RefSpecTemplate extends AbstractDescribableImpl<RefSpecTemplate> {
        private final String value;

        @DataBoundConstructor
        public RefSpecTemplate(String value) {
            this.value = StringUtils.trim(value);
        }

        public String getValue() {
            return value;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<RefSpecTemplate> {

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
            }            @Override
            public String getDisplayName() {
                return "Ref Spec";
            }


        }
    }
}
