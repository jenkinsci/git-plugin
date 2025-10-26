package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import hudson.scm.EditType;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

@ParameterizedClass(name = "{0}-{1}")
@MethodSource("permuteAuthorNameAndRepoUrl")
class FisheyeGitRepositoryBrowserTest {

    private static final String projectName = "fisheyeProjectName";

    private final String repoUrl;
    private final String repoUrlNoTrailingSlash;
    private final boolean useAuthorName;
    private final GitChangeSetSample sample;

    public FisheyeGitRepositoryBrowserTest(boolean useAuthorName, String repoUrl) {
        this.useAuthorName = useAuthorName;
        this.repoUrl = repoUrl;
        this.repoUrlNoTrailingSlash = this.repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        sample = new GitChangeSetSample(this.useAuthorName);
    }

    static Collection permuteAuthorNameAndRepoUrl() {
        List<Object[]> values = new ArrayList<>();
        String fisheyeUrl = "http://fisheye.example.com/site/browse/" + projectName;
        String[] allowedUrls = {fisheyeUrl, fisheyeUrl + "/"};
        boolean[] allowed = {true, false};
        for (boolean authorName : allowed) {
            for (String repoUrl : allowedUrls) {
                Object[] combination = {authorName, repoUrl};
                values.add(combination);
            }
        }
        return values;
    }

    @Test
    void testGetChangeSetLink() throws Exception {
        URL result = (new FisheyeGitRepositoryBrowser(repoUrl)).getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrlNoTrailingSlash.replace("browse", "changelog") + "?cs=" + sample.id), result);
    }

    @Test
    void testGetDiffLink() throws Exception {
        FisheyeGitRepositoryBrowser fisheye = new FisheyeGitRepositoryBrowser(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL diffLink = fisheye.getDiffLink(path);
            EditType editType = path.getEditType();
            String slash = repoUrl.endsWith("/") ? "" : "/";
            URL expectedDiffLink = new URL(repoUrl + slash + path.getPath() + "?r1=" + sample.parent + "&r2=" + sample.id);
            if (editType == EditType.DELETE || editType == EditType.ADD) {
                expectedDiffLink = null;
            }
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(expectedDiffLink, diffLink, msg);
        }
    }

    @Test
    void testGetFileLink() throws Exception {
        FisheyeGitRepositoryBrowser fisheye = new FisheyeGitRepositoryBrowser(repoUrl);
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = fisheye.getFileLink(path);
            EditType editType = path.getEditType();
            URL expectedFileLink = null;
            String slash = repoUrl.endsWith("/") ? "" : "/";
            if (editType == EditType.ADD || editType == EditType.EDIT) {
                expectedFileLink = new URL(repoUrl + slash + path.getPath());
            } else if (editType == EditType.DELETE) {
                expectedFileLink = new URL(repoUrl + slash + path.getPath());
            } else {
                fail("Unexpected edit type " + editType.getName());
            }
            String msg = "Wrong link for path: " + path.getPath() + ", edit type: " + editType.getName();
            assertEquals(expectedFileLink, fileLink, msg);
        }
    }
}
