/*
 * The MIT License
 *
 * Copyright (c) 2020 Nikolas Falco
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
package hudson.plugins.git.extensions.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.Functions;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.util.LogTaskListener;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.TestCliGitAPIImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PruneStaleTagTest {

    @Rule
    public TemporaryFolder fileRule = new TemporaryFolder();

    private TaskListener listener;
    private Run<?, ?> run;

    @Before
    public void setup() throws Exception {
        listener = new LogTaskListener(Logger.getLogger("prune tags"), Level.FINEST);

        run = mock(Run.class);
        when(run.getEnvironment(listener)).thenReturn(new EnvVars());
    }

    /*
     * Local tag       | Remote tag
     * ---------------------------------
     * exists - differ | exists - differ
     */
    @Test
    public void verify_local_tag_is_pruned_if_different_than_on_remote() throws Exception {
        File remoteRepo = fileRule.newFolder("remote");

        // create a remote repository without one tag
        GitClient remoteClient = initRepository(remoteRepo);
        String tagName = "tag";
        String tagComment = "tag comment";
        remoteClient.tag(tagName, tagComment);

        // clone remote repository to workspace
        GitClient localClient = cloneRepository(remoteRepo);
        localClient.config(GitClient.ConfigLevel.LOCAL, "commit.gpgsign", "false");
        localClient.config(GitClient.ConfigLevel.LOCAL, "tag.gpgSign", "false");
        GitSCM scm = new GitSCM(localClient.getRemoteUrl("origin"));
        PruneStaleTag extension = new PruneStaleTag(true);

        // get remote SHA1 for the tag
        String remoteTagHash = remoteClient.getTags().stream()
                .filter(t -> tagName.equals(t.getName()))
                .findFirst()
                .get()
                .getSHA1String();

        FileUtils.touch(new File(localClient.getWorkTree().getRemote(), "localTest"));
        localClient.add("localTest");
        localClient.commit("more commits");
        localClient.deleteTag(tagName);
        localClient.tag(tagName, tagComment);
        String localHashTag = localClient.getTags().stream()
                .filter(t -> tagName.equals(t.getName()))
                .findFirst()
                .get()
                .getSHA1String();
        Assert.assertNotEquals(
                "pre validation failed, local tag must not be the same than remote", remoteTagHash, localHashTag);

        extension.decorateFetchCommand(scm, run, localClient, listener, null);
        Assert.assertFalse("local tag differ from remote tag and is not pruned", localClient.tagExists(tagName));
    }

    /*
     * Local tag       | Remote tag
     * -------------------------------
     * not exists      | exists
     */
    @Test
    public void verify_do_nothing_when_remote_tag_do_not_exist_locally() throws Exception {
        File remoteRepo = fileRule.newFolder("remote");

        // create a remote repository without one tag
        GitClient remoteClient = initRepository(remoteRepo);

        // clone remote repository to workspace
        GitClient localClient = cloneRepository(remoteRepo);

        GitSCM scm = new GitSCM(localClient.getRemoteUrl("origin"));
        PruneStaleTag extension = new PruneStaleTag(true);

        // new tags should not be fetched
        String tagName = "tag";
        String tagComment = "tag comment";
        remoteClient.tag(tagName, tagComment);
        extension.decorateFetchCommand(scm, run, localClient, listener, null);
        Assert.assertFalse("new tags should not be fetched", localClient.tagExists(tagName));
    }

    /*
     * Local tag       | Remote tag
     * -------------------------------
     * exists - same   | exists - same
     */
    @Test
    public void verify_that_local_tag_is_not_pruned_when_exist_on_remote() throws Exception {
        File remoteRepo = fileRule.newFolder("remote");

        // create a remote repository without one tag
        GitClient remoteClient = initRepository(remoteRepo);
        String tagName = "tag";
        String tagComment = "tag comment";
        remoteClient.tag(tagName, tagComment);

        // clone remote repository to workspace
        GitClient localClient = cloneRepository(remoteRepo);

        GitSCM scm = new GitSCM(localClient.getRemoteUrl("origin"));
        PruneStaleTag extension = new PruneStaleTag(true);

        extension.decorateFetchCommand(scm, run, localClient, listener, null);
        Assert.assertTrue("local tags must not be pruned when exists on remote", localClient.tagExists(tagName));
    }

    /*
     * Local tag       | Remote tag
     * -------------------------------
     * exists          | not exists
     */
    @Test
    public void verify_that_local_tag_is_pruned_when_not_exist_on_remote() throws Exception {
        File remoteRepo = fileRule.newFolder("remote");

        // create a remote repository without one tag
        GitClient remoteClient = initRepository(remoteRepo);
        String tagName = "tag";
        String tagComment = "tag comment";
        remoteClient.tag(tagName, tagComment);

        // clone remote repository to workspace
        GitClient localClient = cloneRepository(remoteRepo);

        GitSCM scm = new GitSCM(localClient.getRemoteUrl("origin"));
        PruneStaleTag extension = new PruneStaleTag(true);

        // remove tag on remote, tag remains on local cloned repository
        remoteClient.deleteTag(tagName);
        extension.decorateFetchCommand(scm, run, localClient, listener, null);
        Assert.assertFalse("local tag has not been pruned", localClient.tagExists(tagName));
    }

    @Test
    public void verify_fetch_do_not_prune_local_branches() throws Exception {
        File remoteRepo = fileRule.newFolder("remote");

        // create a remote repository without one tag
        initRepository(remoteRepo);

        // clone remote repository to workspace
        GitClient localClient = cloneRepository(remoteRepo);

        // create a local branch that should not be pruned with tags
        String branchName = "localBranch";
        localClient.branch(branchName);

        String gitURL = remoteRepo.toURI().toString();
        GitSCM scm = new GitSCM(gitURL);
        PruneStaleTag extension = new PruneStaleTag(true);
        extension.decorateFetchCommand(scm, run, localClient, listener, null);

        Assert.assertTrue(
                "Local branches must not be pruned",
                localClient.getBranches().stream().anyMatch(b -> branchName.equals(b.getName())));
    }

    private GitClient newGitClient(File localRepo) {
        String gitExe = Functions.isWindows() ? "git.exe" : "git";
        GitClient localClient = new TestCliGitAPIImpl(gitExe, localRepo, listener, new EnvVars());
        return localClient;
    }

    private GitClient cloneRepository(File remoteRepository) throws Exception {
        File localRepo = fileRule.newFolder("local");
        GitClient localClient = newGitClient(localRepo);
        /*
         * Workaround because File.toURI.toURL returns always one slash after
         * file protocol. Git command on Unix always fails with 'ssh: Could not
         * resolve hostname file: nodename nor servname provided, or not known'
         */
        String gitURL = "file://" + remoteRepository.toURI().getPath();
        localClient.clone(gitURL, "origin", false, "+refs/heads/*:refs/remotes/origin/*");
        localClient.checkoutBranch("master", "refs/remotes/origin/master");
        return localClient;
    }

    private GitClient initRepository(File workspace) throws Exception {
        GitClient remoteClient = newGitClient(workspace);
        remoteClient.init();
        remoteClient.config(GitClient.ConfigLevel.LOCAL, "commit.gpgsign", "false");
        remoteClient.config(GitClient.ConfigLevel.LOCAL, "tag.gpgSign", "false");
        FileUtils.touch(new File(workspace, "test"));
        remoteClient.add("test");

        remoteClient.commit("initial commit");
        return remoteClient;
    }

    @Test
    public void testGetPruneTags() {
        PruneStaleTag pruneEnabled = new PruneStaleTag(true);
        assertThat(pruneEnabled.getPruneTags(), is(true));
    }

    @Test
    public void testGetPruneTagsDisabled() {
        PruneStaleTag pruneEnabled = new PruneStaleTag(false);
        assertThat(pruneEnabled.getPruneTags(), is(false));
    }

    @Test
    public void testEquals() {
        EqualsVerifier.forClass(PruneStaleTag.class).usingGetClass().verify();
    }

    @Test
    public void testHashCode() {
        PruneStaleTag enabledOne = new PruneStaleTag(true);
        PruneStaleTag enabledTwo = new PruneStaleTag(true);
        assertThat(enabledOne.equals(enabledTwo), is(true));
        assertThat(enabledTwo.equals(enabledOne), is(true));
        assertThat(enabledOne.hashCode(), is(enabledTwo.hashCode()));

        PruneStaleTag disabledOne = new PruneStaleTag(false);
        PruneStaleTag disabledTwo = new PruneStaleTag(false);
        assertThat(disabledOne.equals(disabledTwo), is(true));
        assertThat(disabledTwo.equals(disabledOne), is(true));
        assertThat(disabledOne.hashCode(), is(disabledTwo.hashCode()));

        // Not required by hashCode contract, expected in this case
        assertThat(enabledOne.hashCode(), not(is(disabledOne.hashCode())));
    }

    @Test
    public void testToString() {
        PruneStaleTag enabled = new PruneStaleTag(true);
        assertThat(enabled.toString(), is("PruneStaleTag { true }"));
    }

    @Test
    public void testToStringDisabled() {
        PruneStaleTag disabled = new PruneStaleTag(false);
        assertThat(disabled.toString(), is("PruneStaleTag { false }"));
    }
}
