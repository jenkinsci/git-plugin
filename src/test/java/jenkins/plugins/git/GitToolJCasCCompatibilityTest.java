package jenkins.plugins.git;

import hudson.plugins.git.GitTool;
import hudson.tools.BatchCommandInstaller;
import hudson.tools.CommandInstaller;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.tools.ZipExtractionInstaller;
import hudson.util.DescribableList;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class GitToolJCasCCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        final ToolDescriptor descriptor = (ToolDescriptor) restartableJenkinsRule.j.jenkins.getDescriptor(GitTool.class);
        final ToolInstallation[] installations = descriptor.getInstallations();
        assertThat(installations, arrayWithSize(1));
        assertEquals("Default", installations[0].getName());
        assertEquals("git", installations[0].getHome());
        final DescribableList<ToolProperty<?>, ToolPropertyDescriptor> properties = installations[0].getProperties();
        assertThat(properties, hasSize(1));
        final ToolProperty<?> property = properties.get(0);
        assertThat(((InstallSourceProperty)property).installers,
                containsInAnyOrder(
                        allOf(instanceOf(CommandInstaller.class),
                                hasProperty("command", equalTo("install git")),
                                hasProperty("toolHome", equalTo("/my/path/1")),
                                hasProperty("label", equalTo("git command"))),
                        allOf(instanceOf(ZipExtractionInstaller.class),
                                hasProperty("url", equalTo("http://fake.com")),
                                hasProperty("subdir", equalTo("/my/path/2")),
                                hasProperty("label", equalTo("git zip"))),
                        allOf(instanceOf(BatchCommandInstaller.class),
                                hasProperty("command", equalTo("run batch command")),
                                hasProperty("toolHome", equalTo("/my/path/3")),
                                hasProperty("label", equalTo("git batch")))
                ));
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class hudson.plugins.git.GitTool.name = Default";
    }

    @Override
    protected String configResource() {
        return "tool-casc.yaml";
    }
}
