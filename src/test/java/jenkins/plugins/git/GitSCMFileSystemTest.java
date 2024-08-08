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
import hudson.plugins.git.GitException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import hudson.plugins.git.UserRemoteConfig;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;

import org.eclipse.jgit.lib.Constants;
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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import jenkins.plugins.git.GitSCMFileSystem.BuilderImpl.HeadNameResult;

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
     * $ git fetch --tags https://github.com/jenkinsci/git-plugin
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
                gitCmd.run("fetch", "--tags", "https://github.com/jenkinsci/git-plugin");
                tagId = client.revParse(tag); /* throws if tag not available */
            }
        }
    }

    @Test
    @Deprecated // Testing deprecated GitSCMSource constructor
    public void ofSource_Smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMFileSystem fs = SCMFileSystem.of(source, new GitBranchSCMHead("dev"));
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
    @Deprecated // Testing deprecated GitSCMSource constructor
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

    @Test
    @Deprecated // Testing deprecated GitSCMSource constructor
    public void ofSourceRevision_GitBranchSCMHead() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMRevision revision = source.fetch(new GitBranchSCMHead("dev"), null);
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMFileSystem fs = SCMFileSystem.of(source, new GitBranchSCMHead("dev"), revision);
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
        SCMFileSystem fs = SCMFileSystem.of(r.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo.toString(), null), Collections.singletonList(new BranchSpec("*/bug/JENKINS-42817")), null, null, Collections.emptyList()));
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

    @Issue("JENKINS-57587")
    @Test
    public void wildcardBranchNameCausesNPE() throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "contents-for-npe-when-branch-name-is-asterisk");
        sampleRepo.git("commit", "--all", "--message=npe-when-branch-name-is-asterisk");
        /* Non-existent branch names like 'not-a-branch', will fail
         * the build early with a message that the remote ref cannot
         * be found.  Branch names that are valid portions of a
         * refspec like '*' do not fail the build early but generate a
         * null pointer exception when trying to resolve the branch
         * name in the GitSCMFileSystem constructor.
         */
        SCMFileSystem fs = SCMFileSystem.of(r.createFreeStyleProject(),
                                            new GitSCM(GitSCM.createRepoList(sampleRepo.toString(), null),
                                                       Collections.singletonList(new BranchSpec("*")), // JENKINS-57587 issue here
                                                       null, null,
                                                       Collections.emptyList()));
        assertThat("Wildcard branch name '*' resolved to a specific checkout unexpectedly", fs, is(nullValue()));
    }

    @Test
    @Deprecated // Testing deprecated GitSCMSource constructor
    public void lastModified_Smokes() throws Exception {
        if (isWindows()) { // Windows file system last modify dates not trustworthy
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMRevision revision = source.fetch(new GitBranchSCMHead("dev"), null);
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        long fileSystemAllowedOffset = 1500;
        if ("OpenBSD".equals(System.getProperty("os.name"))) {
            fileSystemAllowedOffset = 2 * fileSystemAllowedOffset;
        }
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"), revision);
        long currentTime = System.currentTimeMillis();
        long lastModified = fs.lastModified();
        assertThat(lastModified / 1000L, greaterThanOrEqualTo((currentTime - fileSystemAllowedOffset) / 1000L));
        assertThat(lastModified / 1000L, lessThanOrEqualTo((currentTime + fileSystemAllowedOffset) / 1000L));
        SCMFile file = fs.getRoot().child("file");
        currentTime = System.currentTimeMillis();
        lastModified = file.lastModified();
        assertThat(lastModified / 1000L, greaterThanOrEqualTo((currentTime - fileSystemAllowedOffset) / 1000L));
        assertThat(lastModified / 1000L, lessThanOrEqualTo((currentTime + fileSystemAllowedOffset) / 1000L));
    }

    @Test
    @Deprecated // Testing deprecated GitSCMSource constructor
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
    @Deprecated // Testing deprecated GitSCMSource constructor
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
        Set<String> names = new TreeSet<>();
        SCMFile file = null;
        SCMFile file2 = null;
        SCMFile dir = null;
        for (SCMFile f: children) {
            names.add(f.getName());
            switch (f.getName()) {
                case "file":
                    file = f;
                    break;
                case "file2":
                    file2 = f;
                    break;
                case "dir":
                    dir = f;
                    break;
                default:
                    break;
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

    @Test
    public void create_SCMFileSystem_from_tag() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.mkdirs("dir/subdir");
        sampleRepo.git("mv", "file", "dir/subdir/file");
        sampleRepo.write("dir/subdir/file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "v1.0");
        SCMFileSystem fs = SCMFileSystem.of(r.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo.toString(), null), Collections.singletonList(new BranchSpec("refs/tags/v1.0")), null, null, Collections.emptyList()));
        assertEquals("modified", getFileContent(fs, "dir/subdir/file", "modified"));
    }

    public String getFileContent(SCMFileSystem fs, String path, String expectedContent) throws IOException, InterruptedException {
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
        return file.contentAsString();
    }

    public static List<UserRemoteConfig> createRepoListWithRefspec(String url, String refspec) {
        List<UserRemoteConfig> repoList = new ArrayList<>();
        repoList.add(new UserRemoteConfig(url, null, refspec, null));
        return repoList;
    }

    @Test
    public void create_SCMFileSystem_from_commit() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.mkdirs("dir/subdir");
        sampleRepo.git("mv", "file", "dir/subdir/file");
        sampleRepo.write("dir/subdir/file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        String modifiedCommit = sampleRepo.head();
        sampleRepo.write("dir/subdir/file", "modified again");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMFileSystem fs = SCMFileSystem.of(r.createFreeStyleProject(), new GitSCM(createRepoListWithRefspec(sampleRepo.toString(), "dev"), Collections.singletonList(new BranchSpec(modifiedCommit)), null, null, Collections.emptyList()));
        assertEquals(modifiedCommit, fs.getRevision().toString());
        assertEquals("modified", getFileContent(fs, "dir/subdir/file", "modified"));
    }

    @Test
    public void create_SCMFileSystem_from_FETCH_HEAD() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.mkdirs("dir/subdir");
        sampleRepo.git("mv", "file", "dir/subdir/file");
        sampleRepo.write("dir/subdir/file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMFileSystem fs = SCMFileSystem.of(r.createFreeStyleProject(), new GitSCM(createRepoListWithRefspec(sampleRepo.toString(), "dev"), Collections.singletonList(new BranchSpec(Constants.FETCH_HEAD)), null, null, Collections.emptyList()));
        assertEquals("modified", getFileContent(fs, "dir/subdir/file", "modified"));
    }

    @Issue("JENKINS-52964")
    @Test
    public void filesystem_supports_descriptor() throws Exception {
        SCMSourceDescriptor descriptor = r.jenkins.getDescriptorByType(GitSCMSource.DescriptorImpl.class);
        assertTrue(SCMFileSystem.supports(descriptor));
    }

    @Issue("JENKINS-42971")
    @Test
    public void calculate_head_name_with_env() throws Exception {
        String remote = "origin";
        HeadNameResult result1 = HeadNameResult.calculate(new BranchSpec("${BRANCH}"), null, null, new EnvVars("BRANCH", "master-a"), remote);
        assertEquals("refs/remotes/origin/master-a", result1.remoteHeadName);
        assertTrue(result1.refspec.startsWith("+" + Constants.R_HEADS));

        HeadNameResult result2 = HeadNameResult.calculate(new BranchSpec("${BRANCH}"), null, null, new EnvVars("BRANCH", "refs/heads/master-b"), remote);
        assertEquals("refs/remotes/origin/master-b", result2.remoteHeadName);
        assertTrue(result2.refspec.startsWith("+" + Constants.R_HEADS));

        HeadNameResult result3 = HeadNameResult.calculate(new BranchSpec("refs/heads/${BRANCH}"), null, null, new EnvVars("BRANCH", "master-c"), remote);
        assertEquals("refs/remotes/origin/master-c", result3.remoteHeadName);
        assertTrue(result3.refspec.startsWith("+" + Constants.R_HEADS));

        HeadNameResult result4 = HeadNameResult.calculate(new BranchSpec("${BRANCH}"), null, null, null, remote);
        assertEquals("refs/remotes/origin/${BRANCH}", result4.remoteHeadName);
        assertTrue(result4.refspec.startsWith("+" + Constants.R_HEADS));

        HeadNameResult result5 = HeadNameResult.calculate(new BranchSpec("*/${BRANCH}"), null, null, new EnvVars("BRANCH", "master-d"), remote);
        assertEquals("refs/remotes/origin/master-d", result5.remoteHeadName);
        assertTrue(result5.refspec.startsWith("+" + Constants.R_HEADS));

        HeadNameResult result6 = HeadNameResult.calculate(new BranchSpec("*/master-e"), null, null, new EnvVars("BRANCH", "dummy"), remote);
        assertEquals("refs/remotes/origin/master-e", result6.remoteHeadName);
        assertTrue(result6.refspec.startsWith("+" + Constants.R_HEADS));
    }

    @Test
    public void calculate_head_name() throws Exception {
        String remote = "origin";
        HeadNameResult result1 = HeadNameResult.calculate(new BranchSpec("branch"), null, null, null, remote);
        assertEquals("refs/remotes/origin/branch", result1.remoteHeadName);
        assertEquals("+refs/heads/branch:refs/remotes/origin/branch", result1.refspec);

        HeadNameResult result2 = HeadNameResult.calculate(new BranchSpec("refs/heads/branch"), null, null, null, remote);
        assertEquals("refs/remotes/origin/branch", result2.remoteHeadName);
        assertEquals("+refs/heads/branch:refs/remotes/origin/branch", result2.refspec);

        HeadNameResult result3 = HeadNameResult.calculate(new BranchSpec("refs/tags/my-tag"), null, null, null, remote);
        assertEquals("my-tag", result3.remoteHeadName);
        assertEquals("+refs/tags/my-tag:refs/tags/my-tag", result3.refspec);
    }

    @Test
    public void calculate_head_name_with_refspec_commit() throws Exception {
        String remote = "origin";
        String commit = "0123456789" + "0123456789" + "0123456789" + "0123456789";
        String branch = "branch";
        HeadNameResult result1 = HeadNameResult.calculate(new BranchSpec(commit), null, branch, null, remote);
        assertEquals(commit, result1.remoteHeadName);
        assertEquals(branch, result1.refspec);

        HeadNameResult result2 = HeadNameResult.calculate(new BranchSpec("${BRANCH}"), null, "${REFSPEC}",
                new EnvVars("BRANCH", commit, "REFSPEC", branch), remote);
        assertEquals(commit, result2.remoteHeadName);
        assertEquals(branch, result2.refspec);
    }

    @Test
    public void calculate_head_name_with_refspec_FETCH_HEAD() throws Exception {
        String remote = "origin";
        HeadNameResult result1 = HeadNameResult.calculate(new BranchSpec(Constants.FETCH_HEAD), null, "refs/changes/1/2/3", null, remote);
        assertEquals(Constants.FETCH_HEAD, result1.remoteHeadName);
        assertEquals("refs/changes/1/2/3", result1.refspec);

        HeadNameResult result2 = HeadNameResult.calculate(new BranchSpec("${BRANCH}"), null, "${REFSPEC}",
                new EnvVars("BRANCH", Constants.FETCH_HEAD, "REFSPEC", "refs/changes/1/2/3"), remote);
        assertEquals(Constants.FETCH_HEAD, result2.remoteHeadName);
        assertEquals("refs/changes/1/2/3", result2.refspec);
    }

    /* GitSCMFileSystem in git plugin 4.14.0 reported a null pointer
     * exception when the rev was non-null and the env was null. */
    @Issue("JENKINS-70158")
    @Test
    public void null_pointer_exception() throws Exception {
        File gitDir = new File(".");
        GitClient client = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using("git").getClient();
        ObjectId git260 = client.revParse(GIT_2_6_0_TAG);
        AbstractGitSCMSource.SCMRevisionImpl rev260 =
                new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead("origin"), git260.getName());
        HeadNameResult result1 = HeadNameResult.calculate(new BranchSpec("master-f"), rev260, null, null, "origin");
        assertEquals("refs/remotes/origin/master-f", result1.remoteHeadName);
        assertTrue(result1.refspec.startsWith("+" + Constants.R_HEADS));
    }

    /** inline ${@link hudson.Functions#isWindows()} to prevent a transient remote classloader issue */
    private boolean isWindows() {
        return java.io.File.pathSeparatorChar==';';
    }
}
