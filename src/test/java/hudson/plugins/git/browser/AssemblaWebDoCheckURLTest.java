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
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(),
                is("Invalid URL"));
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
        String hostname = "assembla.comspaces";
        String url = "https://" + hostname + "/git-plugin/git/source";
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(), is("Invalid URL"));
    }

    @Test
    public void testPathLevelChecksOnRepoUrlSupersetOfAssembla() throws Exception {
        java.util.Random random = new java.util.Random();
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
                is("Invalid URL"));
    }

    @Test
    public void testDomainLevelChecksOnRepoUrlAllowDNSLocalHostnamesLocalNet() throws Exception {
        String hostname = "assembla.example.localnet";
        String url = "https://" + hostname + "/space/git-plugin/git/source";
        FormValidation validation = assemblaWebDescriptor.doCheckRepoUrl(project, url);
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(),
                is("Exception reading from Assembla URL " + url + " : ERROR: " + hostname));
    }

    @Test
    public void testDomainLevelChecksOnRepoUrlAllowDNSLocalHostnamesHome() throws Exception {
        String hostname = "assembla.example.home";
        String url = "https://" + hostname + "/space/git-plugin/git/source";
        FormValidation validation = assemblaWebDescriptor.doCheckRepoUrl(project, url);
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(),
                is("Exception reading from Assembla URL " + url + " : ERROR: " + hostname));
    }

    @Test
    public void testDomainLevelChecksOnRepoUrlCorpDomainMustBeValid() throws Exception {
        String hostname = "assembla.myorg.corp";
        String url = "https://" + hostname + "/space/git-plugin/git/source";
        FormValidation validation = assemblaWebDescriptor.doCheckRepoUrl(project, url);
        assertThat(assemblaWebDescriptor.doCheckRepoUrl(project, url).getLocalizedMessage(),
                is("Exception reading from Assembla URL " + url + " : ERROR: " + hostname));
    }
}
