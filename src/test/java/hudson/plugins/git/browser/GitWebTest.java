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

public class GitWebTest {

    private static final String GITWEB_URL = "https://SERVER/gitweb?repo.git";
    private final GitWeb gitwebWeb = new GitWeb(GITWEB_URL);

    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(gitwebWeb.getUrl()), GITWEB_URL);
    }

    @Test
    public void testGetChangeSetLinkGitChangeSet() throws Exception {
        final URL changeSetLink = gitwebWeb.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(GITWEB_URL + "&a=commit&h=396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    @Test
    public void testGetDiffLinkPath() throws Exception {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path modified1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(GITWEB_URL + "&a=blobdiff&f=src/main/java/hudson/plugins/git/browser/GithubWeb.java&fp=src/main/java/hudson/plugins/git/browser/GithubWeb.java&h=3f28ad75f5ecd5e0ea9659362e2eef18951bd451&hp=2e0756cd853dccac638486d6aab0e74bc2ef4041&hb=396fc230a3db05c427737aa5c2eb7856ba72b05d&hpb=f28f125f4cc3e5f6a32daee6a26f36f7b788b8ff", gitwebWeb.getDiffLink(modified1).toString());
    }

    @Test
    public void testGetFileLinkPath() throws Exception {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = gitwebWeb.getFileLink(path);
        assertEquals(GITWEB_URL  + "&a=blob&f=src/main/java/hudson/plugins/git/browser/GithubWeb.java&h=2e0756cd853dccac638486d6aab0e74bc2ef4041&hb=396fc230a3db05c427737aa5c2eb7856ba72b05d", String.valueOf(fileLink));
    }

    @Test
    public void testGetFileLinkPathForDeletedFile() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = gitwebWeb.getFileLink(path);
        assertEquals(GITWEB_URL + "&a=blob&f=bar&h=257cc5642cb1a054f08cc83f2d943e56fd3ebe99&hb=fc029da233f161c65eb06d0f1ed4f36ae81d1f4f", String.valueOf(fileLink));
    }

    private final Random random = new Random();

    private GitChangeSet createChangeSet(String rawchangelogpath) throws Exception {
        /* Use randomly selected git client implementation since the client implementation should not change result */
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).in(new File(".")).using(random.nextBoolean() ? null : "jgit").getClient();
        final GitChangeLogParser logParser = new GitChangeLogParser(gitClient, random.nextBoolean());
        final List<GitChangeSet> changeSetList = logParser.parse(GitWebTest.class.getResourceAsStream(rawchangelogpath));
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
