package hudson.plugins.git.browser;

import hudson.model.*;
import hudson.plugins.git.*;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TFS2013GitRepositoryBrowserTest {

    private static final String repoUrl = "http://tfs/tfs/project/_git/repo";
    private static final GitChangeSetSample sample = new GitChangeSetSample(false);
    
    @BeforeClass
    public static void testSetUp() {

        GitSCM scm = new GitSCM(
            Collections.singletonList(new UserRemoteConfig(repoUrl, null, null, null)),
            new ArrayList<>(),
            null, JGitTool.MAGIC_EXENAME,
            Collections.<GitSCMExtension>emptyList());

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);

        when(project.getScm()).thenReturn(scm);
        when(build.getProject()).thenReturn(project);
        when(build.getParent()).thenReturn(project);

        sample.changeSet.setParent(ChangeLogSet.createEmpty((Run) build));
    }

    @Test
    public void testResolveURLFromSCM() throws Exception {
        TFS2013GitRepositoryBrowser browser = new TFS2013GitRepositoryBrowser("");
        assertThat(browser.getRepoUrl(sample.changeSet).toString(), is("http://tfs/tfs/project/_git/repo/"));
    }

    @Test
    public void testResolveURLFromConfig() throws Exception {
        TFS2013GitRepositoryBrowser browser = new TFS2013GitRepositoryBrowser("http://url/repo");
        assertThat(browser.getRepoUrl(sample.changeSet).toString(), is("http://url/repo/"));
    }

    @Test
    public void testResolveURLFromConfigWithTrailingSlash() throws Exception {
        TFS2013GitRepositoryBrowser browser = new TFS2013GitRepositoryBrowser("http://url/repo/");
        assertThat(browser.getRepoUrl(sample.changeSet).toString(), is("http://url/repo/"));
    }

    @Test
    public void testGetChangeSetLink() throws Exception {
        URL result = new TFS2013GitRepositoryBrowser(repoUrl).getChangeSetLink(sample.changeSet);
        assertThat(result.toString(), is("http://tfs/tfs/project/_git/repo/commit/" + sample.id));
    }

    @Test
    public void testGetDiffLink() throws Exception {
        TFS2013GitRepositoryBrowser browser = new TFS2013GitRepositoryBrowser(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL diffLink = browser.getDiffLink(path);
            EditType editType = path.getEditType();
            URL expectedDiffLink = new URL("http://tfs/tfs/project/_git/repo/commit/" + sample.id + "#path=" + path.getPath() + "&_a=compare");
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(msg, expectedDiffLink, diffLink);
        }
    }

    @Test
    public void testGetFileLink() throws Exception {
        TFS2013GitRepositoryBrowser browser = new TFS2013GitRepositoryBrowser(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = browser.getFileLink(path);
            EditType editType = path.getEditType();
            URL expectedFileLink = new URL("http://tfs/tfs/project/_git/repo/commit/" + sample.id + "#path=" + path.getPath() + "&_a=history");
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(msg, expectedFileLink, fileLink);
        }
    }
}
