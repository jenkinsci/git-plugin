package jenkins.plugins.git.traits;

import hudson.Util;
import hudson.model.Descriptor;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.util.ArrayList;
import java.util.List;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class GitSCMExtensionTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    public List<GitSCMExtensionTraitDescriptor> descriptors() {
        List<GitSCMExtensionTraitDescriptor> list = new ArrayList<>();
        for (Descriptor<SCMSourceTrait> d : SCMSourceTrait.all()) {
            if (d instanceof GitSCMExtensionTraitDescriptor) {
                list.add((GitSCMExtensionTraitDescriptor) d);
            }
        }
        return list;
    }

    @Test
    public void extensionClassesOverrideEquals() {
        for (GitSCMExtensionTraitDescriptor d : descriptors()) {
            assertThat(d.getExtensionClass().getName() + " overrides equals(Object)",
                    Util.isOverridden(GitSCMExtension.class, d.getExtensionClass(), "equals", Object.class),
                    is(true));
        }
    }

    @Test
    public void extensionClassesOverrideHashCode() {
        for (GitSCMExtensionTraitDescriptor d : descriptors()) {
            assertThat(d.getExtensionClass().getName() + " overrides hashCode()",
                    Util.isOverridden(GitSCMExtension.class, d.getExtensionClass(), "hashCode"),
                    is(true));
        }
    }

    @Test
    public void extensionClassesOverrideToString() {
        for (GitSCMExtensionTraitDescriptor d : descriptors()) {
            assertThat(d.getExtensionClass().getName() + " overrides toString()",
                    Util.isOverridden(GitSCMExtension.class, d.getExtensionClass(), "toString"),
                    is(true));
        }
    }
}
