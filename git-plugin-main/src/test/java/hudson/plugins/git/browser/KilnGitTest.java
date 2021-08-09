package hudson.plugins.git.browser;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Chris Klaiber (cklaiber@gmail.com)
 */
public class KilnGitTest {

    private static final String KILN_URL = "http://USER.kilnhg.com/Code/PROJECT/Group/REPO";
    private final KilnGit kilnGit = new KilnGit(KILN_URL);

    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(kilnGit.getUrl()), KILN_URL  + "/");
    }

    @Test
    public void testGetUrlForRepoWithTrailingSlash() throws IOException {
        assertEquals(String.valueOf(new KilnGit(KILN_URL + "/").getUrl()), KILN_URL  + "/");
    }

    @Test
    public void testGetChangeSetLinkGitChangeSet() throws Exception {
        final URL changeSetLink = kilnGit.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(KILN_URL + "/History/396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    @Test
    public void testGetDiffLinkPath() throws Exception {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path path1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(KILN_URL + "/History/396fc230a3db05c427737aa5c2eb7856ba72b05d#diff-1", kilnGit.getDiffLink(path1).toString());
        final Path path2 = pathMap.get("src/test/java/hudson/plugins/git/browser/GithubWebTest.java");
        assertEquals(KILN_URL + "/History/396fc230a3db05c427737aa5c2eb7856ba72b05d#diff-2", kilnGit.getDiffLink(path2).toString());
        final Path path3 = pathMap.get("src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file");
        assertNull("Do not return a diff link for added files.", kilnGit.getDiffLink(path3));
    }

    @Test
    public void testGetFileLinkPath() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = kilnGit.getFileLink(path);
        assertEquals(KILN_URL  + "/FileHistory/src/main/java/hudson/plugins/git/browser/GithubWeb.java?rev=396fc230a3db05c427737aa5c2eb7856ba72b05d", String.valueOf(fileLink));
    }

    @Test
    public void testGetFileLinkPathForDeletedFile() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = kilnGit.getFileLink(path);
        assertEquals(KILN_URL + "/History/fc029da233f161c65eb06d0f1ed4f36ae81d1f4f#diff-1", String.valueOf(fileLink));
    }

    private final Random random = new Random();

    private GitChangeSet createChangeSet(String rawchangelogpath) throws Exception {
        /* Use randomly selected git client implementation since the client implementation should not change result */
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).in(new File(".")).using(random.nextBoolean() ? null : "jgit").getClient();
        final GitChangeLogParser logParser = new GitChangeLogParser(gitClient, false);
        final List<GitChangeSet> changeSetList = logParser.parse(KilnGitTest.class.getResourceAsStream(rawchangelogpath));
        return changeSetList.get(0);
    }

    private HashMap<String, Path> createPathMap(final String changelog) throws Exception {
        final HashMap<String, Path> pathMap = new HashMap<>();
        final Collection<Path> changeSet = createChangeSet(changelog).getPaths();
        for (final Path path : changeSet) {
            pathMap.put(path.getPath(), path);
        }
        return pathMap;
    }
}
