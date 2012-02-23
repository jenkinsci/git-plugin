/**
 * Copyright 2010 Mirko Friedenhagen
 */

package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import junit.framework.TestCase;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * @author mattsemar
 *
 */
public class BitbucketWebTest extends TestCase {

    /**
     *
     */
    private static final String BITBUCKET_URL = "http://bitbucket.org/USER/REPO";
    private final BitbucketWeb bitbucketWeb;

    {
        try {
            bitbucketWeb = new BitbucketWeb(BITBUCKET_URL);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test method for {@link BitbucketWeb#getUrl()}.
     * @throws java.net.MalformedURLException
     */
    public void testGetUrl() throws MalformedURLException {
        assertEquals(String.valueOf(bitbucketWeb.getUrl()), BITBUCKET_URL + "/");
    }

    /**
     * Test method for {@link BitbucketWeb#getUrl()}.
     * @throws java.net.MalformedURLException
     */
    public void testGetUrlForRepoWithTrailingSlash() throws MalformedURLException {
        assertEquals(String.valueOf(new BitbucketWeb(BITBUCKET_URL + "/").getUrl()), BITBUCKET_URL + "/");
    }

    /**
     * Test method for {@link BitbucketWeb#getChangeSetLink(hudson.plugins.git.GitChangeSet)}.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final URL changeSetLink = bitbucketWeb.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(BITBUCKET_URL + "/changeset/396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    /**
     * Test method for {@link BitbucketWeb#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final String path1Str = "src/main/java/hudson/plugins/git/browser/GithubWeb.java";
        final Path path1 = pathMap.get(path1Str);

        assertEquals(BITBUCKET_URL + "/changeset/396fc230a3db05c427737aa5c2eb7856ba72b05d#chg-" + path1Str, bitbucketWeb.getDiffLink(path1).toString());

        final String path2Str = "src/test/java/hudson/plugins/git/browser/GithubWebTest.java";
        final Path path2 = pathMap.get(path2Str);
        assertEquals(BITBUCKET_URL + "/changeset/396fc230a3db05c427737aa5c2eb7856ba72b05d#chg-" + path2Str, bitbucketWeb.getDiffLink(path2).toString());
        final String path3Str = "src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file";
        final Path path3 = pathMap.get(path3Str);
        assertNull("Do not return a diff link for added files.", bitbucketWeb.getDiffLink(path3));
    }

    /**
     * Test method for {@link GithubWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public void testGetFileLinkPath() throws IOException, SAXException {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = bitbucketWeb.getFileLink(path);
        assertEquals(BITBUCKET_URL + "/history/src/main/java/hudson/plugins/git/browser/GithubWeb.java", String.valueOf(fileLink));
    }

    /**
     * Test method for {@link BitbucketWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    public void testGetFileLinkPathForDeletedFile() throws IOException, SAXException {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = bitbucketWeb.getFileLink(path);
        assertEquals(BITBUCKET_URL + "/history/bar", String.valueOf(fileLink));
    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final File rawchangelog = new File(BitbucketWebTest.class.getResource(rawchangelogpath).getFile());
        final GitChangeLogParser logParser = new GitChangeLogParser(false);
        final List<GitChangeSet> changeSetList = logParser.parse(null, rawchangelog).getLogs();
        return changeSetList.get(0);
    }

    /**
     * @param changelog
     * @return
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
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
