package hudson.plugins.git.browser;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSetUtil;
import hudson.plugins.git.GitException;

import org.eclipse.jgit.lib.ObjectId;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitRepositoryBrowserTest {

    private GitRepositoryBrowser browser;
    private GitChangeSet changeSet;
    private Collection<GitChangeSet.Path> paths;

    private final String baseURL = "https://github.com/jenkinsci/git-plugin/";

    private final boolean useAuthorName;
    private final String gitImplementation;
    private final ObjectId sha1;

    public GitRepositoryBrowserTest(String useAuthorName, String gitImplementation, ObjectId sha1) {
        this.useAuthorName = Boolean.valueOf(useAuthorName);
        this.gitImplementation = gitImplementation;
        this.sha1 = sha1;
    }

    private static final ObjectId HEAD = getMostRecentCommit();

    @Parameterized.Parameters(name = "{0},{1},{2}")
    public static Collection permuteAuthorNameAndGitImplementationAndObjectId() {
        List<Object[]> values = new ArrayList<>();
        String[] allowed = {"true", "false"};
        String[] implementations = {"git", "jgit"};
        ObjectId[] sha1Array = { // Use commits from git-plugin repo history
            ObjectId.fromString("016407404eeda093385ba2ebe9557068b519b669"), // simple commit
            ObjectId.fromString("4289aacbb493cfcb78c8276c52e945802942ffd5"), // merge commit
            ObjectId.fromString("daf453dfc43db81ede5cde60d0469fda0b3321ab"), // simple commit
            ObjectId.fromString("c685e980a502fa10e3a5fa08e02ab4194950c1df"), // Introduced findbugs warning
            ObjectId.fromString("8e4ef541b8f319fd2019932a6cddfc480fc7ca28"), // Old commit
            ObjectId.fromString("75ef0cde74e01f16b6da075d67cf88b3503067f5"), // First commit - no files, no parent
        };
        for (String authorName : allowed) {
            for (String gitImplementation : implementations) {
                Object[] headCommitCombination = {authorName, gitImplementation, HEAD};
                values.add(headCommitCombination);
                if (!isShallowClone()) {
                    for (ObjectId sha1 : sha1Array) {
                        Object[] combination = {authorName, gitImplementation, sha1};
                        values.add(combination);
                    }
                }
            }
        }
        return values;
    }

    private static boolean isShallowClone() {
        File shallowFile = new File(".git", "shallow");
        return shallowFile.isFile();
    }

    private static ObjectId getMostRecentCommit() {
        ObjectId headCommit;
        try {
            GitClient git = Git.with(TaskListener.NULL, new EnvVars()).getClient();
            headCommit = git.revParse("HEAD");
        } catch (GitException | IOException | InterruptedException e) {
            headCommit = ObjectId.fromString("016407404eeda093385ba2ebe9557068b519b669"); // simple commit
        }
        return headCommit;
    }

    @Before
    public void setUp() throws Exception {
        browser = new GitRepositoryBrowserImpl(null);
        changeSet = GitChangeSetUtil.genChangeSet(sha1, gitImplementation, useAuthorName);
        paths = changeSet.getPaths();
    }

    @Test
    public void testGetRepoUrl() {
        assertThat(browser.getRepoUrl(), is(nullValue()));
    }

    @Test
    public void testGetDiffLink() throws Exception {
        for (GitChangeSet.Path path : paths) {
            assertThat(browser.getDiffLink(path), is(getURL(path, true)));
        }
    }

    @Test
    public void testGetFileLink() throws Exception {
        for (GitChangeSet.Path path : paths) {
            assertThat(browser.getFileLink(path), is(getURL(path, false)));
        }
    }

    @Test
    public void testGetNormalizeUrl() {
        assertThat(browser.getNormalizeUrl(), is(true));
    }

    @Test
    public void testGetIndexOfPath() throws Exception {
        for (GitChangeSet.Path path : paths) {
            int location = browser.getIndexOfPath(path);

            // Assert that location is in bounds
            assertThat(location, is(lessThan(paths.size())));
            assertThat(location, is(greaterThan(-1)));
        }
    }

    private URL getURL(GitChangeSet.Path path, boolean isDiffLink) throws MalformedURLException {
        return new URL(baseURL + path.getPath() + (isDiffLink ? "-diff-link" : "-file-link"));
    }

    public class GitRepositoryBrowserImpl extends GitRepositoryBrowser {

        protected GitRepositoryBrowserImpl(String repourl) {
            super(repourl);
        }

        @Override
        public URL getDiffLink(GitChangeSet.Path path) throws IOException {
            return getURL(path, true);
        }

        @Override
        public URL getFileLink(GitChangeSet.Path path) throws IOException {
            return getURL(path, false);
        }

        @Override
        public URL getChangeSetLink(GitChangeSet e) throws IOException {
            throw new UnsupportedOperationException("Not implemented.");
        }
    }
}
