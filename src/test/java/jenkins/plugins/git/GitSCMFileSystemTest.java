/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package jenkins.plugins.git;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.GitException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import jenkins.plugins.git.CliGitCommand;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
public class GitSCMFileSystemTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private final static String GIT_2_6_0_TAG = "git-2.6.0";
    private final static String GIT_2_6_1_TAG = "git-2.6.1";

    /* This test requires the tag git-2.6.1 and git-2.6.0. If you're working from a
     * forked copy of the repository and your fork was created before the
     * git-2.6.1 plugin release, you may not have that tag in your fork.
     * If you do not have that tag, you will need to include that tag in
     * your fork.  You can do that with the commands:
     *
     * $ git remote add upstream https://github.com/jenkinsci/git-plugin
     * $ git fetch --tags upstream
     * $ git push --tags origin
     */
    @BeforeClass
    public static void confirmTagsAvailable() throws Exception {
        File gitDir = new File(".");
        GitClient client = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using("jgit").getClient();

        String[] tags = { GIT_2_6_0_TAG, GIT_2_6_1_TAG };
        for (String tag : tags) {
            ObjectId tagId;
            try {
                tagId = client.revParse(tag);
            } catch (GitException ge) {
                CliGitCommand gitCmd = new CliGitCommand(null);
                gitCmd.run("fetch", "--tags");
                tagId = client.revParse(tag); /* throws if tag not available */
            }
        }
    }

    @Test
    public void ofSource_Smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"));
        assertThat(fs, notNullValue());
        SCMFile root = fs.getRoot();
        assertThat(root, notNullValue());
        assertTrue(root.isRoot());
        // assertTrue(root.isDirectory()); // IllegalArgumentException
        // assertTrue(root.exists()); // IllegalArgumentException
        // assertFalse(root.isFile()); // IllegalArgumentException
        Iterable<SCMFile> children = root.children();
        Iterator<SCMFile> iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile file = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(file.getName(), is("file"));
        assertThat(file.contentAsString(), is("modified"));
    }

    @Test
    public void ofSourceRevision() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMRevision revision = source.fetch(new SCMHead("dev"), null);
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"), revision);
        assertThat(fs, notNullValue());
        assertThat(fs.getRoot(), notNullValue());
        Iterable<SCMFile> children = fs.getRoot().children();
        Iterator<SCMFile> iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile file = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(file.getName(), is("file"));
        assertThat(file.contentAsString(), is(""));
    }

    @Issue("JENKINS-42817")
    @Test
    public void slashyBranches() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "bug/JENKINS-42817");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMFileSystem fs = SCMFileSystem.of(r.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo.toString(), null), Collections.singletonList(new BranchSpec("*/bug/JENKINS-42817")), false, Collections.<SubmoduleConfig>emptyList(), null, null, Collections.<GitSCMExtension>emptyList()));
        assertThat(fs, notNullValue());
        SCMFile root = fs.getRoot();
        assertThat(root, notNullValue());
        assertTrue(root.isRoot());
        Iterable<SCMFile> children = root.children();
        Iterator<SCMFile> iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile file = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(file.getName(), is("file"));
        assertThat(file.contentAsString(), is("modified"));
    }

    @Test
    public void lastModified_Smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMRevision revision = source.fetch(new SCMHead("dev"), null);
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        final long fileSystemAllowedOffset = isWindows() ? 4000 : 1500;
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"), revision);
        long currentTime = isWindows() ? System.currentTimeMillis() / 1000L * 1000L : System.currentTimeMillis();
        long lastModified = fs.lastModified();
        assertThat(lastModified, greaterThanOrEqualTo(currentTime - fileSystemAllowedOffset));
        assertThat(lastModified, lessThanOrEqualTo(currentTime + fileSystemAllowedOffset));
        SCMFile file = fs.getRoot().child("file");
        currentTime = isWindows() ? System.currentTimeMillis() / 1000L * 1000L : System.currentTimeMillis();
        lastModified = file.lastModified();
        assertThat(lastModified, greaterThanOrEqualTo(currentTime - fileSystemAllowedOffset));
        assertThat(lastModified, lessThanOrEqualTo(currentTime + fileSystemAllowedOffset));
    }

    @Test
    public void directoryTraversal() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.mkdirs("dir/subdir");
        sampleRepo.git("mv", "file", "dir/subdir/file");
        sampleRepo.write("dir/subdir/file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"));
        assertThat(fs, notNullValue());
        assertThat(fs.getRoot(), notNullValue());
        Iterable<SCMFile> children = fs.getRoot().children();
        Iterator<SCMFile> iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile dir = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(dir.getName(), is("dir"));
        assertThat(dir.getType(), is(SCMFile.Type.DIRECTORY));
        children = dir.children();
        iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile subdir = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(subdir.getName(), is("subdir"));
        assertThat(subdir.getType(), is(SCMFile.Type.DIRECTORY));
        children = subdir.children();
        iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile file = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(file.getName(), is("file"));
        assertThat(file.contentAsString(), is("modified"));
    }

    @Test
    public void mixedContent() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.write("file2", "new");
        sampleRepo.git("add", "file2");
        sampleRepo.write("dir/file3", "modified");
        sampleRepo.git("add", "file", "dir/file3");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"));
        assertThat(fs, notNullValue());
        assertThat(fs.getRoot(), notNullValue());
        Iterable<SCMFile> children = fs.getRoot().children();
        Set<String> names = new TreeSet<String>();
        SCMFile file = null;
        SCMFile file2 = null;
        SCMFile dir = null;
        for (SCMFile f: children) {
            names.add(f.getName());
            if ("file".equals(f.getName())) {
                file = f;
            } else if ("file2".equals(f.getName())) {
                file2 = f;
            } else if ("dir".equals(f.getName())) {
                dir = f;
            }
        }
        assertThat(names, containsInAnyOrder(is("file"), is("file2"), is("dir")));
        assertThat(file.getType(), is(SCMFile.Type.REGULAR_FILE));
        assertThat(file2.getType(), is(SCMFile.Type.REGULAR_FILE));
        assertThat(dir.getType(), is(SCMFile.Type.DIRECTORY));
        assertThat(file.contentAsString(), is("modified"));
        assertThat(file2.contentAsString(), is("new"));
    }

    @Test
    public void given_filesystem_when_askingChangesSinceSameRevision_then_changesAreEmpty() throws Exception {
        File gitDir = new File(".");
        GitClient client = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using("git").getClient();

        ObjectId git261 = client.revParse(GIT_2_6_1_TAG);
        AbstractGitSCMSource.SCMRevisionImpl rev261 =
                new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead("origin"), git261.getName());
        GitSCMFileSystem gitPlugin261FS = new GitSCMFileSystem(client, "origin", git261.getName(), rev261);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertFalse(gitPlugin261FS.changesSince(rev261, out));
        assertThat(out.toString(), is(""));
    }

    @Test
    public void given_filesystem_when_askingChangesSinceOldRevision_then_changesArePopulated() throws Exception {
        File gitDir = new File(".");
        GitClient client = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using("git").getClient();

        ObjectId git261 = client.revParse(GIT_2_6_1_TAG);
        AbstractGitSCMSource.SCMRevisionImpl rev261 =
                new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead("origin"), git261.getName());
        GitSCMFileSystem gitPlugin261FS = new GitSCMFileSystem(client, "origin", git261.getName(), rev261);

        ObjectId git260 = client.revParse(GIT_2_6_0_TAG);
        AbstractGitSCMSource.SCMRevisionImpl rev260 =
                new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead("origin"), git260.getName());

        assertThat(git260, not(is(git261)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(gitPlugin261FS.changesSince(rev260, out));
        assertThat(out.toString(), containsString("prepare release git-2.6.1"));
    }

    @Test
    public void given_filesystem_when_askingChangesSinceNewRevision_then_changesArePopulatedButEmpty() throws Exception {
        File gitDir = new File(".");
        GitClient client = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using("git").getClient();

        ObjectId git260 = client.revParse(GIT_2_6_0_TAG);
        AbstractGitSCMSource.SCMRevisionImpl rev260 =
                new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead("origin"), git260.getName());
        GitSCMFileSystem gitPlugin260FS = new GitSCMFileSystem(client, "origin", git260.getName(), rev260);

        ObjectId git261 = client.revParse(GIT_2_6_1_TAG);
        AbstractGitSCMSource.SCMRevisionImpl rev261 =
                new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead("origin"), git261.getName());
        GitSCMFileSystem gitPlugin261FS =
                new GitSCMFileSystem(client, "origin", git261.getName(), rev261);
        assertEquals(git261.getName(), gitPlugin261FS.getRevision().getHash());

        assertThat(git261, not(is(git260)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(gitPlugin260FS.changesSince(rev261, out));
        assertThat(out.toString(), is(""));
    }

    /** inline ${@link hudson.Functions#isWindows()} to prevent a transient remote classloader issue */
    private boolean isWindows() {
        return java.io.File.pathSeparatorChar==';';
    }
}
