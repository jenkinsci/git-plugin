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

public class RhodeCodeTest {

    private static final String RHODECODE_URL = "https://SERVER/r/PROJECT";
    private final RhodeCode rhodecode = new RhodeCode(RHODECODE_URL);

    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(rhodecode.getUrl()), RHODECODE_URL  + "/");
    }

    @Test
    public void testGetUrlForRepoWithTrailingSlash() throws IOException {
        assertEquals(String.valueOf(new RhodeCode(RHODECODE_URL + "/").getUrl()), RHODECODE_URL  + "/");
    }

    @Test
    public void testGetChangeSetLinkGitChangeSet() throws Exception {
        final URL changeSetLink = rhodecode.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(RHODECODE_URL + "/changeset/396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    @Test
    public void testGetDiffLinkPath() throws Exception {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path modified1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(RHODECODE_URL + "/diff/src/main/java/hudson/plugins/git/browser/GithubWeb.java?diff2=f28f125f4cc3e5f6a32daee6a26f36f7b788b8ff&diff1=396fc230a3db05c427737aa5c2eb7856ba72b05d&diff=diff+to+revision", rhodecode.getDiffLink(modified1).toString());
        // For added files returns a link to the commit.
        final Path added = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(RHODECODE_URL + "/diff/src/main/java/hudson/plugins/git/browser/GithubWeb.java?diff2=f28f125f4cc3e5f6a32daee6a26f36f7b788b8ff&diff1=396fc230a3db05c427737aa5c2eb7856ba72b05d&diff=diff+to+revision", rhodecode.getDiffLink(added).toString());
    }

    @Test
    public void testGetFileLinkPath() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = rhodecode.getFileLink(path);
        assertEquals(RHODECODE_URL  + "/files/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/main/java/hudson/plugins/git/browser/GithubWeb.java", String.valueOf(fileLink));
    }

    @Test
    public void testGetFileLinkPathForDeletedFile() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = rhodecode.getFileLink(path);
        assertEquals(RHODECODE_URL + "/files/b547aa10c3f06710c6fdfcdb2a9149c81662923b/bar", String.valueOf(fileLink));
    }

    private final Random random = new Random();

    private GitChangeSet createChangeSet(String rawchangelogpath) throws Exception {
        /* Use randomly selected git client implementation since the client implementation should not change result */
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).in(new File(".")).using(random.nextBoolean() ? null : "jgit").getClient();
        final GitChangeLogParser logParser = new GitChangeLogParser(gitClient, false);
        final List<GitChangeSet> changeSetList = logParser.parse(RhodeCodeTest.class.getResourceAsStream(rawchangelogpath));
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
