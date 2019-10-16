package jenkins.plugins.git;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.browser.AssemblaWeb;
import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.browser.CGit;
import hudson.plugins.git.browser.FisheyeGitRepositoryBrowser;
import hudson.plugins.git.browser.GitBlitRepositoryBrowser;
import hudson.plugins.git.browser.GitLab;
import hudson.plugins.git.browser.GitList;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.browser.Gitiles;
import hudson.plugins.git.browser.GitoriousWeb;
import hudson.plugins.git.browser.GogsGit;
import hudson.plugins.git.browser.KilnGit;
import hudson.plugins.git.browser.Phabricator;
import hudson.plugins.git.browser.RedmineWeb;
import hudson.plugins.git.browser.RhodeCode;
import hudson.plugins.git.browser.Stash;
import hudson.plugins.git.browser.TFS2013GitRepositoryBrowser;
import hudson.plugins.git.browser.ViewGitWeb;
import hudson.scm.SCM;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.jenkinsci.plugins.workflow.libs.SCMRetriever;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class BrowsersJCasCCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        final List<LibraryConfiguration> libraries = GlobalLibraries.get().getLibraries();
        assertThat(libraries, containsInAnyOrder(
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withAssembla"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withFisheye"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withKiln"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withMic"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withBitbucket"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withCGit"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withGithub"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withGitiles"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withGitlab"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withGitlist"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withGitorious"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withGitweb"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withGogsgit"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withPhab"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withRedmine"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withRhodecode"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withStash"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withViewgit"))
                ),
                allOf(
                        instanceOf(LibraryConfiguration.class),
                        hasProperty("name", equalTo("withGitlib"))
                )
        ));

        final List<GitRepositoryBrowser> browsers = new ArrayList<>();
        for (LibraryConfiguration library : libraries) {
            final String errorMessage = String.format("Error checking library %s", library.getName());
            final LibraryRetriever retriever = library.getRetriever();
            assertThat(errorMessage, retriever, instanceOf(SCMRetriever.class));
            final SCM scm =  ((SCMRetriever) retriever).getScm();
            assertThat(errorMessage, scm, instanceOf(GitSCM.class));
            final GitSCM gitSCM = (GitSCM)scm;
            assertNotNull(errorMessage, gitSCM.getBrowser());
            browsers.add(gitSCM.getBrowser());
        }

        assertEquals(libraries.size(), browsers.size());
        assertThat(browsers, containsInAnyOrder(
                // AssemblaWeb
                allOf(
                        instanceOf(AssemblaWeb.class),
                        hasProperty("repoUrl", equalTo("http://url.assembla"))
                ),
                // FishEye
                allOf(
                        instanceOf(FisheyeGitRepositoryBrowser.class),
                        hasProperty("repoUrl", equalTo("http://url.fishEye/browse/foobar"))
                ),
                // Kiln
                allOf(
                        instanceOf(KilnGit.class),
                        hasProperty("repoUrl", equalTo("http://url.kiln"))
                ),
                // Microsoft Team Foundation Server/Visual Studio Team Services
                allOf(
                        instanceOf(TFS2013GitRepositoryBrowser.class),
                        hasProperty("repoUrl", equalTo("http://url.mic/_git/foobar/"))
                ),
                // bitbucketweb
                allOf(
                        instanceOf(BitbucketWeb.class),
                        hasProperty("repoUrl", equalTo("http://url.bitbucket"))
                ),
                // cgit
                allOf(
                        instanceOf(CGit.class),
                        hasProperty("repoUrl", equalTo("http://url.cgit"))
                ),
                // gitblit
                allOf(
                        instanceOf(GitBlitRepositoryBrowser.class),
                        hasProperty("repoUrl", equalTo("http://url.gitlib")),
                        hasProperty("projectName", equalTo("my_project"))
                ),
                // githubweb
                allOf(
                        instanceOf(GithubWeb.class),
                        hasProperty("repoUrl", equalTo("http://github.com"))
                ),
                // gitiles
                allOf(
                        instanceOf(Gitiles.class),
                        hasProperty("repoUrl", equalTo("http://url.gitiles"))
                ),
                // gitlab
                allOf(
                        instanceOf(GitLab.class),
                        hasProperty("repoUrl", equalTo("http://gitlab.com")),
                        hasProperty("version", closeTo(1.0, 0.01))
                ),
                // gitlist
                allOf(
                        instanceOf(GitList.class),
                        hasProperty("repoUrl", equalTo("http://url.gitlist"))
                ),
                // gitoriousweb
                allOf(
                        instanceOf(GitoriousWeb.class),
                        hasProperty("repoUrl", equalTo("http://url.gitorious"))
                ),
                // gitweb
                allOf(
                        instanceOf(GitWeb.class),
                        hasProperty("repoUrl", equalTo("http://url.gitweb"))
                ),
                // gogs
                allOf(
                        instanceOf(GogsGit.class),
                        hasProperty("repoUrl", equalTo("http://url.gogs"))
                ),
                // phabricator
                allOf(
                        instanceOf(Phabricator.class),
                        hasProperty("repoUrl", equalTo("http://url.phabricator")),
                        hasProperty("repo", equalTo("my_repository"))
                ),
                // redmineweb
                allOf(
                        instanceOf(RedmineWeb.class),
                        hasProperty("repoUrl", equalTo("http://url.redmineweb"))
                ),
                // rhodecode
                allOf(
                        instanceOf(RhodeCode.class),
                        hasProperty("repoUrl", equalTo("http://url.rhodecode"))
                ),
                // stash
                allOf(
                        instanceOf(Stash.class),
                        hasProperty("repoUrl", equalTo("http://url.stash"))
                ),
                // viewgit
                allOf(
                        instanceOf(ViewGitWeb.class),
                        hasProperty("repoUrl", equalTo("http://url.viewgit")),
                        hasProperty("projectName", equalTo("my_other_project"))
                )
        ));
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class hudson.plugins.git.browser.GitBlitRepositoryBrowser.repoUrl = http://url.gitlib";
    }

    @Override
    protected String configResource() {
        return "browsers-casc.yaml";
    }
}
