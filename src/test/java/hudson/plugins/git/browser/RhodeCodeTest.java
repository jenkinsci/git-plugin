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


public class RhodeCodeTest {

    private static final String RHODECODE_URL = "https://SERVER/r/PROJECT";
    private final RhodeCode rhodecode = new RhodeCode(RHODECODE_URL);

    /**
     * Test method for {@link hudson.plugins.git.browser.RhodeCode#getUrl()}.
     * @throws MalformedURLException
     */
    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(rhodecode.getUrl()), RHODECODE_URL  + "/");
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.RhodeCode#getUrl()}.
     * @throws MalformedURLException
     */
    @Test
    public void testGetUrlForRepoWithTrailingSlash() throws IOException {
        assertEquals(String.valueOf(new RhodeCode(RHODECODE_URL + "/").getUrl()), RHODECODE_URL  + "/");
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.RhodeCode#getChangeSetLink(hudson.plugins.git.GitChangeSet)}.
     * @throws SAXException
     * @throws IOException
     */
    @Test
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final URL changeSetLink = rhodecode.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(RHODECODE_URL + "/changeset/396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.RhodeCode#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException
     * @throws IOException
     */
    @Test
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path modified1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(RHODECODE_URL + "/diff/src/main/java/hudson/plugins/git/browser/GithubWeb.java?diff2=396fc230a3db05c427737aa5c2eb7856ba72b05d&diff1=396fc230a3db05c427737aa5c2eb7856ba72b05d&diff=diff+to+revision", rhodecode.getDiffLink(modified1).toString());
        // For added files returns a link to the commit.
        final Path added = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(RHODECODE_URL + "/diff/src/main/java/hudson/plugins/git/browser/GithubWeb.java?diff2=396fc230a3db05c427737aa5c2eb7856ba72b05d&diff1=396fc230a3db05c427737aa5c2eb7856ba72b05d&diff=diff+to+revision", rhodecode.getDiffLink(added).toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GithubWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException
     * @throws IOException
     */
    @Test
    public void testGetFileLinkPath() throws IOException, SAXException {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = rhodecode.getFileLink(path);
        assertEquals(RHODECODE_URL  + "/files/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/main/java/hudson/plugins/git/browser/GithubWeb.java", String.valueOf(fileLink));
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GithubWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException
     * @throws IOException
     */
    @Test
    public void testGetFileLinkPathForDeletedFile() throws IOException, SAXException {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = rhodecode.getFileLink(path);
        assertEquals(RHODECODE_URL + "/files/b547aa10c3f06710c6fdfcdb2a9149c81662923b/bar", String.valueOf(fileLink));
    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final File rawchangelog = new File(RhodeCodeTest.class.getResource(rawchangelogpath).getFile());
        final GitChangeLogParser logParser = new GitChangeLogParser(false);
        final List<GitChangeSet> changeSetList = logParser.parse((Run) null, null, rawchangelog).getLogs();
        return changeSetList.get(0);
    }

    /**
     * @param changelog
     * @return
     * @throws IOException
     * @throws SAXException
     */
    private HashMap<String, Path> createPathMap(final String changelog) throws IOException, SAXException {
        final HashMap<String, Path> pathMap = new HashMap<String, Path>();
        final Collection<Path> changeSet = createChangeSet(changelog).getPaths();
        for (final Path path : changeSet) {
            pathMap.put(path.getPath(), path);
        }
        return pathMap;
    }


}
