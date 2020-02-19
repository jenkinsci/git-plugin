package hudson.plugins.git.browser;

import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;


import static org.junit.Assert.assertEquals;

public class AssemblaWebDoCheckURLTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private FreeStyleProject project;
    private AssemblaWeb.AssemblaWebDescriptor assemblaWebDescriptor;

    @Before
    public void setProject() throws Exception {
        project = r.createFreeStyleProject("p1");
        assemblaWebDescriptor = new AssemblaWeb.AssemblaWebDescriptor();
    }

    @Test
    public void testInitialChecksOnRepoUrl() throws Exception {
        String url = "https://app.assembla.com/spaces/git-plugin/git/source";
        // Empty url
        String url2 = "";
        // URL with env variable
        String url3 = "https://www.assembla.com/spaces/$";

        assertEquals(FormValidation.ok(), assemblaWebDescriptor.doCheckRepoUrl(project, url));
        assertEquals(FormValidation.ok(), assemblaWebDescriptor.doCheckRepoUrl(project, url2));
        assertEquals(FormValidation.ok(), assemblaWebDescriptor.doCheckRepoUrl(project, url3));
    }

    @Test
    public void testDomainLevelChecksOnRepoUrl() throws Exception {
        // illegal syntax - Earlier it would open connection for such mistakes but now check resolves it beforehand.
        String url = "https:/assembla.com";
        String url2 = "http//assmebla";

        assertEquals("Invalid URL", assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage());
        assertEquals("Invalid URL", assemblaWebDescriptor.doCheckRepoUrl(project, url2).getLocalizedMessage());
    }

    @Test
    public void testPathLevelChecksOnRepoUrl() throws Exception {
        // Invalid path syntax
        String url = "https://assembla.comspaces/git-plugin/git/source";
        // Syntax issue related specific to Assembla
        String url2 = "https://app.assembla.com/space/git-plugin/git/source";
        // Any path related errors will not be caught except syntax issues
        String url3 = "https://app.assembla.com/spaces";

        assertEquals("Invalid URL", assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage());
        assertEquals("Unable to connect https://app.assembla.com/space/git-plugin/git/source/", assemblaWebDescriptor.doCheckRepoUrl(project, url2).getLocalizedMessage());
        assertEquals(FormValidation.ok(), assemblaWebDescriptor.doCheckRepoUrl(project, url3));
    }
}
