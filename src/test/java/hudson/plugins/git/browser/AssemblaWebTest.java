package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import hudson.scm.EditType;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssemblaWebTest {

    private final String repoUrl = "http://assembla.example.com/";

    private final boolean useAuthorName;
    private final GitChangeSetSample sample;

    public AssemblaWebTest(String useAuthorName) {
        this.useAuthorName = Boolean.valueOf(useAuthorName);
        sample = new GitChangeSetSample(this.useAuthorName);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteAuthorName() {
        List<Object[]> values = new ArrayList<Object[]>();
        String[] allowed = {"true", "false"};
        for (String authorName : allowed) {
            Object[] combination = {authorName};
            values.add(combination);
        }
        return values;
    }

    @Test
    public void testGetChangeSetLink() throws Exception {
        URL result = (new AssemblaWeb(repoUrl)).getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrl + "commits/" + sample.id), result);
    }

    @Test
    public void testGetDiffLink() throws Exception {
        AssemblaWeb assemblaWeb = new AssemblaWeb(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL diffLink = assemblaWeb.getDiffLink(path);
            EditType editType = path.getEditType();
            URL expectedDiffLink = new URL(repoUrl + "commits/" + sample.id);
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(msg, expectedDiffLink, diffLink);
        }
    }

    @Test
    public void testGetFileLink() throws Exception {
        AssemblaWeb assemblaWeb = new AssemblaWeb(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = assemblaWeb.getFileLink(path);
            EditType editType = path.getEditType();
            URL expectedFileLink = null;
            if (editType == EditType.ADD || editType == EditType.EDIT) {
                expectedFileLink = new URL(repoUrl + "nodes/" + sample.id + path.getPath());
            } else if (editType == EditType.DELETE) {
                expectedFileLink = new URL(repoUrl + "nodes/" + sample.parent + path.getPath());
            } else {
                fail("Unexpected edit type " + editType.getName());
            }
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(msg, expectedFileLink, fileLink);
        }
    }

}
