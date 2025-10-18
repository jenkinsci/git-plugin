package jenkins.plugins.git.traits;

import hudson.Util;
import hudson.model.Descriptor;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.util.ArrayList;
import java.util.List;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@WithJenkins
class GitSCMExtensionTraitTest {

    private static JenkinsRule r;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        r = rule;
    }

    public List<GitSCMExtensionTraitDescriptor> descriptors() {
        List<GitSCMExtensionTraitDescriptor> list = new ArrayList<>();
        for (Descriptor<SCMSourceTrait> d : SCMSourceTrait.all()) {
            if (d instanceof GitSCMExtensionTraitDescriptor descriptor) {
                list.add(descriptor);
            }
        }
        return list;
    }

    @Test
    void extensionClassesOverrideEquals() {
        for (GitSCMExtensionTraitDescriptor d : descriptors()) {
            assertThat(d.getExtensionClass().getName() + " overrides equals(Object)",
                    Util.isOverridden(GitSCMExtension.class, d.getExtensionClass(), "equals", Object.class),
                    is(true));
        }
    }

    @Test
    void extensionClassesOverrideHashCode() {
        for (GitSCMExtensionTraitDescriptor d : descriptors()) {
            assertThat(d.getExtensionClass().getName() + " overrides hashCode()",
                    Util.isOverridden(GitSCMExtension.class, d.getExtensionClass(), "hashCode"),
                    is(true));
        }
    }

    @Test
    void extensionClassesOverrideToString() {
        for (GitSCMExtensionTraitDescriptor d : descriptors()) {
            assertThat(d.getExtensionClass().getName() + " overrides toString()",
                    Util.isOverridden(GitSCMExtension.class, d.getExtensionClass(), "toString"),
                    is(true));
        }
    }
}
