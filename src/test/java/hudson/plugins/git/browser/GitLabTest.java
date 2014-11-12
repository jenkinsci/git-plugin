package hudson.plugins.git.browser;

import hudson.model.Run;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import junit.framework.TestCase;

import org.xml.sax.SAXException;

public class GitLabTest extends TestCase {

    private static final String GITLAB_URL = "https://SERVER/USER/REPO/";
    private final GitLab gitlab29 = new GitLab(GITLAB_URL, "2.9");
    private final GitLab gitlab42 = new GitLab(GITLAB_URL, "4.2");
    private final GitLab gitlab50 = new GitLab(GITLAB_URL, "5.0");
    private final GitLab gitlab51 = new GitLab(GITLAB_URL, "5.1");

    /**
     * Test method for {@link hudson.plugins.git.browser.GitLab#getVersion()}.
     */
    public void testGetVersion() {
        assertEquals(gitlab29.getVersion(), 2.9);
        assertEquals(gitlab42.getVersion(), 4.2);
        assertEquals(gitlab50.getVersion(), 5.0);
        assertEquals(gitlab51.getVersion(), 5.1);
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitLab#getChangeSetLink(hudson.plugins.git.GitChangeSet)}.
     * @throws IOException
     */
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        assertEquals(GITLAB_URL + "commits/396fc230a3db05c427737aa5c2eb7856ba72b05d",
                     gitlab29.getChangeSetLink(createChangeSet("rawchangelog")).toString());
        assertEquals(GITLAB_URL + "commit/396fc230a3db05c427737aa5c2eb7856ba72b05d",
                     gitlab42.getChangeSetLink(createChangeSet("rawchangelog")).toString());
        assertEquals(GITLAB_URL + "commit/396fc230a3db05c427737aa5c2eb7856ba72b05d",
                     gitlab50.getChangeSetLink(createChangeSet("rawchangelog")).toString());
        assertEquals(GITLAB_URL + "commit/396fc230a3db05c427737aa5c2eb7856ba72b05d",
                     gitlab51.getChangeSetLink(createChangeSet("rawchangelog")).toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitLab#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws IOException
     */
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path modified1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(GITLAB_URL + "commits/396fc230a3db05c427737aa5c2eb7856ba72b05d#src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                     gitlab29.getDiffLink(modified1).toString());
        assertEquals(GITLAB_URL + "commit/396fc230a3db05c427737aa5c2eb7856ba72b05d#src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                     gitlab42.getDiffLink(modified1).toString());
        assertEquals(GITLAB_URL + "commit/396fc230a3db05c427737aa5c2eb7856ba72b05d#src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                     gitlab50.getDiffLink(modified1).toString());
        assertEquals(GITLAB_URL + "commit/396fc230a3db05c427737aa5c2eb7856ba72b05d#src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                     gitlab51.getDiffLink(modified1).toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitLab#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws IOException
     */
    public void testGetFileLinkPath() throws IOException, SAXException {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(GITLAB_URL + "tree/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                     gitlab29.getFileLink(path).toString());
        assertEquals(GITLAB_URL + "tree/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                     gitlab42.getFileLink(path).toString());
        assertEquals(GITLAB_URL + "396fc230a3db05c427737aa5c2eb7856ba72b05d/tree/src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                     gitlab50.getFileLink(path).toString());
        assertEquals(GITLAB_URL + "blob/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                     gitlab51.getFileLink(path).toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitLab#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws IOException
     */
    public void testGetFileLinkPathForDeletedFile() throws IOException, SAXException {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        assertEquals(GITLAB_URL + "commits/fc029da233f161c65eb06d0f1ed4f36ae81d1f4f#bar",
                     gitlab29.getFileLink(path).toString());
        assertEquals(GITLAB_URL + "commit/fc029da233f161c65eb06d0f1ed4f36ae81d1f4f#bar",
                     gitlab42.getFileLink(path).toString());
        assertEquals(GITLAB_URL + "commit/fc029da233f161c65eb06d0f1ed4f36ae81d1f4f#bar",
                     gitlab50.getFileLink(path).toString());
        assertEquals(GITLAB_URL + "commit/fc029da233f161c65eb06d0f1ed4f36ae81d1f4f#bar",
                     gitlab51.getFileLink(path).toString());
    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final File rawchangelog = new File(GitLabTest.class.getResource(rawchangelogpath).getFile());
        final GitChangeLogParser logParser = new GitChangeLogParser(false);
        final List<GitChangeSet> changeSetList = logParser.parse((Run) null, null, rawchangelog).getLogs();
        return changeSetList.get(0);
    }

    private HashMap<String, Path> createPathMap(final String changelog) throws IOException, SAXException {
        final HashMap<String, Path> pathMap = new HashMap<String, Path>();
        final Collection<Path> changeSet = createChangeSet(changelog).getPaths();
        for (final Path path : changeSet) {
            pathMap.put(path.getPath(), path);
        }
        return pathMap;
    }

}
