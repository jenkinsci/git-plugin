package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import hudson.scm.EditType;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitBlitRepositoryBrowserTest {

    private final String repoUrl = "http://gitblit.example.com/";

    private final boolean useAuthorName;
    private final GitChangeSetSample sample;
    private final String projectName = "gitBlitProjectName";

    public GitBlitRepositoryBrowserTest(String useAuthorName) {
        this.useAuthorName = Boolean.valueOf(useAuthorName);
        sample = new GitChangeSetSample(this.useAuthorName);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteAuthorName() {
        List<Object[]> values = new ArrayList<>();
        String[] allowed = {"true", "false"};
        for (String authorName : allowed) {
            Object[] combination = {authorName};
            values.add(combination);
        }
        return values;
    }

    @Test
    public void testGetChangeSetLink() throws Exception {
        URL result = (new GitBlitRepositoryBrowser(repoUrl, projectName)).getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrl + "commit?r=" + projectName + "&h=" + sample.id), result);
    }

    @Test
    public void testGetDiffLink() throws Exception {
        GitBlitRepositoryBrowser gitblit = new GitBlitRepositoryBrowser(repoUrl, projectName);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            EditType editType = path.getEditType();
            assertTrue("Unexpected edit type " + editType.getName(), editType == EditType.ADD || editType == EditType.EDIT || editType == EditType.DELETE);
            URL diffLink = gitblit.getDiffLink(path);
            URL expectedDiffLink = new URL(repoUrl + "blobdiff?r=" + projectName + "&h=" + sample.id + "&hb=" + sample.parent);
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(msg, expectedDiffLink, diffLink);
        }
    }

    @Test
    public void testGetFileLink() throws Exception {
        GitBlitRepositoryBrowser gitblit = new GitBlitRepositoryBrowser(repoUrl, projectName);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = gitblit.getFileLink(path);
            EditType editType = path.getEditType();
            URL expectedFileLink = null;
            if (editType == EditType.ADD || editType == EditType.EDIT) {
                expectedFileLink = new URL(repoUrl + "blob?r=" + projectName + "&h=" + sample.id + "&f=" + URLEncoder.encode(path.getPath(), "UTF-8").replaceAll("\\+", "%20"));
            } else if (editType == EditType.DELETE) {
                expectedFileLink = null;
            } else {
                fail("Unexpected edit type " + editType.getName());
            }
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(msg, expectedFileLink, fileLink);
        }
    }

    @Test
    public void testGetProjectName() {
        GitBlitRepositoryBrowser gitblit = new GitBlitRepositoryBrowser(repoUrl, projectName);
        assertEquals(projectName, gitblit.getProjectName());
    }
}
