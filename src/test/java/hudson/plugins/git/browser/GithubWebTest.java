/**
 * Copyright 2010 Mirko Friedenhagen
 */

package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import junit.framework.TestCase;

import org.xml.sax.SAXException;

/**
 * @author mirko
 *
 */
public class GithubWebTest extends TestCase {

    private final GithubWeb githubWeb;

    {
        try {
            githubWeb = new GithubWeb("http://github.com/mfriedenhagen/Hudson-GIT-plugin");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GithubWeb#getUrl()}.
     * @throws MalformedURLException
     */
    public void testGetUrl() throws MalformedURLException {
        assertEquals(String.valueOf(githubWeb.getUrl()), "http://github.com/mfriedenhagen/Hudson-GIT-plugin/");
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GithubWeb#getChangeSetLink(hudson.plugins.git.GitChangeSet)}.
     * @throws SAXException
     * @throws IOException
     */
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final List<GitChangeSet> changeSetList = createChangeSetList("rawchangelog");
        final URL changeSetLink = githubWeb.getChangeSetLink(changeSetList.get(0));
        assertEquals("http://github.com/mfriedenhagen/Hudson-GIT-plugin/commit/031fff899fb0686f9cbafcb969f37a37361a4365", changeSetLink.toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GithubWeb#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException
     * @throws IOException
     */
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final List<GitChangeSet> changeSetList = createChangeSetList("rawchangelog");
        final Path path = changeSetList.get(0).getPaths().iterator().next();
        final URL diffLink = githubWeb.getDiffLink(path);
        assertEquals("http://github.com/mfriedenhagen/Hudson-GIT-plugin/commit/031fff899fb0686f9cbafcb969f37a37361a4365#diff-0", diffLink.toString());
    }

    /**
     * Test method for {@link hudson.plugins.git.browser.GithubWeb#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     * @throws SAXException
     * @throws IOException
     */
    public void testGetFileLinkPath() throws IOException, SAXException {
        final List<GitChangeSet> changeSetList = createChangeSetList("rawchangelog");
        final Path path = changeSetList.get(0).getPaths().iterator().next();
        final URL fileLink = githubWeb.getFileLink(path);
        assertEquals("http://github.com/mfriedenhagen/Hudson-GIT-plugin/blob/031fff899fb0686f9cbafcb969f37a37361a4365/src/main/java/hudson/plugins/git/browser/GithubWeb.java", String.valueOf(fileLink));
    }

    private List<GitChangeSet> createChangeSetList(String rawchangelogpath) throws IOException, SAXException {
        final File rawchangelog = new File(GithubWebTest.class.getResource(rawchangelogpath).getFile());
        final GitChangeLogParser logParser = new GitChangeLogParser(false);
        final List<GitChangeSet> changeSetList = logParser.parse(null, rawchangelog).getLogs();
        return changeSetList;
    }


}
