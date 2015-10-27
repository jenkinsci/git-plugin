package hudson.plugins.git.browser;

import hudson.model.Run;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;
import org.junit.Test;

import org.xml.sax.SAXException;


public class GitoriousWebTest {

    private static final String GITORIOUS_URL = "https://SERVER/PROJECT";
    private final GitoriousWeb gitoriousWeb = new GitoriousWeb(GITORIOUS_URL);


    /**
     * Test method for {@link hudson.plugins.git.browser.GitoriousWeb#getUrl()}.
     * @throws MalformedURLException
     */
    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(gitoriousWeb.getUrl()), GITORIOUS_URL  + "/");
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitoriousWeb#getUrl()}.
     * @throws MalformedURLException
     */
    @Test
    public void testGetUrlForRepoWithTrailingSlash() throws IOException {
        assertEquals(String.valueOf(new GitoriousWeb(GITORIOUS_URL + "/").getUrl()), GITORIOUS_URL  + "/");
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitoriousWeb#getChangeSetLink(hudson.plugins.git.GitChangeSet)}.
     * @throws SAXException
     * @throws IOException
     */
    @Test
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final URL changeSetLink = gitoriousWeb.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(GITORIOUS_URL + "/commit/396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitoriousWeb#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException
     * @throws IOException
     */
    @Test
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path modified1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(GITORIOUS_URL + "/commit/396fc230a3db05c427737aa5c2eb7856ba72b05d/diffs?diffmode=sidebyside&fragment=1#src/main/java/hudson/plugins/git/browser/GithubWeb.java", gitoriousWeb.getDiffLink(modified1).toString());
        // For added files returns a link to the commit.
        final Path added = pathMap.get("src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file");
        assertEquals(GITORIOUS_URL + "/commit/396fc230a3db05c427737aa5c2eb7856ba72b05d/diffs?diffmode=sidebyside&fragment=1#src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file", gitoriousWeb.getDiffLink(added).toString());
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
        final URL fileLink = gitoriousWeb.getFileLink(path);
        assertEquals(GITORIOUS_URL  + "/blobs/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/main/java/hudson/plugins/git/browser/GithubWeb.java", String.valueOf(fileLink));
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
        final URL fileLink = gitoriousWeb.getFileLink(path);
        assertEquals(GITORIOUS_URL + "/commit/fc029da233f161c65eb06d0f1ed4f36ae81d1f4f/diffs?diffmode=sidebyside&fragment=1#bar", String.valueOf(fileLink));
    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final File rawchangelog = new File(URLDecoder.decode(BitbucketWebTest.class.getResource(rawchangelogpath), "utf-8").getFile());
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
