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
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import hudson.plugins.git.UserRemoteConfig;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
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
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static hudson.Functions.isWindows;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
@WithJenkins
@WithGitSampleRepo
class GitSCMFileSystemTest {

    private static JenkinsRule r;

    private GitSampleRepoRule sampleRepo;

    private static final String GIT_2_6_0_TAG = "git-2.6.0";
    private static final String GIT_2_6_1_TAG = "git-2.6.1";

    /* This test requires the tag git-2.6.1 and git-2.6.0. If you're working from a
     * forked copy of the repository and your fork was created before the
     * git-2.6.1 plugin release, you may not have that tag in your fork.
     * If you do not have that tag, you will need to include that tag in
     * your fork.  You can do that with the commands:
     *
     * $ git fetch --tags https://github.com/jenkinsci/git-plugin
     * $ git push --tags origin
     */
    @BeforeAll
    static void beforeAll(JenkinsRule rule) throws Exception {
        r = rule;

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

    @BeforeEach
    void beforeEach(GitSampleRepoRule repo) {
        sampleRepo = repo;
    }

    // Testing deprecated GitSCMSource constructor
    @Test
    @Deprecated
    void ofSource_Smokes() throws Exception {
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

    // Testing deprecated GitSCMSource constructor
    @Test
    @Deprecated
    void ofSourceRevision() throws Exception {
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

    // Testing deprecated GitSCMSource constructor
    @Test
    @Deprecated
    void ofSourceRevision_GitBranchSCMHead() throws Exception {
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
    void slashyBranches() throws Exception {
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
    void wildcardBranchNameCausesNPE() throws Exception {
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

    // Testing deprecated GitSCMSource constructor
    @Test
    @Deprecated
    void lastModified_Smokes() throws Exception {
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
        long fileSystemAllowedOffset = 2500; // Sometimes a busy file system is offset more than 1500 ms
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

    // Testing deprecated GitSCMSource constructor
    @Test
    @Deprecated
    void directoryTraversal() throws Exception {
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

    // Testing deprecated GitSCMSource constructor
    @Test
    @Deprecated
    void mixedContent() throws Exception {
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
    void given_filesystem_when_askingChangesSinceSameRevision_then_changesAreEmpty() throws Exception {
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
    void given_filesystem_when_askingChangesSinceOldRevision_then_changesArePopulated() throws Exception {
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
    void given_filesystem_when_askingChangesSinceNewRevision_then_changesArePopulatedButEmpty() throws Exception {
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
    void create_SCMFileSystem_from_tag() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.mkdirs("dir/subdir");
        sampleRepo.git("mv", "file", "dir/subdir/file");
        sampleRepo.write("dir/subdir/file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "v1.0");
        SCMFileSystem fs = SCMFileSystem.of(r.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo.toString(), null), Collections.singletonList(new BranchSpec("refs/tags/v1.0")), null, null, Collections.emptyList()));
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

    @Issue("JENKINS-52964")
    @Test
    void filesystem_supports_descriptor() throws Exception {
        SCMSourceDescriptor descriptor = r.jenkins.getDescriptorByType(GitSCMSource.DescriptorImpl.class);
        assertTrue(SCMFileSystem.supports(descriptor));
    }

    @Issue("JENKINS-42971")
    @Test
    void calculate_head_name_with_env() throws Exception {
        GitSCMFileSystem.BuilderImpl.HeadNameResult result1 = GitSCMFileSystem.BuilderImpl.HeadNameResult.calculate(new BranchSpec("${BRANCH}"), null,
                new EnvVars("BRANCH", "master-a"));
        assertEquals("master-a", result1.headName);
        assertEquals(Constants.R_HEADS, result1.prefix);

        GitSCMFileSystem.BuilderImpl.HeadNameResult result2 = GitSCMFileSystem.BuilderImpl.HeadNameResult.calculate(new BranchSpec("${BRANCH}"), null,
                new EnvVars("BRANCH", "refs/heads/master-b"));
        assertEquals("master-b", result2.headName);
        assertEquals(Constants.R_HEADS, result2.prefix);

        GitSCMFileSystem.BuilderImpl.HeadNameResult result3 = GitSCMFileSystem.BuilderImpl.HeadNameResult.calculate(new BranchSpec("refs/heads/${BRANCH}"), null,
                new EnvVars("BRANCH", "master-c"));
        assertEquals("master-c", result3.headName);
        assertEquals(Constants.R_HEADS, result3.prefix);

        GitSCMFileSystem.BuilderImpl.HeadNameResult result4 = GitSCMFileSystem.BuilderImpl.HeadNameResult.calculate(new BranchSpec("${BRANCH}"), null,
                null);
        assertEquals("${BRANCH}", result4.headName);
        assertEquals(Constants.R_HEADS, result4.prefix);

        GitSCMFileSystem.BuilderImpl.HeadNameResult result5 = GitSCMFileSystem.BuilderImpl.HeadNameResult.calculate(new BranchSpec("*/${BRANCH}"), null,
                new EnvVars("BRANCH", "master-d"));
        assertEquals("master-d", result5.headName);
        assertEquals(Constants.R_HEADS, result5.prefix);

        GitSCMFileSystem.BuilderImpl.HeadNameResult result6 = GitSCMFileSystem.BuilderImpl.HeadNameResult.calculate(new BranchSpec("*/master-e"), null,
                new EnvVars("BRANCH", "dummy"));
        assertEquals("master-e", result6.headName);
        assertEquals(Constants.R_HEADS, result6.prefix);
    }

    /* GitSCMFileSystem in git plugin 4.14.0 reported a null pointer
     * exception when the rev was non-null and the env was null. */
    @Issue("JENKINS-70158")
    @Test
    void null_pointer_exception() throws Exception {
        File gitDir = new File(".");
        GitClient client = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using("git").getClient();
        ObjectId git260 = client.revParse(GIT_2_6_0_TAG);
        AbstractGitSCMSource.SCMRevisionImpl rev260 =
                new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead("origin"), git260.getName());
        GitSCMFileSystem.BuilderImpl.HeadNameResult result1 = GitSCMFileSystem.BuilderImpl.HeadNameResult.calculate(new BranchSpec("master-f"), rev260, null);
        assertEquals("master-f", result1.headName);
        assertEquals(Constants.R_HEADS, result1.prefix);
    }

    /** Helper for test(s) below. Populates {@link #sampleRepo} with
     *  a bare git repository which has a "shallow/master" branch
     *  and a forged "master" branch, both with a git snapshot history
     *  one commit deep. They differ in metadata (one is shallow,
     *  another is complete as far as the git index is concerned).
     *  No remote repository is registered as an "origin" of this one.
     */
    void prepare_bare_shallow_origin() throws Exception {
        /* Populate a bare repository into sampleRepo with a shallow
         * fetch from an existing repo. Unlike `git clone --depth...`
         * this routine can be repeated to provide a snapshot of
         * multiple git branches or tags into e.g. generated Jenkins
         * containers, tailored for a purpose like offline automation.
         */
        File gitDir = new File(".");
        GitClient client = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using("git").getClient();

        sampleRepo.initBare();

        // Here we parameterize branchNameOriginal more due to the fact
        // that in e.g. automated Jenkins CI builds a PR source branch
        // of git-plugin might be the only named ref checked out in the
        // build workspace, and so the run-time would not necessarily
        // know about a "master" to fetch. We can still name the branch
        // made from a FETCH_HEAD in the sampleRepo replica whatever we
        // like (e.g. branchName="master" as expected by test cases).
        String branchNameOriginal = null;
        try {
            // Pick any, actually
            for (Branch b : client.getBranches()) {
                branchNameOriginal = b.getName();
                break;
            }
        } catch (Exception ignored) {
            // no-op, fall back to master below
            ignored.printStackTrace();
        }

        if (branchNameOriginal == null) {
            branchNameOriginal = "master";
        }

        sampleRepo.git("fetch", "--depth=1", gitDir.getAbsolutePath(), branchNameOriginal);

        // NOTE: Code below (after the initial "fetch") is repeatable
        // if multiple branches should be provided via shallow replicas.
        // Our tests expect to see a "master" so we name whatever we
        // did fetch like that.
        String branchName = "master";

        // The fetch (if successful) updated the local repository index,
        // but did not record any symbolic references like branch names.
        // Stash that commit as a branch on the side, and forge another
        // branch with the original name (but necessarily different hash)
        // which is completely responsible for its own history per metadata.
        sampleRepo.git("branch", "shallow/" + branchName, "FETCH_HEAD");

        // Original hash (refers to shallow metadata):
        String hashOriginal = sampleRepo.gitOutput(false,
                "rev-parse", "shallow/" + branchName);
        if (hashOriginal != null)
            hashOriginal = hashOriginal.lines().toList().get(0);
        assertNotNull(hashOriginal);
        assertNotEquals("", hashOriginal);

        // Same content as if it is the one and only commit to know about
        // in the git data tree:
        String hashTreeContent = sampleRepo.gitOutput(false,
                "rev-parse", "shallow/" + branchName + "^{tree}");
        if (hashTreeContent != null)
            hashTreeContent = hashTreeContent.lines().toList().get(0);
        assertNotNull(hashTreeContent);
        assertNotEquals("", hashTreeContent);

        // Use that tree content to create a new commit:
        String hashNewTip = sampleRepo.gitOutput(false,
                "commit-tree", hashTreeContent,
                "-m", "Shallow snapshot of " + branchName + " at " + hashOriginal);
        if (hashNewTip != null)
            hashNewTip = hashNewTip.lines().toList().get(0);
        assertNotNull(hashNewTip);
        assertNotEquals("", hashNewTip);

        // Now there is a symbolic name for "master" pointing to
        // a commit in the index. Note that by construct of the
        // offline source repo, there is no "origin" and so no
        // symbolic "refs/heads/origin/master", for example.
        // This is to not be confused with clones in Jenkins git
        // cache or in ultimate jobs, which could refer to this
        // bare repo as their "origin".
        sampleRepo.git("update-ref", "refs/heads/" + branchName, hashNewTip);
    }

    /** By default, git has a problem cloning from a shallow repository
     *  as it does not know how to handle the history responsibly.
     *  There are flags to let it trust the shallow origin "as is",
     *  but the plugin currently does not seem to use that feature.<br/>
     *
     *  As a result, repos cloned from this bare repo will be
     *  semi-instantiated with present index files but absent
     *  symbolic references.<br/>
     *
     *  The test is to verify that the plugin can handle such repos
     *  with a reasonable error rather than an NPE somewhere in its
     *  call stack (bug present in git-5.10.1).<br/>
     */
    @Test
    void handle_bare_shallow_local_origin() throws Throwable {
        GitSampleRepoRule cloneRepo = new GitSampleRepoRule();
        cloneRepo.before();

        try {
            prepare_bare_shallow_origin();

            // See if we can clone the incomplete history
            cloneRepo.git("clone",
                    "-b", "shallow/master",
                    sampleRepo.getRoot().toString(),
                    cloneRepo.getRoot().toString());

            cloneRepo.git("remote", "-v");
            cloneRepo.git("branch", "-a");

            String gitLog = cloneRepo.gitOutput(false, "log", "--oneline");
            assertNotNull(gitLog);
            assertNotEquals(0, gitLog.length());
            assertEquals(1, gitLog.lines().toList().size());

            // Do something like what "Pipeline from SCM" does;
            // with git-5.10.1 this already fails (because git
            // did not create symbolic ref for the branch name):
            GitSCM scmFromOrigin = new GitSCM(
                    List.of(new UserRemoteConfig(
                            sampleRepo.getRoot().toString(),
                            null,
                            null,
                            null
                    )),
                    List.of(new BranchSpec("refs/heads/shallow/master")),
                    false,
                    Collections.emptyList(),
                    null,
                    null,
                    Collections.emptyList()
            );
            assertNotNull(scmFromOrigin);

            List<BranchSpec> branches = scmFromOrigin.getBranches();
            assertNotNull(branches);
            assertNotEquals(0, branches.size());
            branches.forEach(b -> {
                assertNotNull(b.getName());
            });

            GitSCM scmFromClone = new GitSCM(
                    List.of(new UserRemoteConfig(
                            cloneRepo.getRoot().toString(),
                            null,
                            null,
                            null
                    )),
                    List.of(new BranchSpec("refs/heads/shallow/master")),
                    false,
                    Collections.emptyList(),
                    null,
                    null,
                    Collections.emptyList()
            );
            assertNotNull(scmFromClone);

            branches = scmFromClone.getBranches();
            assertNotNull(branches);
            assertNotEquals(0, branches.size());
            branches.forEach(b -> {
                assertNotNull(b.getName());
            });

            if (r != null) {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pipeline-from-git-shallow");
                // This repo is used as test subject and has *some* Jenkinsfile;
                // we don't plan to fully run that job, but it must pass git checkout
                p.setDefinition(new CpsScmFlowDefinition(scmFromOrigin, "Jenkinsfile"));

                // This is the code path used by Pipeline lightweight checkout
                // with the bare repo used as origin git source for the pipeline.
                // This is actually the point where the test failed when the
                // research for https://github.com/jenkinsci/git-plugin/pull/3988
                // was started, with broken plugin versions (5.10.1 and older):
                //   java.lang.NullPointerException: Cannot invoke
                //       "org.eclipse.jgit.lib.Ref.getObjectId()" because the return
                //       value of "org.eclipse.jgit.lib.Repository.findRef(String)" is null
                // An intermediate fix reported a meaningful message instead:
                //   java.io.IOException: Expected ref refs/remotes/origin/shallow/master
                //       was not created by preceding git fetch
                // The fix applied here retries the fetch through JGit (which does not
                // refuse to update refs that would introduce a new shallow root), so
                // this should now succeed and return a usable filesystem.
                SCMFileSystem fs =
                        SCMFileSystem.of(r.jenkins.getItemByFullName("pipeline-from-git-shallow"), scmFromOrigin, null);
                assertNotNull(fs);

                // This may produce an NPE that we are looking for
                // (to confirm the problem with initial code and the
                // lack of one after the fix) in the job context,
                // assuming we have miraculously survived till now.
                WorkflowRun b = null;
                Result res = null;
                try {
                    b = p.scheduleBuild2(0).waitForStart();
                    r.waitForCompletion(b);
                    res = b.getResult();
                } catch (Throwable t) {
                    // We do not care at this point that the job failed,
                    // as it may be due to the shallow clone or lack of
                    // the buildPlugin() step defined by Jenkins CI farm.
                    t.printStackTrace();
                }

                assertNotNull(res);
                r.assertLogNotContains("java.lang.NullPointerException: ", b);

                r.assertLogContains("Cloning repository", b);
                r.assertLogContains("Fetching upstream changes from", b);

                // We probably fail at this one until shallow checkout is fixed.
                try {
                    r.assertLogContains("Checking out Revision", b);
                    r.assertLogContains("Start of Pipeline", b);
                    r.assertLogContains("End of Pipeline", b);
                } catch (Throwable t) {
                    // Troubles are expected at the moment, this test confirms them
                    t.printStackTrace();

                    // NOTE: The plugin fix applied so far is to throw
                    //  a meaningful message instead of the unhelpful
                    //  NPE (when lightweight code path is used).
                    //  There is no checkout still.
                    //  Here we pass the actual git checkout path.
                    r.assertLogContains("Couldn't find any revision to build. Verify the repository and branch configuration for this job", b);
                }
            }
        } finally {
            cloneRepo.after();
        }
    }

    /** This test is to verify that unlike the setup crafted in
     *  {@link #handle_bare_shallow_local_origin}, the equivalent
     *  branch name with a complete history (albeit also a single
     *  commit deep) just works as far as git tooling and this
     *  plugin are concerned.
     */
    @Test
    void handle_bare_complete_local_origin() throws Throwable {
        GitSampleRepoRule cloneRepo = new GitSampleRepoRule();
        cloneRepo.before();

        try {
            prepare_bare_shallow_origin();

            // Make sure we can clone the complete history
            cloneRepo.git("clone",
                    "-b", "master",
                    sampleRepo.getRoot().toString(),
                    cloneRepo.getRoot().toString());

            cloneRepo.git("remote", "-v");
            cloneRepo.git("branch", "-a");

            String gitLog = cloneRepo.gitOutput(false, "log", "--oneline");
            assertNotNull(gitLog);
            assertNotEquals(0, gitLog.length());
            assertEquals(1, gitLog.lines().toList().size());

            // Do something like what "Pipeline from SCM" does;
            // with git-5.10.1 this already fails (because git
            // did not create symbolic ref for the branch name):
            GitSCM scmFromOrigin = new GitSCM(
                    List.of(new UserRemoteConfig(
                            sampleRepo.getRoot().toString(),
                            null,
                            null,
                            null
                    )),
                    List.of(new BranchSpec("refs/heads/master")),
                    false,
                    Collections.emptyList(),
                    null,
                    null,
                    Collections.emptyList()
            );
            assertNotNull(scmFromOrigin);

            List<BranchSpec> branches = scmFromOrigin.getBranches();
            assertNotNull(branches);
            assertNotEquals(0, branches.size());
            branches.forEach(b -> {
                assertNotNull(b.getName());
            });

            GitSCM scmFromClone = new GitSCM(
                    List.of(new UserRemoteConfig(
                            cloneRepo.getRoot().toString(),
                            null,
                            null,
                            null
                    )),
                    List.of(new BranchSpec("refs/heads/master")),
                    false,
                    Collections.emptyList(),
                    null,
                    null,
                    Collections.emptyList()
            );
            assertNotNull(scmFromClone);

            branches = scmFromClone.getBranches();
            assertNotNull(branches);
            assertNotEquals(0, branches.size());
            branches.forEach(b -> {
                assertNotNull(b.getName());
            });

            if (r != null) {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pipeline-from-git-complete");
                // This repo is used as test subject and has *some* Jenkinsfile;
                // we don't plan to fully run that job, but it must pass git checkout
                p.setDefinition(new CpsScmFlowDefinition(scmFromOrigin, "Jenkinsfile"));

                // This is the code path used by Pipeline lightweight checkout
                // with the bare repo used as origin git source for the pipeline
                SCMFileSystem fs = SCMFileSystem.of(
                        r.jenkins.getItemByFullName("pipeline-from-git-complete"),
                        scmFromOrigin,
                        null
                );
                assertNotNull(fs);

                // We do not anticipate grave problems with this build
                WorkflowRun b = null;
                Result res = null;
                try {
                    b = p.scheduleBuild2(0).waitForStart();
                    r.waitForCompletion(b);
                    res = b.getResult();
                } catch (Throwable t) {
                    // We do not care at this point that the job failed,
                    // as it may be due to lack of the buildPlugin() step
                    // defined by Jenkins CI farm.
                    t.printStackTrace();
                }

                assertNotNull(res);
                r.assertLogNotContains("java.lang.NullPointerException: ", b);

                r.assertLogContains("Cloning repository", b);
                r.assertLogContains("Fetching upstream changes from", b);
                r.assertLogContains("Checking out Revision", b);
                r.assertLogContains("Start of Pipeline", b);
                r.assertLogContains("End of Pipeline", b);
            }
        } finally {
            cloneRepo.after();
        }
    }
}
