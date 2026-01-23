package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import hudson.scm.EditType;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

@ParameterizedClass(name = "{0}")
@MethodSource("permuteAuthorName")
class GitBlitRepositoryBrowserTest {

    private final String repoUrl = "http://gitblit.example.com/";

    private final boolean useAuthorName;
    private final GitChangeSetSample sample;
    private final String projectName = "gitBlitProjectName";

    public GitBlitRepositoryBrowserTest(boolean useAuthorName) {
        this.useAuthorName = useAuthorName;
        sample = new GitChangeSetSample(this.useAuthorName);
    }

    static Collection permuteAuthorName() {
        List<Object[]> values = new ArrayList<>();
        boolean[] allowed = {true, false};
        for (boolean authorName : allowed) {
            Object[] combination = {authorName};
            values.add(combination);
        }
        return values;
    }

    @Test
    void testGetChangeSetLink() throws Exception {
        URL result = (new GitBlitRepositoryBrowser(repoUrl, projectName)).getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrl + "commit?r=" + projectName + "&h=" + sample.id), result);
    }

    @Test
    void testGetDiffLink() throws Exception {
        GitBlitRepositoryBrowser gitblit = new GitBlitRepositoryBrowser(repoUrl, projectName);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            EditType editType = path.getEditType();
            assertTrue(editType == EditType.ADD || editType == EditType.EDIT || editType == EditType.DELETE, "Unexpected edit type " + editType.getName());
            URL diffLink = gitblit.getDiffLink(path);
            URL expectedDiffLink = new URL(repoUrl + "blobdiff?r=" + projectName + "&h=" + sample.id + "&hb=" + sample.parent);
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(expectedDiffLink, diffLink, msg);
        }
    }

    @Test
    void testGetFileLink() throws Exception {
        GitBlitRepositoryBrowser gitblit = new GitBlitRepositoryBrowser(repoUrl, projectName);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = gitblit.getFileLink(path);
            EditType editType = path.getEditType();
            URL expectedFileLink = null;
            if (editType == EditType.ADD || editType == EditType.EDIT) {
                expectedFileLink = new URL(repoUrl + "blob?r=" + projectName + "&h=" + sample.id + "&f=" + URLEncoder.encode(path.getPath(), StandardCharsets.UTF_8).replaceAll("\\+", "%20"));
            } else if (editType == EditType.DELETE) {
                expectedFileLink = null;
            } else {
                fail("Unexpected edit type " + editType.getName());
            }
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(expectedFileLink, fileLink, msg);
        }
    }

    @Test
    void testGetProjectName() {
        GitBlitRepositoryBrowser gitblit = new GitBlitRepositoryBrowser(repoUrl, projectName);
        assertEquals(projectName, gitblit.getProjectName());
    }
}
