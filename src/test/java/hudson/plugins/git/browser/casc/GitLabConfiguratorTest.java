package hudson.plugins.git.browser.casc;

import hudson.plugins.git.browser.GitLab;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.model.Mapping;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class GitLabConfiguratorTest {

    private final GitLabConfigurator configurator = new GitLabConfigurator();
    private static final ConfigurationContext NULL_CONFIGURATION_CONTEXT = null;

    @Test
    void testGetName() {
        assertEquals("gitLab", configurator.getName());
    }

    @Test
    void testGetTarget() {
        assertEquals(GitLab.class, configurator.getTarget(), "Wrong target class");
    }

    @Test
    void testCanConfigure() {
        assertTrue(configurator.canConfigure(GitLab.class), "Can't configure AdvisorGlobalConfiguration");
        assertFalse(configurator.canConfigure(GitLabConfigurator.class), "Can configure AdvisorRootConfigurator");
    }

    @Test
    void testGetImplementedAPI() {
        assertEquals(GitLab.class, configurator.getImplementedAPI(), "Wrong implemented API");
    }

    @Test
    void testGetConfigurators() {
        assertThat(configurator.getConfigurators(NULL_CONFIGURATION_CONTEXT), contains(configurator));
    }

    @Test
    @Deprecated
    void testDescribe() throws Exception {
        final Mapping expectedMapping = new Mapping();
        expectedMapping.put("repoUrl", "http://fake");
        expectedMapping.put("version", "1.1");
        final GitLab configuration = new GitLab("http://fake", "1.1");

        final Mapping described = configurator.describe(configuration, NULL_CONFIGURATION_CONTEXT).asMapping();
        assertEquals(expectedMapping.getScalarValue("repoUrl"), described.getScalarValue("repoUrl"));
        assertEquals(expectedMapping.getScalarValue("version"), described.getScalarValue("version"));
    }

    @Test
    @Deprecated
    void testInstance() throws Exception {
        final GitLab expectedConfiguration = new GitLab("http://fake", "2.0");
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "http://fake");
        mapping.put("version", "2.0");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }

    @Test
    @Deprecated
    void testInstanceWithEmptyRepo() throws Exception {
        final GitLab expectedConfiguration = new GitLab("", "2.0");
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "");
        mapping.put("version", "2.0");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));

    }

    @Test
    @Deprecated
    void testInstanceWithNullRepo() throws Exception {
        final GitLab expectedConfiguration = new GitLab(null, "2.0");
        final Mapping mapping = new Mapping();
        mapping.put("version", "2.0");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertThat(instance.getRepoUrl(), isEmptyString());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }


    @Test
    @Deprecated
    void testInstanceWithEmptyVersion() throws Exception {
        final GitLab expectedConfiguration = new GitLab("http://fake", "");
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "http://fake");
        mapping.put("version", "");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }

    @Test
    @Deprecated
    void testInstanceWithNullVersion() throws Exception {
        // If passing a null, GitLab throws an exception
        final GitLab expectedConfiguration = new GitLab("http://fake", "");
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "http://fake");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }

    @Test
    @Deprecated
    void testInstanceWithNullMapping() throws Exception {
        // A null mapping should create an instance with empty arguments
        final GitLab expectedConfiguration = new GitLab("", "");
        final Mapping mapping = null;
        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }

    @Test
    @Deprecated
    void testInstanceWithNaNVersion() throws Exception {
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "http://fake");
        mapping.put("version", "NaN");
        // When version is NaN, then GitLab uses the DEFAULT_VERSION. It's the same result as using an empty String
        final GitLab expectedConfiguration = new GitLab("http://fake", "");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }

}
