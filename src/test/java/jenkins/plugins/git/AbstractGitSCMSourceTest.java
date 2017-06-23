package jenkins.plugins.git;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCMRevisionState;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.util.StreamTaskListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.IgnoreOnPushNotificationTrait;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import static org.hamcrest.Matchers.*;

import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
public class AbstractGitSCMSourceTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule
    public GitSampleRepoRule sampleRepo2 = new GitSampleRepoRule();

    // TODO AbstractGitSCMSourceRetrieveHeadsTest *sounds* like it would be the right place, but it does not in fact retrieve any heads!
    @Issue("JENKINS-37482")
    @Test
    public void retrieveHeads() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());
        // And reuse cache:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());
        sampleRepo.git("checkout", "-b", "dev2");
        sampleRepo.write("file", "modified again");
        sampleRepo.git("commit", "--all", "--message=dev2");
        // After changing data:
        assertEquals("[SCMHead{'dev'}, SCMHead{'dev2'}, SCMHead{'master'}]", source.fetch(listener).toString());
    }

    @Test
    public void retrieveHeadsRequiresBranchDiscovery() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[]", source.fetch(listener).toString());
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());
        // And reuse cache:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());
        sampleRepo.git("checkout", "-b", "dev2");
        sampleRepo.write("file", "modified again");
        sampleRepo.git("commit", "--all", "--message=dev2");
        // After changing data:
        assertEquals("[SCMHead{'dev'}, SCMHead{'dev2'}, SCMHead{'master'}]", source.fetch(listener).toString());
    }

    public static abstract class ActionableSCMSourceOwner extends Actionable implements SCMSourceOwner {

    }

    @Test
    public void retrievePrimaryHead_NotDuplicated() throws Exception {
        retrievePrimaryHead(false);
    }

    @Test
    public void retrievePrimaryHead_Duplicated() throws Exception {
        retrievePrimaryHead(true);
    }

    public void retrievePrimaryHead(boolean duplicatePrimary) throws Exception {
        sampleRepo.init();
        sampleRepo.write("file.txt", "");
        sampleRepo.git("add", "file.txt");
        sampleRepo.git("commit", "--all", "--message=add-empty-file");
        sampleRepo.git("checkout", "-b", "new-primary");
        sampleRepo.write("file.txt", "content");
        sampleRepo.git("add", "file.txt");
        sampleRepo.git("commit", "--all", "--message=add-file");
        if (duplicatePrimary) {
            // If more than one branch points to same sha1 as new-primary and the
            // command line git implementation is older than 2.8.0, then the guesser
            // for primary won't be able to choose between the two alternatives.
            // The next line illustrates that case with older command line git.
            sampleRepo.git("checkout", "-b", "new-primary-duplicate", "new-primary");
        }
        sampleRepo.git("checkout", "master");
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.git("symbolic-ref", "HEAD", "refs/heads/new-primary");

        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        ActionableSCMSourceOwner owner = Mockito.mock(ActionableSCMSourceOwner.class);
        when(owner.getSCMSource(source.getId())).thenReturn(source);
        when(owner.getSCMSources()).thenReturn(Collections.singletonList(source));
        source.setOwner(owner);
        TaskListener listener = StreamTaskListener.fromStderr();
        Map<String, SCMHead> headByName = new TreeMap<String, SCMHead>();
        for (SCMHead h: source.fetch(listener)) {
            headByName.put(h.getName(), h);
        }
        if (duplicatePrimary) {
            assertThat(headByName.keySet(), containsInAnyOrder("master", "dev", "new-primary", "new-primary-duplicate"));
        } else {
            assertThat(headByName.keySet(), containsInAnyOrder("master", "dev", "new-primary"));
        }
        List<Action> actions = source.fetchActions(null, listener);
        GitRemoteHeadRefAction refAction = null;
        for (Action a: actions) {
            if (a instanceof GitRemoteHeadRefAction) {
                refAction = (GitRemoteHeadRefAction) a;
                break;
            }
        }
        final boolean CLI_GIT_LESS_THAN_280 = !sampleRepo.gitVersionAtLeast(2, 8);
        if (duplicatePrimary && CLI_GIT_LESS_THAN_280) {
            assertThat(refAction, is(nullValue()));
        } else {
            assertThat(refAction, notNullValue());
            assertThat(refAction.getName(), is("new-primary"));
            when(owner.getAction(GitRemoteHeadRefAction.class)).thenReturn(refAction);
            when(owner.getActions(GitRemoteHeadRefAction.class)).thenReturn(Collections.singletonList(refAction));
            actions = source.fetchActions(headByName.get("new-primary"), null, listener);
        }

        PrimaryInstanceMetadataAction primary = null;
        for (Action a: actions) {
            if (a instanceof PrimaryInstanceMetadataAction) {
                primary = (PrimaryInstanceMetadataAction) a;
                break;
            }
        }
        if (duplicatePrimary && CLI_GIT_LESS_THAN_280) {
            assertThat(primary, is(nullValue()));
        } else {
            assertThat(primary, notNullValue());
        }
    }

    @Issue("JENKINS-31155")
    @Test
    public void retrieveRevision() throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "v1");
        sampleRepo.git("commit", "--all", "--message=v1");
        sampleRepo.git("tag", "v1");
        String v1 = sampleRepo.head();
        sampleRepo.write("file", "v2");
        sampleRepo.git("commit", "--all", "--message=v2"); // master
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "v3");
        sampleRepo.git("commit", "--all", "--message=v3"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of branches:
        assertEquals("v2", fileAt("master", run, source, listener));
        assertEquals("v3", fileAt("dev", run, source, listener));
        // Tags:
        assertEquals("v1", fileAt("v1", run, source, listener));
        // And commit hashes:
        assertEquals("v1", fileAt(v1, run, source, listener));
        assertEquals("v1", fileAt(v1.substring(0, 7), run, source, listener));
        // Nonexistent stuff:
        assertNull(fileAt("nonexistent", run, source, listener));
        assertNull(fileAt("1234567", run, source, listener));
        assertNull(fileAt("", run, source, listener));
        assertNull(fileAt("\n", run, source, listener));
        assertThat(source.fetchRevisions(listener), hasItems("master", "dev", "v1"));
        // we do not care to return commit hashes or other references
    }
    private String fileAt(String revision, Run<?,?> run, SCMSource source, TaskListener listener) throws Exception {
        SCMRevision rev = source.fetch(revision, listener);
        if (rev == null) {
            return null;
        } else {
            FilePath ws = new FilePath(run.getRootDir()).child("tmp-" + revision);
            source.build(rev.getHead(), rev).checkout(run, new Launcher.LocalLauncher(listener), ws, listener, null, SCMRevisionState.NONE);
            return ws.child("file").readToString();
        }
    }

    @Issue("JENKINS-37727")
    @Test
    public void pruneRemovesDeletedBranches() throws Exception {
        sampleRepo.init();

        /* Write a file to the master branch */
        sampleRepo.write("master-file", "master-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "master-file");
        sampleRepo.git("commit", "--message=master-branch-commit-message");

        /* Write a file to the dev branch */
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("dev-file", "dev-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "dev-file");
        sampleRepo.git("commit", "--message=dev-branch-commit-message");

        /* Fetch from sampleRepo */
        GitSCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());
        // And reuse cache:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());

        /* Create dev2 branch and write a file to it */
        sampleRepo.git("checkout", "-b", "dev2", "master");
        sampleRepo.write("dev2-file", "dev2-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "dev2-file");
        sampleRepo.git("commit", "--message=dev2-branch-commit-message");

        // Verify new branch is visible
        assertEquals("[SCMHead{'dev'}, SCMHead{'dev2'}, SCMHead{'master'}]", source.fetch(listener).toString());

        /* Delete the dev branch */
        sampleRepo.git("branch", "-D", "dev");

        /* Fetch and confirm dev branch was pruned */
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* CentOS 6 git version (1.7.1) doesn't prune on fetch */
            assertEquals("[SCMHead{'dev'}, SCMHead{'dev2'}, SCMHead{'master'}]", source.fetch(listener).toString());
        } else {
            assertEquals("[SCMHead{'dev2'}, SCMHead{'master'}]", source.fetch(listener).toString());
        }
    }

    @Test
    public void testSpecificRevisionBuildChooser() throws Exception {
        sampleRepo.init();

        /* Write a file to the master branch */
        sampleRepo.write("master-file", "master-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "master-file");
        sampleRepo.git("commit", "--message=master-branch-commit-message");

        /* Fetch from sampleRepo */
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new IgnoreOnPushNotificationTrait()));
        List<GitSCMExtension> extensions = new ArrayList<GitSCMExtension>();
        assertThat(source.getExtensions(), is(empty()));
        LocalBranch localBranchExtension = new LocalBranch("**");
        extensions.add(localBranchExtension);
        source.setExtensions(extensions);
        assertThat(source.getExtensions(), contains(
                allOf(
                        instanceOf(LocalBranch.class),
                        hasProperty("localBranch", is("**")
                        )
                )
        ));

        SCMHead head = new SCMHead("master");
        SCMRevision revision = new AbstractGitSCMSource.SCMRevisionImpl(head, "beaded4deed2bed4feed2deaf78933d0f97a5a34");

        // because we are ignoring push notifications we also ignore commits
        extensions.add(new IgnoreNotifyCommit());

        /* Check that BuildChooserSetting not added to extensions by build() */
        GitSCM scm = (GitSCM) source.build(head);
        assertThat(scm.getExtensions(), containsInAnyOrder(
                allOf(
                        instanceOf(LocalBranch.class),
                        hasProperty("localBranch", is("**")
                        )
                ),
                // no BuildChooserSetting
                instanceOf(IgnoreNotifyCommit.class),
                instanceOf(GitSCMSourceDefaults.class)
        ));

        /* Check that BuildChooserSetting has been added to extensions by build() */
        GitSCM scmRevision = (GitSCM) source.build(head, revision);
        assertThat(scmRevision.getExtensions(), containsInAnyOrder(
                allOf(
                        instanceOf(LocalBranch.class),
                        hasProperty("localBranch", is("**")
                        )
                ),
                instanceOf(BuildChooserSetting.class),
                instanceOf(IgnoreNotifyCommit.class),
                instanceOf(GitSCMSourceDefaults.class)
        ));
    }


    @Test
    public void testCustomRemoteName() throws Exception {
        sampleRepo.init();

        GitSCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "upstream", null, "*", "", true);
        SCMHead head = new SCMHead("master");
        GitSCM scm = (GitSCM) source.build(head);
        List<UserRemoteConfig> configs = scm.getUserRemoteConfigs();
        assertEquals(1, configs.size());
        UserRemoteConfig config = configs.get(0);
        assertEquals("upstream", config.getName());
        assertEquals("+refs/heads/*:refs/remotes/upstream/*", config.getRefspec());
    }

    @Test
    public void testCustomRefSpecs() throws Exception {
        sampleRepo.init();

        GitSCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", null, "+refs/heads/*:refs/remotes/origin/*          +refs/merge-requests/*/head:refs/remotes/origin/merge-requests/*", "*", "", true);
        SCMHead head = new SCMHead("master");
        GitSCM scm = (GitSCM) source.build(head);
        List<UserRemoteConfig> configs = scm.getUserRemoteConfigs();

        assertEquals(1, configs.size());

        UserRemoteConfig config = configs.get(0);
        assertEquals("origin", config.getName());
        assertEquals("+refs/heads/*:refs/remotes/origin/* +refs/merge-requests/*/head:refs/remotes/origin/merge-requests/*", config.getRefspec());
    }
}
