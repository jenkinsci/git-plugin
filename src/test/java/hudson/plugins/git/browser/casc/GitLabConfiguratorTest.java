package hudson.plugins.git.browser.casc;

import hudson.plugins.git.browser.GitLab;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.model.Mapping;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GitLabConfiguratorTest {
    private final GitLabConfigurator configurator = new GitLabConfigurator();
    private static final ConfigurationContext NULL_CONFIGURATION_CONTEXT = null;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testGetName() {
        assertEquals("gitLab", configurator.getName());
    }

    @Test
    public void testGetTarget() {
        assertEquals("Wrong target class", configurator.getTarget(), GitLab.class);
    }

    @Test
    public void testCanConfigure() {
        assertTrue("Can't configure AdvisorGlobalConfiguration", configurator.canConfigure(GitLab.class));
        assertFalse("Can configure AdvisorRootConfigurator", configurator.canConfigure(GitLabConfigurator.class));
    }

    @Test
    public void testGetImplementedAPI() {
        assertEquals("Wrong implemented API", configurator.getImplementedAPI(), GitLab.class);
    }

    @Test
    public void testGetConfigurators() {
        assertThat(configurator.getConfigurators(NULL_CONFIGURATION_CONTEXT), contains(configurator));
    }

    @Test
    public void testDescribe() throws Exception {
        final Mapping expectedMapping = new Mapping();
        expectedMapping.put("repoUrl", "http://fake");
        expectedMapping.put("version", "1.1");
        final GitLab configuration = new GitLab("http://fake", "1.1");

        final Mapping described = configurator.describe(configuration, NULL_CONFIGURATION_CONTEXT).asMapping();
        assertEquals(expectedMapping.getScalarValue("repoUrl"), described.getScalarValue("repoUrl"));
        assertEquals(expectedMapping.getScalarValue("version"), described.getScalarValue("version"));
    }

    @Test
    public void testInstance() throws Exception {
        final GitLab expectedConfiguration = new GitLab("http://fake", "2.0");
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "http://fake");
        mapping.put("version", "2.0");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }

    @Test
    public void testInstanceWithEmptyRepo() throws Exception {
        final GitLab expectedConfiguration = new GitLab("", "2.0");
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "");
        mapping.put("version", "2.0");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));

    }

    @Test
    public void testInstanceWithNullRepo() throws Exception {
        final GitLab expectedConfiguration = new GitLab(null, "2.0");
        final Mapping mapping = new Mapping();
        mapping.put("version", "2.0");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertThat(instance.getRepoUrl(), isEmptyString());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }


    @Test
    public void testInstanceWithEmptyVersion() throws Exception {
        final GitLab expectedConfiguration = new GitLab("http://fake", "");
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "http://fake");
        mapping.put("version", "");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }

    @Test
    public void testInstanceWithNullVersion() throws Exception {
        // If passing a null, GitLab throws an exception
        final GitLab expectedConfiguration = new GitLab("http://fake", "");
        final Mapping mapping = new Mapping();
        mapping.put("repoUrl", "http://fake");

        final GitLab instance = configurator.instance(mapping, NULL_CONFIGURATION_CONTEXT);
        assertEquals(expectedConfiguration.getRepoUrl(), instance.getRepoUrl());
        assertEquals(String.valueOf(expectedConfiguration.getVersion()), String.valueOf(instance.getVersion()));
    }

    @Test
    public void testInstanceWithNaNVersion() throws Exception {
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
