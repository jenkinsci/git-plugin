package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PhabricatorTest {

    private final String repoName = "phabricatorRepo";
    private final String repoUrl = "http://phabricator.example.com/";
    private final Phabricator phabricator = new Phabricator(repoUrl, repoName);

    private final GitChangeSetSample sample = new GitChangeSetSample(true);

    @Test
    void testGetRepo() throws IOException {
        assertEquals(repoName, phabricator.getRepo());
    }

    @Test
    void testGetChangeSetLink() throws Exception {
        URL result = phabricator.getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrl + "r" + repoName + sample.id), result);
    }

    @Test
    void testGetDiffLink() throws Exception {
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL diffLink = phabricator.getDiffLink(path);
            URL expectedDiffLink = new URL(repoUrl + "diffusion/" + repoName + "/change/master/" + path.getPath() + ";" + sample.id);
            String msg = "Wrong link for path: " + path.getPath();
            assertEquals(expectedDiffLink, diffLink, msg);
        }
    }

    @Test
    void testGetFileLink() throws Exception {
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = phabricator.getDiffLink(path);
            URL expectedFileLink = new URL(repoUrl + "diffusion/" + repoName + "/change/master/" + path.getPath() + ";" + sample.id);
            String msg = "Wrong link for path: " + path.getPath();
            assertEquals(expectedFileLink, fileLink, msg);
        }
    }

}
