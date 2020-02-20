package hudson.plugins.git.browser;

import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class AssemblaWebDoCheckURLTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();
    private static int counter = 0;

    private FreeStyleProject project;
    private AssemblaWeb.AssemblaWebDescriptor assemblaWebDescriptor;

    @Before
    public void setProject() throws Exception {
        project = r.createFreeStyleProject("assembla-project-" + counter++);
        assemblaWebDescriptor = new AssemblaWeb.AssemblaWebDescriptor();
    }

    @Test
    public void testInitialChecksOnRepoUrl() throws Exception {
        String url = "https://app.assembla.com/spaces/git-plugin/git/source";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url), is(FormValidation.ok()));
    }

    @Test
    public void testInitialChecksOnRepoUrlEmpty() throws Exception {
        String url = "";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url), is(FormValidation.ok()));
    }

    @Test
    public void testInitialChecksOnRepoUrlWithVariable() throws Exception {
        String url = "https://www.assembla.com/spaces/$";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url), is(FormValidation.ok()));
    }

    @Test
    public void testDomainLevelChecksOnRepoUrl() throws Exception {
        // Invalid URL, missing '/' character - Earlier it would open connection for such mistakes but now check resolves it beforehand.
        String url = "https:/assembla.com";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(), is("Invalid URL"));
    }

    @Test
    public void testDomainLevelChecksOnRepoUrlInvalidURL() throws Exception {
        // Invalid URL, missing ':' character - Earlier it would open connection for such mistakes but now check resolves it beforehand.
        String url = "http//assmebla";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(), is("Invalid URL"));
    }

    @Test
    public void testPathLevelChecksOnRepoUrlInvalidPathSyntax() throws Exception {
        // Invalid hostname in URL
        String url = "https://assembla.comspaces/git-plugin/git/source";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(), is("Invalid URL"));
    }

    @Test
    public void testPathLevelChecksOnRepoUrlValidURLNullProject() throws Exception {
        String url = "https://app.assembla.com/space/git-plugin/git/source";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(null, url), is(FormValidation.ok()));
    }

    @Test
    public void testPathLevelChecksOnRepoUrlUnableToConnect() throws Exception {
        // Syntax issue related specific to Assembla
        String url = "https://app.assembla.com/space/git-plugin/git/source";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(),
                is("Unable to connect https://app.assembla.com/space/git-plugin/git/source/"));
    }

    @Test
    public void testPathLevelChecksOnRepoUrl() throws Exception {
        // Any path related errors will not be caught except syntax issues
        String url = "https://app.assembla.com/spaces/";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url), is(FormValidation.ok()));
    }

    @Test
    public void testPathLevelChecksOnRepoUrlSupersetOfAssembla() throws Exception {
        Random random = new Random();
        String [] urls = {
          "http://assemblage.com/",
          "http://assemblage.net/",
          "http://assemblage.org/",
          "http://assemblages.com/",
          "http://assemblages.net/",
          "http://assemblages.org/",
          "http://assemblagist.com/",
        };
        String url = urls[random.nextInt(urls.length)]; // Don't abuse a single web site with tests
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(),
                is("This is a valid URL but it does not look like Assembla"));
    }
}
