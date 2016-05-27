package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSetUtil;

import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;
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

    @Parameterized.Parameters(name = "{0},{1},{2}")
    public static Collection permuteAuthorNameAndGitImplementationAndObjectId() {
        List<Object[]> values = new ArrayList<Object[]>();
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
                for (ObjectId sha1 : sha1Array) {
                    Object[] combination = {authorName, gitImplementation, sha1};
                    values.add(combination);
                }
            }
        }
        return values;
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
        browser = new GitRepositoryBrowserImpl();
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
        Set<Integer> foundLocations = new HashSet<Integer>(paths.size());
        for (GitChangeSet.Path path : paths) {
            int location = browser.getIndexOfPath(path);

            // Assert that location is in bounds
            assertThat(location, is(lessThan(paths.size())));
            assertThat(location, is(greaterThan(-1)));

            // Assert that location has not been seen before
            assertThat(foundLocations, not(hasItem(location)));
            foundLocations.add(location);
        }

        // Assert that exact number of locations were found
        assertThat(foundLocations.size(), is(paths.size()));
    }

    private URL getURL(GitChangeSet.Path path, boolean isDiffLink) throws MalformedURLException {
        return new URL(baseURL + path.getPath() + (isDiffLink ? "-diff-link" : "-file-link"));
    }

    public class GitRepositoryBrowserImpl extends GitRepositoryBrowser {

        public URL getDiffLink(GitChangeSet.Path path) throws IOException {
            return getURL(path, true);
        }

        public URL getFileLink(GitChangeSet.Path path) throws IOException {
            return getURL(path, false);
        }

        @Override
        public URL getChangeSetLink(GitChangeSet e) throws IOException {
            throw new UnsupportedOperationException("Not implemented.");
        }
    }
}
