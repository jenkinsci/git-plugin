package jenkins.plugins.git.traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCM;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class IgnoreOnPushNotificationTrait extends SCMSourceTrait {

    @DataBoundConstructor
    public IgnoreOnPushNotificationTrait() {
    }

    @Override
    protected <B extends SCMSourceContext<B, R>, R extends SCMSourceRequest> void decorateContext(B context) {
        ((GitSCMSourceContext<?, ?>) context).withIgnoreOnPushNotifications(true);
    }

    @Override
    protected <B extends SCMBuilder<B, S>, S extends SCM> void decorateBuilder(B builder) {
        // this next should be strictly not necessary, but we add it anyway just to be safe
        ((GitSCMBuilder<?>) builder).withExtension(new IgnoreNotifyCommit());
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Ignore on push notifications";
        }

        @Override
        public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
            return super.isApplicableToBuilder(builderClass) && GitSCMBuilder.class.isAssignableFrom(builderClass);
        }

        @Override
        public boolean isApplicableToContext(@NonNull Class<? extends SCMSourceContext> contextClass) {
            return super.isApplicableToContext(contextClass) && GitSCMSourceContext.class
                    .isAssignableFrom(contextClass);
        }

        @Override
        public boolean isApplicableToSCM(@NonNull Class<? extends SCM> scmClass) {
            return super.isApplicableToSCM(scmClass) && GitSCMSource.class.isAssignableFrom(scmClass);
        }

        @Override
        public boolean isApplicableTo(SCMSource source) {
            return super.isApplicableTo(source) && source instanceof GitSCMSource;
        }

    }
}
