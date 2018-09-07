/**
 * Copyright 2010 Mirko Friedenhagen
 */

package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.plugins.git.GitSCM;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import org.eclipse.jgit.transport.RefSpec;

import static org.junit.Assert.*;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import org.xml.sax.SAXException;

/**
 * @author mirko
 */
public class GithubWebTest {

    private static final String GITHUB_URL = "http://github.com/USER/REPO";
    private final GithubWeb githubWeb = new GithubWeb(GITHUB_URL);

    @Test
    public void testGetUrl() throws IOException {
        assertEquals(String.valueOf(githubWeb.getUrl()), GITHUB_URL  + "/");
    }

    @Test
    public void testGetUrlForRepoWithTrailingSlash() throws IOException {
        assertEquals(String.valueOf(new GithubWeb(GITHUB_URL + "/").getUrl()), GITHUB_URL  + "/");
    }

    @Test
    public void testGetChangeSetLinkGitChangeSet() throws IOException, SAXException {
        final URL changeSetLink = githubWeb.getChangeSetLink(createChangeSet("rawchangelog"));
        assertEquals(GITHUB_URL + "/commit/396fc230a3db05c427737aa5c2eb7856ba72b05d", changeSetLink.toString());
    }

    @Test
    public void testGetDiffLinkPath() throws IOException, SAXException {
        final HashMap<String, Path> pathMap = createPathMap("rawchangelog");
        final Path path1 = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        assertEquals(GITHUB_URL + "/commit/396fc230a3db05c427737aa5c2eb7856ba72b05d#diff-0", githubWeb.getDiffLink(path1).toString());
        final Path path2 = pathMap.get("src/test/java/hudson/plugins/git/browser/GithubWebTest.java");
        assertEquals(GITHUB_URL + "/commit/396fc230a3db05c427737aa5c2eb7856ba72b05d#diff-1", githubWeb.getDiffLink(path2).toString());
        final Path path3 = pathMap.get("src/test/resources/hudson/plugins/git/browser/rawchangelog-with-deleted-file");
        assertNull("Do not return a diff link for added files.", githubWeb.getDiffLink(path3));
    }

    @Test
    public void testGetFileLinkPath() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog");
        final Path path = pathMap.get("src/main/java/hudson/plugins/git/browser/GithubWeb.java");
        final URL fileLink = githubWeb.getFileLink(path);
        assertEquals(GITHUB_URL  + "/blob/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/main/java/hudson/plugins/git/browser/GithubWeb.java", String.valueOf(fileLink));
    }

    @Issue("JENKINS-42597")
    @Test
    public void testGetFileLinkPathWithEscape() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-escape");
        final Path path = pathMap.get("src/test/java/hudson/plugins/git/browser/conf%.txt");
        final URL fileLink = githubWeb.getFileLink(path);
        assertEquals(GITHUB_URL  + "/blob/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/test/java/hudson/plugins/git/browser/conf%25.txt", String.valueOf(fileLink));
    }

    @Issue("JENKINS-42597")
    @Test
    public void testGetFileLinkPathWithSpaceInName() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-escape");
        final Path path = pathMap.get("src/test/java/hudson/plugins/git/browser/config file.txt");
        final URL fileLink = githubWeb.getFileLink(path);
        assertEquals(GITHUB_URL  + "/blob/396fc230a3db05c427737aa5c2eb7856ba72b05d/src/test/java/hudson/plugins/git/browser/config%20file.txt", String.valueOf(fileLink));
    }

    @Test
    public void testGetFileLinkPathForDeletedFile() throws Exception {
        final HashMap<String,Path> pathMap = createPathMap("rawchangelog-with-deleted-file");
        final Path path = pathMap.get("bar");
        final URL fileLink = githubWeb.getFileLink(path);
        assertEquals(GITHUB_URL + "/commit/fc029da233f161c65eb06d0f1ed4f36ae81d1f4f#diff-0", String.valueOf(fileLink));
    }

    private String repoUrl(String baseUrl, boolean add_git_suffix, boolean add_slash_suffix) {
        return baseUrl + (add_git_suffix ? ".git" : "") + (add_slash_suffix ? "/" : "");
    }
        
    @Test
    public void testGuessBrowser() {
        assertGuessURL("https://github.com/kohsuke/msv.git", "https://github.com/kohsuke/msv/");
        assertGuessURL("https://github.com/kohsuke/msv/", "https://github.com/kohsuke/msv/");
        assertGuessURL("https://github.com/kohsuke/msv", "https://github.com/kohsuke/msv/");
        assertGuessURL("git@github.com:kohsuke/msv.git", "https://github.com/kohsuke/msv/");
        assertGuessURL("git@git.apache.org:whatever.git", null);
        final boolean allowed [] = { Boolean.TRUE, Boolean.FALSE };
        for (final boolean add_git_suffix : allowed) {
            for (final boolean add_slash_suffix : allowed) {
                assertGuessURL(repoUrl("git@github.com:kohsuke/msv", add_git_suffix, add_slash_suffix), "https://github.com/kohsuke/msv/");
                assertGuessURL(repoUrl("https://github.com/kohsuke/msv", add_git_suffix, add_slash_suffix), "https://github.com/kohsuke/msv/");
                assertGuessURL(repoUrl("ssh://github.com/kohsuke/msv", add_git_suffix, add_slash_suffix), "https://github.com/kohsuke/msv/");
                assertGuessURL(repoUrl("ssh://git@github.com/kohsuke/msv", add_git_suffix, add_slash_suffix), "https://github.com/kohsuke/msv/");
            }
        }
    }

    private void assertGuessURL(String repo, String web) {
        RepositoryBrowser<?> guess = new GitSCM(repo).guessBrowser();
        String actual = guess instanceof GithubWeb ? ((GithubWeb) guess).getRepoUrl() : null;
        assertEquals("For repo '" + repo + "':", web, actual);
    }

    @Issue("JENKINS-33409")
    @Test
    public void guessBrowserSCMSource() throws Exception {
        // like GitSCMSource:
        assertGuessURL("https://github.com/kohsuke/msv.git", "https://github.com/kohsuke/msv/", "+refs/heads/*:refs/remotes/origin/*");
        // like GitHubSCMSource:
        assertGuessURL("https://github.com/kohsuke/msv.git", "https://github.com/kohsuke/msv/", "+refs/heads/*:refs/remotes/origin/*", "+refs/pull/*/merge:refs/remotes/origin/pr/*");
    }

    private void assertGuessURL(String remote, String web, String... refSpecs) {
        RepositoryBrowser<?> guess = new MockSCMSource(remote, refSpecs).build(new SCMHead("master")).guessBrowser();
        String actual = guess instanceof GithubWeb ? ((GithubWeb) guess).getRepoUrl() : null;
        assertEquals(web, actual);
    }

    private static class MockSCMSource extends AbstractGitSCMSource {
        private final String remote;
        private final String[] refSpecs;
        MockSCMSource(String remote, String[] refSpecs) {
            this.remote = remote;
            this.refSpecs = refSpecs;
        }
        @Override
        public String getCredentialsId() {
            return null;
        }
        @Override
        public String getRemote() {
            return remote;
        }
        @Override
        public String getIncludes() {
            return "*";
        }
        @Override
        public String getExcludes() {
            return "";
        }
        @Override
        protected List<RefSpec> getRefSpecs() {
            List<RefSpec> result = new ArrayList<>();
            for (String refSpec : refSpecs) {
                result.add(new RefSpec(refSpec));
            }
            return result;
        }
    }

    private GitChangeSet createChangeSet(String rawchangelogpath) throws IOException, SAXException {
        final GitChangeLogParser logParser = new GitChangeLogParser(false);
        final List<GitChangeSet> changeSetList = logParser.parse(GithubWebTest.class.getResourceAsStream(rawchangelogpath));
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
