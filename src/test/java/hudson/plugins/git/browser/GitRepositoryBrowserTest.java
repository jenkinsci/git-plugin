package hudson.plugins.git.browser;

import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSetUtil;

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
    private final boolean useLegacyFormat;
    private final boolean hasParent;

    public GitRepositoryBrowserTest(String useAuthorName, String useLegacyFormat, String hasParent) {
        this.useAuthorName = Boolean.valueOf(useAuthorName);
        this.useLegacyFormat = Boolean.valueOf(useLegacyFormat);
        this.hasParent = Boolean.valueOf(hasParent);
    }

    @Parameterized.Parameters(name = "{0},{1},{2}")
    public static Collection permuteAuthorNameAndLegacyFormatAndHasParent() {
        List<Object[]> values = new ArrayList<Object[]>();
        String[] allowed = {"true", "false"};
        for (String authorName : allowed) {
            for (String legacyFormat : allowed) {
                for (String hasParent : allowed) {
                    Object[] combination = {authorName, legacyFormat, hasParent};
                    values.add(combination);
                }
            }
        }
        return values;
    }

    @Before
    public void setUp() {
        browser = new GitRepositoryBrowserImpl();
        changeSet = GitChangeSetUtil.genChangeSet(true, true, true);
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
