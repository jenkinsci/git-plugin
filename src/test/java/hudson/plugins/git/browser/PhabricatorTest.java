package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import java.io.IOException;
import java.net.URL;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhabricatorTest {

    private final String repoName = "phabricatorRepo";
    private final String repoUrl = "http://phabricator.example.com/";
    private final Phabricator phabricator;

    private final GitChangeSetSample sample;

    public PhabricatorTest() {
        phabricator = new Phabricator(repoUrl, repoName);
        sample = new GitChangeSetSample(true);
    }

    @Test
    public void testGetRepo() throws IOException {
        assertEquals(repoName, phabricator.getRepo());
    }

    @Test
    public void testGetChangeSetLink() throws Exception {
        URL result = phabricator.getChangeSetLink(sample.changeSet);
        assertEquals(new URL(repoUrl + "r" + repoName + sample.id), result);
    }

    @Test
    public void testGetDiffLink() throws Exception {
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL diffLink = phabricator.getDiffLink(path);
            URL expectedDiffLink = new URL(repoUrl + "diffusion/" + repoName + "/change/master/" + path.getPath() + ";" + sample.id);
            String msg = "Wrong link for path: " + path.getPath();
            assertEquals(msg, expectedDiffLink, diffLink);
        }
    }

    @Test
    public void testGetFileLink() throws Exception {
        for (GitChangeSet.Path path : sample.changeSet.getPaths()) {
            URL fileLink = phabricator.getDiffLink(path);
            URL expectedFileLink = new URL(repoUrl + "diffusion/" + repoName + "/change/master/" + path.getPath() + ";" + sample.id);
            String msg = "Wrong link for path: " + path.getPath();
            assertEquals(msg, expectedFileLink, fileLink);
        }
    }

}
