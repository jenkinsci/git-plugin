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

import junit.framework.TestCase;

import org.xml.sax.SAXException;

/**
 * @author Paul Nyheim (paul.nyheim@gmail.com)
 */
public class ViewGitWebTest extends TestCase {

    private static final String VIEWGIT_URL = "http://SERVER/viewgit";
    private static final String PROJECT_NAME = "PROJECT";
    private final ViewGitWeb viewGitWeb = new ViewGitWeb(VIEWGIT_URL, PROJECT_NAME);

    /**
     * Test method for {@link hudson.plugins.git.browser.ViewGitWeb#getUrl()}.
     * 
     * @throws MalformedURLException
     */
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(viewGitWeb.getUrl()), VIEWGIT_URL + "/");
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.ViewGitWeb#getUrl()}.
     * 
     * @throws MalformedURLException
     */
    public void testGetUrlForRepoWithTrailingSlash() throws IOException {
        assertEquals(String.valueOf(new ViewGitWeb(VIEWGIT_URL + "/", PROJECT_NAME).getUrl()), VIEWGIT_URL + "/");
    }

    /**
     * Test method for
     * {@link hudson.plugins.git.browser.ViewGitWeb#getChangeSetLink(hudson.plugins.git.GitChangeSet)}
     * .
     * 
     * @throws SAXException
     * @throws IOException
     */
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final URL changeSetLink = viewGitWeb.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals("http://SERVER/viewgit/?p=PROJECT&a=commit&h=396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    /**
     * Test method for
     * {@link hudson.plugins.git.browser.ViewGitWeb#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}
     * .
     * 
     * @throws SAXException
     * @throws IOException
     */
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path path1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(VIEWGIT_URL + "/?p=PROJECT&a=commitdiff&h=396fc230a3db05c427737aa5c2eb7856ba72b05d#src%2Fmain%2Fjava%2Fhudson%2Fplugins%2Fgit%2Fbrowser%2FGithubWeb.java", viewGitWeb.getDiffLink(path1).toString());
        final Path path2 = pathMap.get("src/test/java/hudson/plugins/git/browser/GithubWebTest.java");
        assertEquals(VIEWGIT_URL + "/?p=PROJECT&a=commitdiff&h=396fc230a3db05c427737aa5c2eb7856ba72b05d#src%2Ftest%2Fjava%2Fhudson%2Fplugins%2Fgit%2Fbrowser%2FGithubWebTest.java", viewGitWeb.getDiffLink(path2).toString());
        final Path path3 = pathMap.get("src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file");
        assertNull("Do not return a diff link for added files.", viewGitWeb.getDiffLink(path3));
    }

    /**
     * Test method for
     * {@link hudson.plugins.git.browser.ViewGitWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}
     * .
     * 
     * @throws SAXException
     * @throws IOException
     */
    public void testGetFileLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = viewGitWeb.getFileLink(path);
        assertEquals(VIEWGIT_URL + "/?p=PROJECT&a=viewblob&h=2e0756cd853dccac638486d6aab0e74bc2ef4041&f=src/main/java/hudson/plugins/git/browser/GithubWeb.java",
                String.valueOf(fileLink));
    }
    
    public void testGetDiffLinkForDeletedFile() throws Exception{
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        assertNull("Do not return a diff link for deleted files.", viewGitWeb.getDiffLink(path));

    }

    /**
     * Test method for
     * {@link hudson.plugins.git.browser.ViewGitWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}
     * .
     * 
     * @throws SAXException
     * @throws IOException
     */
    public void testGetFileLinkPathForDeletedFile() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = viewGitWeb.getFileLink(path);
        assertEquals(VIEWGIT_URL + "/?p=PROJECT&a=commitdiff&h=fc029da233f161c65eb06d0f1ed4f36ae81d1f4f#bar", String.valueOf(fileLink));
    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final File rawchangelog = new File(ViewGitWebTest.class.getResource(rawchangelogpath).getFile());
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
