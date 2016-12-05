package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitilesTest {

    private final String repoUrl = "https://gwt.googlesource.com/gwt/";

    private final boolean useAuthorName;
    private final GitChangeSetSample sample;

    public GitilesTest(String useAuthorName) {
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
        URL result = (new Gitiles(repoUrl)).getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrl + "+/" + sample.id + "%5E%21"), result);
    }

    @Test
    public void testGetDiffLink() throws Exception {
        Gitiles gitiles = new Gitiles(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL diffLink = gitiles.getDiffLink(path);
            URL expectedDiffLink = new URL(repoUrl + "+/" + sample.id + "%5E%21");
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + path.getEditType().getName();
            assertEquals(msg, expectedDiffLink, diffLink);
        }
    }

    @Test
    public void testGetFileLink() throws Exception {
        Gitiles gitiles = new Gitiles(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = gitiles.getFileLink(path);
            URL expectedFileLink = new URL(repoUrl + "+blame/" + sample.id + "/" + path.getPath());
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + path.getEditType().getName();
            assertEquals(msg, expectedFileLink, fileLink);
        }
    }
}
