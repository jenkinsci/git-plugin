package hudson.plugins.git;

import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.model.User;

import java.util.Set;

import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Sets;

/**
 * Unit test for change sets with various exclusions.
 * @author Pavel Baranchikov
 */
public class GitChangeSetExlusionsTest extends AbstractGitTestCase {

    private static final String EXCL_FILE_PREFIX = "excludedFile";
    private static final String NON_EXCL_FILE_PREFIX = "neededFile";
    private int counter = 0;
    private FreeStyleProject project;
    private GitSCM gitScm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = setupProject("master", false, null, "ex.*", null, null);
        gitScm = (GitSCM) project.getScm();
        commitNonExcluded(johnDoe);
        build(project, Result.SUCCESS);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // Clean up to avoid collisions
        project = null;
    }

    private void commit(PersonIdent committer, String prefix) throws Exception {
        final String commitFile = prefix + counter++;
        commit(commitFile, committer, "Commited file " + commitFile);
    }

    private void commitExcluded(PersonIdent committer) throws Exception {
        commit(committer, EXCL_FILE_PREFIX);
    }

    private void commitNonExcluded(PersonIdent committer) throws Exception {
        commit(committer, NON_EXCL_FILE_PREFIX);
    }

    private void testCulprits(PersonIdent... expectedCulprits) throws Exception {
        final FreeStyleBuild build = build(project, Result.SUCCESS);
        final Set<String> expected = Sets.newHashSet();
        for (PersonIdent culprit : expectedCulprits) {
            expected.add(culprit.getName());
        }
        final Set<String> actual = Sets.newHashSet();
        for (User user : build.getCulprits()) {
            actual.add(user.getFullName());
        }
        Assert.assertEquals("Expected culprits differ from actual", expected, actual);
    }

    @Test
    public void testAllExcluded() throws Exception {
        gitScm.getDescriptor().setHideExcludedInChangeList(true);
        commitExcluded(johnDoe);
        commitExcluded(janeDoe);
        testCulprits();
    }

    @Test
    public void testFirstExcluded() throws Exception {
        gitScm.getDescriptor().setHideExcludedInChangeList(true);
        commitExcluded(johnDoe);
        commitNonExcluded(janeDoe);
        testCulprits(janeDoe);
    }

    @Test
    public void testSecondExcluded() throws Exception {
        gitScm.getDescriptor().setHideExcludedInChangeList(true);
        commitNonExcluded(johnDoe);
        commitExcluded(janeDoe);
        testCulprits(johnDoe);
    }

    @Test
    public void testNoneExcluded() throws Exception {
        gitScm.getDescriptor().setHideExcludedInChangeList(true);
        commitNonExcluded(johnDoe);
        commitNonExcluded(janeDoe);
        testCulprits(johnDoe, janeDoe);
    }

    @Test
    public void testAllExcludedNoHide() throws Exception {
        gitScm.getDescriptor().setHideExcludedInChangeList(false);
        commitExcluded(johnDoe);
        commitExcluded(janeDoe);
        testCulprits(johnDoe, janeDoe);
    }

    @Test
    public void testNoneExcludedNoHide() throws Exception {
        gitScm.getDescriptor().setHideExcludedInChangeList(false);
        commitNonExcluded(johnDoe);
        commitNonExcluded(janeDoe);
        testCulprits(johnDoe, janeDoe);
    }

}
