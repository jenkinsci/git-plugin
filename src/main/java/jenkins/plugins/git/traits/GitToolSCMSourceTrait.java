package jenkins.plugins.git.traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Stephen Connolly
 */
public class GitToolSCMSourceTrait extends SCMSourceTrait {
    private final String gitTool;

    @DataBoundConstructor
    public GitToolSCMSourceTrait(String gitTool) {
        this.gitTool = gitTool;
    }

    public String getGitTool() {
        return gitTool;
    }

    @Override
    protected <B extends SCMSourceContext<B, R>, R extends SCMSourceRequest> void decorateContext(B context) {
        ((GitSCMSourceContext<?,?>)context).withGitTool(gitTool);
    }

    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        ((GitSCMBuilder<?>) builder).withGitTool(gitTool);
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Select Git executable";
        }

        @Override
        public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
            return super.isApplicableToBuilder(builderClass) && GitSCMBuilder.class.isAssignableFrom(builderClass)
                    && getSCMDescriptor().showGitToolOptions();
        }

        @Override
        public boolean isApplicableToContext(@NonNull Class<? extends SCMSourceContext> contextClass) {
            return super.isApplicableToContext(contextClass) && GitSCMSourceContext.class
                    .isAssignableFrom(contextClass);
        }

        @Override
        public boolean isApplicableToSCM(@NonNull Class<? extends SCM> scmClass) {
            return super.isApplicableToSCM(scmClass) && AbstractGitSCMSource.class.isAssignableFrom(scmClass);
        }

        @Override
        public boolean isApplicableTo(SCMSource source) {
            return super.isApplicableTo(source) && source instanceof AbstractGitSCMSource;
        }

        private GitSCM.DescriptorImpl getSCMDescriptor() {
            return (GitSCM.DescriptorImpl) Jenkins.getActiveInstance().getDescriptor(GitSCM.class);
        }

        public ListBoxModel doFillGitToolItems() {
            return getSCMDescriptor().doFillGitToolItems();
        }

    }
}
