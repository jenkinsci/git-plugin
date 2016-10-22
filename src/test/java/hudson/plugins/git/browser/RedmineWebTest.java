package hudson.plugins.git.browser;

import hudson.model.Run;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

import org.xml.sax.SAXException;

/**
 * @author mfriedenhagen
 */
public class RedmineWebTest {

    private static final String REDMINE_URL = "https://SERVER/PATH/projects/PROJECT/repository";
    private final RedmineWeb redmineWeb = new RedmineWeb(REDMINE_URL);

    /**
     * Test method for {@link hudson.plugins.git.browser.RedmineWeb#getUrl()}.
     * @throws MalformedURLException
     */
    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(redmineWeb.getUrl()), REDMINE_URL  + "/");
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.RedmineWeb#getUrl()}.
     * @throws MalformedURLException
     */
    @Test
    public void testGetUrlForRepoWithTrailingSlash() throws IOException {
        assertEquals(String.valueOf(new RedmineWeb(REDMINE_URL + "/").getUrl()), REDMINE_URL  + "/");
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.RedmineWeb#getChangeSetLink(hudson.plugins.git.GitChangeSet)}.
     * @throws SAXException on XML parsing exception
     * @throws IOException on input or output error
     */
    @Test
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final URL changeSetLink = redmineWeb.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(REDMINE_URL + "/diff?rev=396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.RedmineWeb#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException on XML parsing exception
     * @throws IOException on input or output error
     */
    @Test
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path modified1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(REDMINE_URL + "/revisions/396fc230a3db05c427737aa5c2eb7856ba72b05d/diff/src/main/java/hudson/plugins/git/browser/GithubWeb.java", redmineWeb.getDiffLink(modified1).toString());
        final Path modified2 = pathMap.get("src/test/java/hudson/plugins/git/browser/GithubWebTest.java");
        assertEquals(REDMINE_URL + "/revisions/396fc230a3db05c427737aa5c2eb7856ba72b05d/diff/src/test/java/hudson/plugins/git/browser/GithubWebTest.java", redmineWeb.getDiffLink(modified2).toString());
        // For added files returns a link to the entry.
        final Path added = pathMap.get("src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file");
        assertEquals(REDMINE_URL + "/revisions/396fc230a3db05c427737aa5c2eb7856ba72b05d/entry/src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file", redmineWeb.getDiffLink(added).toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GithubWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException on XML parsing exception
     * @throws IOException on input or output error
     */
    @Test
    public void testGetFileLinkPath() throws IOException, SAXException {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = redmineWeb.getFileLink(path);
        assertEquals(REDMINE_URL  + "/revisions/396fc230a3db05c427737aa5c2eb7856ba72b05d/entry/src/main/java/hudson/plugins/git/browser/GithubWeb.java", String.valueOf(fileLink));
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GithubWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException on XML parsing exception
     * @throws IOException on input or output error
     */
    @Test
    public void testGetFileLinkPathForDeletedFile() throws IOException, SAXException {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = redmineWeb.getFileLink(path);
        assertEquals(REDMINE_URL + "/revisions/fc029da233f161c65eb06d0f1ed4f36ae81d1f4f/diff/bar", String.valueOf(fileLink));
    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final GitChangeLogParser logParser = new GitChangeLogParser(false);
        final List<GitChangeSet> changeSetList = logParser.parse(RedmineWebTest.class.getResourceAsStream(rawchangelogpath));
        return changeSetList.get(0);
    }

    /**
     * @param changelog
     * @return
     * @throws IOException on input or output error
     * @throws SAXException on XML parsing exception
     */
    private HashMap<String, Path> createPathMap(final String changelog) throws IOException, SAXException {
        final HashMap<String, Path> pathMap = new HashMap<>();
        final Collection<Path> changeSet = createChangeSet(changelog).getPaths();
        for (final Path path : changeSet) {
            pathMap.put(path.getPath(), path);
        }
        return pathMap;
    }


}
