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
public class CGitTest {

    private final String repoUrl = "http://cgit.example.com/";

    private final boolean useAuthorName;
    private final GitChangeSetSample sample;

    public CGitTest(String useAuthorName) {
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
        URL result = (new CGit(repoUrl)).getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrl + "commit/?id=" + sample.id), result);
    }

    @Test
    public void testGetDiffLink() throws Exception {
        CGit cgit = new CGit(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL diffLink = cgit.getDiffLink(path);
            EditType editType = path.getEditType();
            URL expectedDiffLink = null;
            if (editType == EditType.ADD || editType == EditType.EDIT) {
                expectedDiffLink = new URL(repoUrl + "diff/" + path.getPath() + "?id=" + sample.id);
            } else if (editType == EditType.DELETE) {
                // Surprising that the DELETE EditType uses sample.id and not sample.parent ???
                expectedDiffLink = new URL(repoUrl + "diff/" + path.getPath() + "?id=" + sample.id);
            } else {
                fail("Unexpected edit type " + editType.getName());
            }
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(msg, expectedDiffLink, diffLink);
        }
    }

    @Test
    public void testGetFileLink() throws Exception {
        CGit cgit = new CGit(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = cgit.getFileLink(path);
            EditType editType = path.getEditType();
            URL expectedFileLink = null;
            if (editType == EditType.ADD || editType == EditType.EDIT) {
                expectedFileLink = new URL(repoUrl + "tree/" + path.getPath() + "?id=" + sample.id);
            } else if (editType == EditType.DELETE) {
                expectedFileLink = new URL(repoUrl + "tree/" + path.getPath() + "?id=" + sample.parent);
            } else {
                fail("Unexpected edit type " + editType.getName());
            }
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(msg, expectedFileLink, fileLink);
        }
    }

}
