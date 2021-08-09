package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleProject;

import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import hudson.plugins.git.util.BuildData;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.mockito.Mockito;

// NOTE: isRevExcluded generally returns null instead of false
@RunWith(Enclosed.class)
public class PathRestrictionTest {

    public abstract static class PathRestrictionExtensionTest extends GitSCMExtensionTest {

        protected FreeStyleProject project;
        protected TestGitRepo repo;
        protected BuildData mockBuildData = Mockito.mock(BuildData.class);

        @Override
        public void before() throws Exception {
            repo = new TestGitRepo("repo", tmp.newFolder(), listener);
            project = setupBasicProject(repo);
        }

    }

    public static class NoRulesTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction(null, null);
        }

        @Test
        public void test() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt", "bar/bar.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }

    public static class EmptyPathsTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction(".*", null);
        }

        @Test
        public void test() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>());
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }


    public static class BasicExcludeTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction(null, "bar.*");
        }

        @Test
        public void testMiss() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }

        @Test
        public void testMatch() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/bar.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }

    public static class BasicIncludeTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction("foo.*", null);
        }

        @Test
        public void testMatch() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }

        @Test
        public void testMiss() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/bar.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }


    public static class MultiExcludeTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction(null, "bar.*\n.*bax");
        }

        @Test
        public void testAccept() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt", "foo.foo", "README.mdown")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("docs.txt", "more-docs.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("a/really/long/path/file.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }

        @Test
        public void testReject() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/bar.txt", "foo.bax")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/docs.txt", "bar/more-docs.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }

    public static class MultiIncludeTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction("foo.*\nqux.*", null);
        }

        @Test
        public void testAccept() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt", "something/else")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt", "foo.foo", "README.mdown")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("docs.txt", "qux/more-docs.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }

        @Test
        public void testReject() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/bar.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/bar.txt", "bar.bar", "README.mdown")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("docs.txt", "more-docs.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("a/really/long/path/file.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }
}

class FakePathGitChangeSet extends GitChangeSet {

    private Collection<String> paths;

    public FakePathGitChangeSet(Collection<String> paths) {
        super(Collections.emptyList(), false);
        this.paths = paths;
    }

    @Override
    public Collection<String> getAffectedPaths() {
        return paths;
    }

    @Override
    public String getCommitId() {
        return "fake123";
    }
}
