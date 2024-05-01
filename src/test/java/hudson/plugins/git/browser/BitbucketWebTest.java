/**
 * Copyright 2010 Mirko Friedenhagen
 */

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
 * @author mattsemar
 */
public class BitbucketWebTest {

    private static final String BITBUCKET_URL = "http://bitbucket.org/USER/REPO";
    private final BitbucketWeb bitbucketWeb = new BitbucketWeb(BITBUCKET_URL);

    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(bitbucketWeb.getUrl()), BITBUCKET_URL + "/");
    }

    @Test
    public void testGetUrlForRepoWithTrailingSlash() throws IOException {
        assertEquals(String.valueOf(new BitbucketWeb(BITBUCKET_URL + "/").getUrl()), BITBUCKET_URL + "/");
    }

    @Test
    public void testGetChangeSetLinkGitChangeSet() throws Exception {
        final URL changeSetLink = bitbucketWeb.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(BITBUCKET_URL + "/commits/396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    @Test
    public void testGetDiffLinkPath() throws Exception {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final String path1Str = "src/main/java/hudson/plugins/git/browser/GithubWeb.java";
        final Path path1 = pathMap.get(path1Str);

        assertEquals(BITBUCKET_URL + "/commits/396fc230a3db05c427737aa5c2eb7856ba72b05d#chg-" + path1Str, bitbucketWeb.getDiffLink(path1).toString());

        final String path2Str = "src/test/java/hudson/plugins/git/browser/GithubWebTest.java";
        final Path path2 = pathMap.get(path2Str);
        assertEquals(BITBUCKET_URL + "/commits/396fc230a3db05c427737aa5c2eb7856ba72b05d#chg-" + path2Str, bitbucketWeb.getDiffLink(path2).toString());
        final String path3Str = "src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file";
        final Path path3 = pathMap.get(path3Str);
        assertNull("Do not return a diff link for added files.", bitbucketWeb.getDiffLink(path3));
    }

    @Test
    public void testGetFileLinkPath() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = bitbucketWeb.getFileLink(path);
        assertEquals(BITBUCKET_URL + "/history/src/main/java/hudson/plugins/git/browser/GithubWeb.java", String.valueOf(fileLink));
    }

    @Test
    public void testGetFileLinkPathForDeletedFile() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = bitbucketWeb.getFileLink(path);
        assertEquals(BITBUCKET_URL + "/history/bar", String.valueOf(fileLink));
    }

    private final Random random = new Random();

    private GitChangeSet createChangeSet(String rawchangelogpath) throws Exception {
        /* Use randomly selected git client implementation since the client implementation should not change result */
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).in(new File(".")).using(random.nextBoolean() ? "Default" : "jgit").getClient();
        final GitChangeLogParser logParser = new GitChangeLogParser(gitClient, false);
        final List<GitChangeSet> changeSetList = logParser.parse(BitbucketWebTest.class.getResourceAsStream(rawchangelogpath));
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
