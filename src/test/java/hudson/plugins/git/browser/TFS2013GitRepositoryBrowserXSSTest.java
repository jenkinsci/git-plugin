package hudson.plugins.git.browser;

import org.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;

@WithJenkins
class TFS2013GitRepositoryBrowserXSSTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    @Issue("SECURITY-1723")
    void testXSS() throws Exception {
        // setup scm
        GitSCM scm = new GitSCM(
                Collections.singletonList(new UserRemoteConfig("http://tfs/tfs/project/_git/repo", null, null, null)),
                new ArrayList<>(),
                null, JGitTool.MAGIC_EXENAME,
                Collections.emptyList());
        scm.setBrowser(new TFS2013GitRepositoryBrowser("<img src=x onerror=alert(232)>"));

        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(scm);

        AtomicBoolean xss = new AtomicBoolean(false);
        JenkinsRule.WebClient wc = r.createWebClient();
        wc.setAlertHandler((page, s) -> xss.set(true));
        HtmlPage page = wc.getPage(p, "configure");
        assertFalse(xss.get());
    }
}
