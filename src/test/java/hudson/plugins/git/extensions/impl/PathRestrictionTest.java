package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleProject;

import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import hudson.plugins.git.util.BuildData;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;

// NOTE: isRevExcluded generally returns null instead of false
class PathRestrictionTest {

    abstract static class PathRestrictionExtensionTest extends GitSCMExtensionTest {

        protected FreeStyleProject project;
        protected TestGitRepo repo;
        protected BuildData mockBuildData = Mockito.mock(BuildData.class);

        @Override
        public void before() throws Exception {
            repo = new TestGitRepo("repo", newFolder(tmp, "junit"), listener);
            project = setupBasicProject(repo);
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }

    }

    @Nested
    class NoRulesTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction(null, null);
        }

        @Test
        void test() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt", "bar/bar.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }

    @Nested
    class EmptyPathsTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction(".*", null);
        }

        @Test
        void test() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>());
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }


    @Nested
    class BasicExcludeTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction(null, "bar.*");
        }

        @Test
        void testMiss() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Collections.singletonList("foo/foo.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }

        @Test
        void testMatch() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Collections.singletonList("bar/bar.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }

    @Nested
    class BasicIncludeTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction("foo.*", null);
        }

        @Test
        void testMatch() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Collections.singletonList("foo/foo.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }

        @Test
        void testMiss() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Collections.singletonList("bar/bar.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }


    @Nested
    class MultiExcludeTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction(null, "bar.*\n.*bax");
        }

        @Test
        void testAccept() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Collections.singletonList("foo/foo.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt", "foo.foo", "README.mdown")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("docs.txt", "more-docs.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Collections.singletonList("a/really/long/path/file.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }

        @Test
        void testReject() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/bar.txt", "foo.bax")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/docs.txt", "bar/more-docs.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }
    }

    @Nested
    class MultiIncludeTest extends PathRestrictionExtensionTest {

        @Override
        protected GitSCMExtension getExtension() {
            return new PathRestriction("foo.*\nqux.*", null);
        }

        @Test
        void testAccept() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt", "something/else")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("foo/foo.txt", "foo.foo", "README.mdown")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("docs.txt", "qux/more-docs.txt")));
            assertNull(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
        }

        @Test
        void testReject() throws Exception {
            GitChangeSet commit = new FakePathGitChangeSet(new HashSet<>(Collections.singletonList("bar/bar.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("bar/bar.txt", "bar.bar", "README.mdown")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Arrays.asList("docs.txt", "more-docs.txt")));
            assertTrue(getExtension().isRevExcluded((hudson.plugins.git.GitSCM) project.getScm(), repo.git, commit, listener, mockBuildData));
            commit = new FakePathGitChangeSet(new HashSet<>(Collections.singletonList("a/really/long/path/file.txt")));
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
