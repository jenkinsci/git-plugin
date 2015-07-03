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


public class GitWebTest {

    private static final String GITWEB_URL = "https://SERVER/gitweb?repo.git";
    private final GitWeb gitwebWeb = new GitWeb(GITWEB_URL);


    /**
     * Test method for {@link hudson.plugins.git.browser.GitWeb#getUrl()}.
     * @throws MalformedURLException
     */
    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(gitwebWeb.getUrl()), GITWEB_URL);
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitWeb#getChangeSetLink(hudson.plugins.git.GitChangeSet)}.
     * @throws SAXException
     * @throws IOException
     */
    @Test
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final URL changeSetLink = gitwebWeb.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(GITWEB_URL + "&a=commit&h=396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GitWeb#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException
     * @throws IOException
     */
    @Test
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path modified1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(GITWEB_URL + "&a=blobdiff&f=src/main/java/hudson/plugins/git/browser/GithubWeb.java&fp=src/main/java/hudson/plugins/git/browser/GithubWeb.java&h=3f28ad75f5ecd5e0ea9659362e2eef18951bd451&hp=2e0756cd853dccac638486d6aab0e74bc2ef4041&hb=396fc230a3db05c427737aa5c2eb7856ba72b05d&hpb=f28f125f4cc3e5f6a32daee6a26f36f7b788b8ff", gitwebWeb.getDiffLink(modified1).toString());
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
        final URL fileLink = gitwebWeb.getFileLink(path);
        assertEquals(GITWEB_URL  + "&a=blob&f=src/main/java/hudson/plugins/git/browser/GithubWeb.java&h=2e0756cd853dccac638486d6aab0e74bc2ef4041&hb=396fc230a3db05c427737aa5c2eb7856ba72b05d", String.valueOf(fileLink));
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
        final URL fileLink = gitwebWeb.getFileLink(path);
        assertEquals(GITWEB_URL + "&a=blob&f=bar&h=257cc5642cb1a054f08cc83f2d943e56fd3ebe99&hb=fc029da233f161c65eb06d0f1ed4f36ae81d1f4f", String.valueOf(fileLink));
    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final File rawchangelog = new File(GitWebTest.class.getResource(rawchangelogpath).getFile());
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
