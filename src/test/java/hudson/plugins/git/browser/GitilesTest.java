package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

@ParameterizedClass(name = "{0}")
@MethodSource("permuteAuthorName")
class GitilesTest {

    private final String repoUrl = "https://gwt.googlesource.com/gwt/";

    private final boolean useAuthorName;
    private final GitChangeSetSample sample;

    public GitilesTest(boolean useAuthorName) {
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
        URL result = (new Gitiles(repoUrl)).getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrl + "+/" + sample.id + "%5E%21"), result);
    }

    @Test
    void testGetDiffLink() throws Exception {
        Gitiles gitiles = new Gitiles(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL diffLink = gitiles.getDiffLink(path);
            URL expectedDiffLink = new URL(repoUrl + "+/" + sample.id + "%5E%21");
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + path.getEditType().getName();
            assertEquals(expectedDiffLink, diffLink, msg);
        }
    }

    @Test
    void testGetFileLink() throws Exception {
        Gitiles gitiles = new Gitiles(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = gitiles.getFileLink(path);
            URL expectedFileLink = new URL(repoUrl + "+blame/" + sample.id + "/" + path.getPath());
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + path.getEditType().getName();
            assertEquals(expectedFileLink, fileLink, msg);
        }
    }
}
