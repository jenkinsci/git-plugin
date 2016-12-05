package hudson.plugins.git.browser;

import hudson.model.Run;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;
import org.junit.Test;

import org.xml.sax.SAXException;

public class GitLabTest {

    private static final String GITLAB_URL = "https://SERVER/USER/REPO/";
    private final GitLab gitlab29 = new GitLab(GITLAB_URL, "2.9");
    private final GitLab gitlab42 = new GitLab(GITLAB_URL, "4.2");
    private final GitLab gitlab50 = new GitLab(GITLAB_URL, "5.0");
    private final GitLab gitlab51 = new GitLab(GITLAB_URL, "5.1");
    private final GitLab gitlab711 = new GitLab(GITLAB_URL, "7.11"); /* Which is < 7.2 ! */
//    private final GitLab gitlab7114ee = new GitLab(GITLAB_URL, "7.11.4.ee"); /* Totally borked */
    private final GitLab gitlab7114ee = new GitLab(GITLAB_URL, "7.11");  /* Which is < 7.2 ! */
    private final GitLab gitlab80 = new GitLab(GITLAB_URL, "8.0");
    private final GitLab gitlab87 = new GitLab(GITLAB_URL, "8.7");
    private final GitLab gitlabDefault = new GitLab(GITLAB_URL, "");
    private final GitLab gitlabNaN = new GitLab(GITLAB_URL, "NaN");
    private final GitLab gitlabInfinity = new GitLab(GITLAB_URL, "Infinity");
    private final GitLab gitlabNegative = new GitLab(GITLAB_URL, "-1");
    private final GitLab gitlabGreater = new GitLab(GITLAB_URL, "9999");

    private final String SHA1 = "396fc230a3db05c427737aa5c2eb7856ba72b05d";
    private final String fileName = "src/main/java/hudson/plugins/git/browser/GithubWeb.java";

    /**
     * Test method for {@link hudson.plugins.git.browser.GitLab#getVersion()}.
     */
    @Test
    public void testGetVersion() {
        assertEquals(2.9, gitlab29.getVersion(), .001);
        assertEquals(4.2, gitlab42.getVersion(), .001);
        assertEquals(5.0, gitlab50.getVersion(), .001);
        assertEquals(5.1, gitlab51.getVersion(), .001);
        assertEquals(GitLab.DEFAULT_VERSION, gitlab87.getVersion(), .001);
        assertEquals(GitLab.DEFAULT_VERSION, gitlabDefault.getVersion(), .001);
        assertEquals(GitLab.DEFAULT_VERSION, gitlabNaN.getVersion(), .001);
        assertEquals(GitLab.DEFAULT_VERSION, gitlabInfinity.getVersion(), .001);
        assertEquals(-1.0, gitlabNegative.getVersion(), .001);
        assertEquals(9999.0, gitlabGreater.getVersion(), .001);
    }

    /**
     * Test method for
     * {@link hudson.plugins.git.browser.GitLab#getChangeSetLink(hudson.plugins.git.GitChangeSet)}.
     *
     * @throws IOException on input or output error
     */
    @Test
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final GitChangeSet changeSet = createChangeSet("rawchangelog");
        final String expectedURL = GITLAB_URL + "commit/" + SHA1;
        assertEquals(expectedURL.replace("commit/", "commits/"), gitlab29.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlab42.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlab50.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlab51.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlab711.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlab7114ee.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlabDefault.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlabNaN.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlabInfinity.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL.replace("commit/", "commits/"), gitlabNegative.getChangeSetLink(changeSet).toString());
        assertEquals(expectedURL, gitlabGreater.getChangeSetLink(changeSet).toString());
    }

    /**
     * Test method for
     * {@link hudson.plugins.git.browser.GitLab#getDiffLink(hudson.plugins.git.GitChangeSet.Path)}.
     *
     * @throws IOException on input or output error
     */
    @Test
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path modified1 = pathMap.get(fileName);
        final String expectedPre30 = GITLAB_URL + "commits/" + SHA1 + "#" + fileName;
        final String expectedPre80 = GITLAB_URL + "commit/" + SHA1 + "#" + fileName;
        final String expectedURL = GITLAB_URL + "commit/" + SHA1 + "#" + "diff-0";
        final String expectedDefault = expectedURL;
        assertEquals(expectedPre30, gitlabNegative.getDiffLink(modified1).toString());
        assertEquals(expectedPre30, gitlab29.getDiffLink(modified1).toString());
        assertEquals(expectedPre80, gitlab42.getDiffLink(modified1).toString());
        assertEquals(expectedPre80, gitlab50.getDiffLink(modified1).toString());
        assertEquals(expectedPre80, gitlab51.getDiffLink(modified1).toString());
        assertEquals(expectedPre80, gitlab711.getDiffLink(modified1).toString());
        assertEquals(expectedPre80, gitlab7114ee.getDiffLink(modified1).toString());
        assertEquals(expectedURL, gitlab80.getDiffLink(modified1).toString());
        assertEquals(expectedURL, gitlabGreater.getDiffLink(modified1).toString());
        
        assertEquals(expectedDefault, gitlabDefault.getDiffLink(modified1).toString());
        assertEquals(expectedDefault, gitlabNaN.getDiffLink(modified1).toString());
        assertEquals(expectedDefault, gitlabInfinity.getDiffLink(modified1).toString());
    }

    /**
     * Test method for
     * {@link hudson.plugins.git.browser.GitLab#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     *
     * @throws IOException on input or output error
     */
    @Test
    public void testGetFileLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get(fileName);
        final String expectedURL = GITLAB_URL + "blob/396fc230a3db05c427737aa5c2eb7856ba72b05d/" + fileName;
        final String expectedV29 = expectedURL.replace("blob/", "tree/");
        final String expectedV50 = GITLAB_URL + "396fc230a3db05c427737aa5c2eb7856ba72b05d/tree/" + fileName;
        assertEquals(expectedV29, gitlabNegative.getFileLink(path).toString());
        assertEquals(expectedV29, gitlab29.getFileLink(path).toString());
        assertEquals(expectedV29, gitlab42.getFileLink(path).toString());
        assertEquals(expectedV50, gitlab50.getFileLink(path).toString());
        assertEquals(expectedURL, gitlab51.getFileLink(path).toString());
        assertEquals(expectedURL, gitlab711.getFileLink(path).toString());
        assertEquals(expectedURL, gitlab7114ee.getFileLink(path).toString());
        assertEquals(expectedURL, gitlabDefault.getFileLink(path).toString());
        assertEquals(expectedURL, gitlabNaN.getFileLink(path).toString());
        assertEquals(expectedURL, gitlabInfinity.getFileLink(path).toString());
        assertEquals(expectedURL, gitlabGreater.getFileLink(path).toString());
    }

    /**
     * Test method for
     * {@link hudson.plugins.git.browser.GitLab#getFileLink(hudson.plugins.git.GitChangeSet.Path)}.
     *
     * @throws IOException on input or output error
     */
    @Test
    public void testGetFileLinkPathForDeletedFile() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final String fileName = "bar";
        final Path path = pathMap.get(fileName);
        final String SHA1 = "fc029da233f161c65eb06d0f1ed4f36ae81d1f4f";
        final String expectedPre30 = GITLAB_URL + "commits/" + SHA1 + "#" + fileName;
        final String expectedPre80 = GITLAB_URL + "commit/" + SHA1 + "#" + fileName;
        final String expectedURL = GITLAB_URL + "commit/" + SHA1 + "#" + "diff-0";
        final String expectedDefault = expectedURL;
 
        assertEquals(expectedPre30, gitlabNegative.getFileLink(path).toString());
        assertEquals(expectedPre30, gitlab29.getFileLink(path).toString());
        assertEquals(expectedPre80, gitlab42.getFileLink(path).toString());
        assertEquals(expectedPre80, gitlab50.getFileLink(path).toString());
        assertEquals(expectedPre80, gitlab51.getFileLink(path).toString());
        assertEquals(expectedPre80, gitlab711.getFileLink(path).toString());
        assertEquals(expectedPre80, gitlab7114ee.getFileLink(path).toString());
        assertEquals(expectedURL, gitlab80.getFileLink(path).toString());
        assertEquals(expectedURL, gitlabGreater.getFileLink(path).toString());
        
        assertEquals(expectedDefault, gitlabDefault.getFileLink(path).toString());
        assertEquals(expectedDefault, gitlabNaN.getFileLink(path).toString());
        assertEquals(expectedDefault, gitlabInfinity.getFileLink(path).toString());

    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final GitChangeLogParser logParser = new GitChangeLogParser(false);
        final List<GitChangeSet> changeSetList = logParser.parse(GitLabTest.class.getResourceAsStream(rawchangelogpath));
        return changeSetList.get(0);
    }

    private HashMap<String, Path> createPathMap(final String changelog) throws IOException, SAXException {
        final HashMap<String, Path> pathMap = new HashMap<>();
        final Collection<Path> changeSet = createChangeSet(changelog).getPaths();
        for (final Path path : changeSet) {
            pathMap.put(path.getPath(), path);
        }
        return pathMap;
    }

}
