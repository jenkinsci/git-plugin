package jenkins.plugins.git;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCMRevisionState;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.DiscoverOtherRefsTrait;
import jenkins.plugins.git.traits.IgnoreOnPushNotificationTrait;
import jenkins.plugins.git.traits.PruneStaleBranchTrait;
import jenkins.plugins.git.traits.TagDiscoveryTrait;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;

import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.TestJGitAPIImpl;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
public class AbstractGitSCMSourceTest {

    static final String GitBranchSCMHead_DEV_MASTER = "[GitBranchSCMHead{name='dev', ref='refs/heads/dev'}, GitBranchSCMHead{name='master', ref='refs/heads/master'}]";
    static final String GitBranchSCMHead_DEV_DEV2_MASTER = "[GitBranchSCMHead{name='dev', ref='refs/heads/dev'}, GitBranchSCMHead{name='dev2', ref='refs/heads/dev2'}, GitBranchSCMHead{name='master', ref='refs/heads/master'}]";

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule
    public GitSampleRepoRule sampleRepo2 = new GitSampleRepoRule();

    // TODO AbstractGitSCMSourceRetrieveHeadsTest *sounds* like it would be the right place, but it does not in fact retrieve any heads!
    @Issue("JENKINS-37482")
    @Test
    @Deprecated // Tests deprecated GitSCMSource constructor
    public void retrieveHeads() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());
        // And reuse cache:
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());
        sampleRepo.git("checkout", "-b", "dev2");
        sampleRepo.write("file", "modified again");
        sampleRepo.git("commit", "--all", "--message=dev2");
        // After changing data:
        assertEquals(GitBranchSCMHead_DEV_DEV2_MASTER, source.fetch(listener).toString());
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
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());
        // And reuse cache:
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());
        sampleRepo.git("checkout", "-b", "dev2");
        sampleRepo.write("file", "modified again");
        sampleRepo.git("commit", "--all", "--message=dev2");
        // After changing data:
        assertEquals(GitBranchSCMHead_DEV_DEV2_MASTER, source.fetch(listener).toString());
    }

    @Issue("JENKINS-46207")
    @Test
    public void retrieveHeadsSupportsTagDiscovery_ignoreTagsWithoutTagDiscoveryTrait() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "lightweight");
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=dev2");
        sampleRepo.git("tag", "-a", "annotated", "-m", "annotated");
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=dev3");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[]", source.fetch(listener).toString());
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());
        // And reuse cache:
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());
        sampleRepo.git("checkout", "-b", "dev2");
        sampleRepo.write("file", "modified again");
        sampleRepo.git("commit", "--all", "--message=dev2");
        // After changing data:
        assertEquals(GitBranchSCMHead_DEV_DEV2_MASTER, source.fetch(listener).toString());
    }

    @Issue("JENKINS-46207")
    @Test
    public void retrieveHeadsSupportsTagDiscovery_findTagsWithTagDiscoveryTrait() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev-commit-message", "--no-verify");
        long beforeLightweightTag = System.currentTimeMillis();
        sampleRepo.git("tag", "lightweight");
        long afterLightweightTag = System.currentTimeMillis();
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=dev2-commit-message", "--no-verify");
        long beforeAnnotatedTag = System.currentTimeMillis();
        sampleRepo.git("tag", "-a", "annotated", "-m", "annotated");
        long afterAnnotatedTag = System.currentTimeMillis();
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=dev3-commit-message", "--no-verify");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(new ArrayList<>());
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[]", source.fetch(listener).toString());
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        Set<SCMHead> scmHeadSet = source.fetch(listener);
        long now = System.currentTimeMillis();
        for (SCMHead scmHead : scmHeadSet) {
            if (scmHead instanceof GitTagSCMHead) {
                GitTagSCMHead tagHead = (GitTagSCMHead) scmHead;
                // FAT file system time stamps only resolve to 2 second boundary
                // EXT3 file system time stamps only resolve to 1 second boundary
                long fileTimeStampFuzz = isWindows() ? 2000L : 1000L;
                fileTimeStampFuzz = 12 * fileTimeStampFuzz / 10; // 20% grace for file system noise
                switch (scmHead.getName()) {
                    case "lightweight":
                        {
                            long timeStampDelta = afterLightweightTag - tagHead.getTimestamp();
                            assertThat(timeStampDelta, is(both(greaterThanOrEqualTo(0L)).and(lessThanOrEqualTo(afterLightweightTag - beforeLightweightTag + fileTimeStampFuzz))));
                            break;
                        }
                    case "annotated":
                        {
                            long timeStampDelta = afterAnnotatedTag - tagHead.getTimestamp();
                            assertThat(timeStampDelta, is(both(greaterThanOrEqualTo(0L)).and(lessThanOrEqualTo(afterAnnotatedTag - beforeAnnotatedTag + fileTimeStampFuzz))));
                            break;
                        }
                    default:
                        fail("Unexpected tag head '" + scmHead.getName() + "'");
                        break;
                }
            }
        }
        String expected = "[SCMHead{'annotated'}, GitBranchSCMHead{name='dev', ref='refs/heads/dev'}, SCMHead{'lightweight'}, GitBranchSCMHead{name='master', ref='refs/heads/master'}]";
        assertEquals(expected, scmHeadSet.toString());
        // And reuse cache:
        assertEquals(expected, source.fetch(listener).toString());
        sampleRepo.git("checkout", "-b", "dev2");
        sampleRepo.write("file", "modified again");
        sampleRepo.git("commit", "--all", "--message=dev2");
        // After changing data:
        expected = "[SCMHead{'annotated'}, GitBranchSCMHead{name='dev', ref='refs/heads/dev'}, GitBranchSCMHead{name='dev2', ref='refs/heads/dev2'}, SCMHead{'lightweight'}, GitBranchSCMHead{name='master', ref='refs/heads/master'}]";
        assertEquals(expected, source.fetch(listener).toString());
    }

    @Issue("JENKINS-46207")
    @Test
    public void retrieveHeadsSupportsTagDiscovery_onlyTagsWithoutBranchDiscoveryTrait() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "lightweight");
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=dev2");
        sampleRepo.git("tag", "-a", "annotated", "-m", "annotated");
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=dev3");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(new ArrayList<>());
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[]", source.fetch(listener).toString());
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new TagDiscoveryTrait()));
        assertEquals("[SCMHead{'annotated'}, SCMHead{'lightweight'}]", source.fetch(listener).toString());
        // And reuse cache:
        assertEquals("[SCMHead{'annotated'}, SCMHead{'lightweight'}]", source.fetch(listener).toString());
    }

    @Issue("JENKINS-45953")
    @Test
    public void retrieveRevisions() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "lightweight");
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=dev2");
        sampleRepo.git("tag", "-a", "annotated", "-m", "annotated");
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=dev3");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(new ArrayList<>());
        TaskListener listener = StreamTaskListener.fromStderr();
        assertThat(source.fetchRevisions(listener, null), hasSize(0));
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        assertThat(source.fetchRevisions(listener, null), containsInAnyOrder("dev", "master"));
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new TagDiscoveryTrait()));
        assertThat(source.fetchRevisions(listener, null), containsInAnyOrder("annotated", "lightweight"));
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        assertThat(source.fetchRevisions(listener, null), containsInAnyOrder("dev", "master", "annotated", "lightweight"));
    }

    @Issue("JENKINS-64803")
    @Test
    public void retrieveTags_folderScopedCredentials() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "lightweight");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        TaskListener listener = StreamTaskListener.fromStderr();

        // Create a Folder and add a folder credentials
        Folder f = r.jenkins.createProject(Folder.class, "test");
        Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
        CredentialsStore folderStore = null;
        for (CredentialsStore s : stores) {
            if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
                folderStore = s;
                break;
            }
        }
        assert folderStore != null;
        String fCredentialsId = "fcreds";
        StandardCredentials fCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
            fCredentialsId, "fcreds", "user", "password");
        folderStore.addCredentials(Domain.global(), fCredentials);
        folderStore.save();
        WorkflowJob p = f.createProject(WorkflowJob.class, "wjob");

        source.setTraits(new ArrayList<>());
        source.setCredentialsId(fCredentialsId);

        Git git = mock(Git.class, CALLS_REAL_METHODS);
        GitClient gitClient = spy(git.getClient());
        // Spy on GitClient methods
        try (MockedStatic<Git> gitMock = mockStatic(Git.class, CALLS_REAL_METHODS)) {
            gitMock.when(() -> Git.with(any(), any())).thenReturn(git);
            doReturn(gitClient).when(git).getClient();

            String className = "jenkins.plugins.git.AbstractGitSCMSourceTest";
            String testName = "retrieveTags_folderScopedCredentials";
            String flag = className + "." + testName + ".enabled";
            String defaultValue = "The source.fetch() unexpectedly modifies the git remote.origin.url in the working repo";
            /* If -Djenkins.plugins.git.AbstractGitSCMSourceTest.retrieveTags_folderScopedCredentials.enabled=true */
            if (!System.getProperty(flag, defaultValue).equals(defaultValue)) {
                /* The source.fetch() unexpectedly modifies the git remote.origin.url in the working repo */
                SCMRevision rev = source.fetch("lightweight", listener, p);
                assertThat(rev, notNullValue());
                assertThat(rev.getHead().toString(), equalTo("SCMHead{'lightweight'}"));
                verify(gitClient, times(0)).addDefaultCredentials(null);
                verify(gitClient, atLeastOnce()).addDefaultCredentials(fCredentials);
            }
        }
    }

    @Issue("JENKINS-47824")
    @Test
    public void retrieveByName() throws Exception {
        sampleRepo.init();
        String masterHash = sampleRepo.head();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        sampleRepo.git("tag", "v1");
        String v1Hash = sampleRepo.head();
        sampleRepo.write("file", "modified2");
        sampleRepo.git("commit", "--all", "--message=dev2");
        sampleRepo.git("tag", "-a", "v2", "-m", "annotated");
        String v2Hash = sampleRepo.head();
        sampleRepo.write("file", "modified3");
        sampleRepo.git("commit", "--all", "--message=dev3");
        String devHash = sampleRepo.head();
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(new ArrayList<>());

        TaskListener listener = StreamTaskListener.fromStderr();

        listener.getLogger().println("\n=== fetch('master') ===\n");
        SCMRevision rev = source.fetch("master", listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl)rev).getHash(), is(masterHash));
        listener.getLogger().println("\n=== fetch('dev') ===\n");
        rev = source.fetch("dev", listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl)rev).getHash(), is(devHash));
        listener.getLogger().println("\n=== fetch('v1') ===\n");
        rev = source.fetch("v1", listener, null);
        assertThat(rev, instanceOf(GitTagSCMRevision.class));
        assertThat(((GitTagSCMRevision)rev).getHash(), is(v1Hash));
        listener.getLogger().println("\n=== fetch('v2') ===\n");
        rev = source.fetch("v2", listener, null);
        assertThat(rev, instanceOf(GitTagSCMRevision.class));
        assertThat(((GitTagSCMRevision)rev).getHash(), is(v2Hash));

        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", masterHash);
        rev = source.fetch(masterHash, listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(masterHash));
        assertThat(rev.getHead().getName(), is("master"));

        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", masterHash.substring(0, 10));
        rev = source.fetch(masterHash.substring(0, 10), listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(masterHash));
        assertThat(rev.getHead().getName(), is("master"));

        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", devHash);
        rev = source.fetch(devHash, listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(devHash));
        assertThat(rev.getHead().getName(), is("dev"));

        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", devHash.substring(0, 10));
        rev = source.fetch(devHash.substring(0, 10), listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(devHash));
        assertThat(rev.getHead().getName(), is("dev"));

        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", v1Hash);
        rev = source.fetch(v1Hash, listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(v1Hash));

        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", v1Hash.substring(0, 10));
        rev = source.fetch(v1Hash.substring(0, 10), listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(v1Hash));

        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", v2Hash);
        rev = source.fetch(v2Hash, listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(v2Hash));

        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", v2Hash.substring(0, 10));
        rev = source.fetch(v2Hash.substring(0, 10), listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(v2Hash));

        String v2Tag = "refs/tags/v2";
        listener.getLogger().printf("%n=== fetch('%s') ===%n%n", v2Tag);
        rev = source.fetch(v2Tag, listener, null);
        assertThat(rev, instanceOf(AbstractGitSCMSource.SCMRevisionImpl.class));
        assertThat(((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash(), is(v2Hash));

    }

    public static abstract class ActionableSCMSourceOwner extends Actionable implements SCMSourceOwner {

    }

    @Test
    @Deprecated
    public void retrievePrimaryHead_NotDuplicated() throws Exception {
        retrievePrimaryHead(false);
    }

    @Test
    @Deprecated
    public void retrievePrimaryHead_Duplicated() throws Exception {
        retrievePrimaryHead(true);
    }

    @Deprecated // Calls deprecated GitSCMSource constructor
    private void retrievePrimaryHead(boolean duplicatePrimary) throws Exception {
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
        Map<String, SCMHead> headByName = new TreeMap<>();
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
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
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
        assertThat(source.fetchRevisions(listener, null), hasItems("master", "dev", "v1"));
        // we do not care to return commit hashes or other references
    }

    @Issue("JENKINS-48061")
    @Test
    public void retrieveRevision_nonHead() throws Exception {
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
        String v3 = sampleRepo.head();
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of non head revision:
        assertEquals("v3", fileAt(v3, run, source, listener));
    }

    @Issue("JENKINS-48061")
    @Test
    // @Ignore("At least file:// protocol doesn't allow fetching unannounced commits")
    public void retrieveRevision_nonAdvertised() throws Exception {
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
        String v3 = sampleRepo.head();
        sampleRepo.git("reset", "--hard", "HEAD^"); // dev, the v3 ref is eligible for GC but still fetchable
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of non head revision:
        // Fails with a file:// URL, do not assert
        // @Ignore("At least file:// protocol doesn't allow fetching unannounced commits")
        // assertEquals("v3", fileAt(v3, run, source, listener));
    }

    @Issue("JENKINS-48061")
    @Test
    public void retrieveRevision_customRef() throws Exception {
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
        String v3 = sampleRepo.head();
        sampleRepo.git("update-ref", "refs/custom/foo", v3); // now this is an advertised ref so cannot be GC'd
        sampleRepo.git("reset", "--hard", "HEAD^"); // dev
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(),
                new TagDiscoveryTrait(),
                new DiscoverOtherRefsTrait("refs/custom/foo")));
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of non head revision:
        assertEquals("v3", fileAt(v3, run, source, listener));
    }

    @Issue("JENKINS-48061")
    @Test
    public void retrieveRevision_customRef_descendant() throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "v1");
        sampleRepo.git("commit", "--all", "--message=v1");
        sampleRepo.git("tag", "v1");
        sampleRepo.write("file", "v2");
        sampleRepo.git("commit", "--all", "--message=v2"); // master
        sampleRepo.git("checkout", "-b", "dev");
        String v2 = sampleRepo.head();
        sampleRepo.write("file", "v3");
        sampleRepo.git("commit", "--all", "--message=v3"); // dev
        String v3 = sampleRepo.head();
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        sampleRepo.git("update-ref", "refs/custom/foo", v3); // now this is an advertised ref so cannot be GC'd
        sampleRepo.git("reset", "--hard", "HEAD~2"); // dev
        String dev = sampleRepo.head();
        assertNotEquals(dev, v3); //Just verifying the reset nav got correct
        assertEquals(dev, v2);
        sampleRepo.write("file", "v5");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(),
                new TagDiscoveryTrait(),
                new DiscoverOtherRefsTrait("refs/custom/*")));
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of non head revision:
        assertEquals("v3", fileAt(v3, run, source, listener));
    }

    @Issue("JENKINS-48061")
    @Test
    public void retrieveRevision_customRef_abbrev_sha1() throws Exception {
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
        String v3 = sampleRepo.head();
        sampleRepo.git("update-ref", "refs/custom/foo", v3); // now this is an advertised ref so cannot be GC'd
        sampleRepo.git("reset", "--hard", "HEAD^"); // dev
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(),
                new TagDiscoveryTrait(),
                new DiscoverOtherRefsTrait("refs/custom/foo")));
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of non head revision:
        assertEquals("v3", fileAt(v3.substring(0, 7), run, source, listener));
    }

    @Issue("JENKINS-48061")
    @Test
    public void retrieveRevision_pr_refspec() throws Exception {
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
        String v3 = sampleRepo.head();
        sampleRepo.git("update-ref", "refs/pull-requests/1/from", v3); // now this is an advertised ref so cannot be GC'd
        sampleRepo.git("reset", "--hard", "HEAD^"); // dev
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait(), new DiscoverOtherRefsTrait("pull-requests/*/from")));
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of non head revision:
        assertEquals("v3", fileAt("pull-requests/1/from", run, source, listener));
    }

    @Issue("JENKINS-48061")
    @Test
    public void retrieveRevision_pr_local_refspec() throws Exception {
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
        String v3 = sampleRepo.head();
        sampleRepo.git("update-ref", "refs/pull-requests/1/from", v3); // now this is an advertised ref so cannot be GC'd
        sampleRepo.git("reset", "--hard", "HEAD^"); // dev
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        //new RefSpecsSCMSourceTrait("+refs/pull-requests/*/from:refs/remotes/@{remote}/pr/*")
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait(),
                new DiscoverOtherRefsTrait("/pull-requests/*/from", "pr/@{1}")));
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of non head revision:
        assertEquals("v3", fileAt("pr/1", run, source, listener));
    }

    private int wsCount;
    private String fileAt(String revision, Run<?,?> run, SCMSource source, TaskListener listener) throws Exception {
        SCMRevision rev = source.fetch(revision, listener, null);
        if (rev == null) {
            return null;
        } else {
            FilePath ws = new FilePath(run.getRootDir()).child("ws" + ++wsCount);
            source.build(rev.getHead(), rev).checkout(run, new Launcher.LocalLauncher(listener), ws, listener, null, SCMRevisionState.NONE);
            return ws.child("file").readToString();
        }
    }

    @Issue("JENKINS-48061")
    @Test
    public void fetchOtherRef() throws Exception {
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
        String v3 = sampleRepo.head();
        sampleRepo.git("update-ref", "refs/custom/1", v3);
        sampleRepo.git("reset", "--hard", "HEAD^"); // dev
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait(), new DiscoverOtherRefsTrait("custom/*")));
        StreamTaskListener listener = StreamTaskListener.fromStderr();

        final SCMHeadObserver.Collector collector =
        source.fetch(new SCMSourceCriteria() {
            @Override
            public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                return true;
            }
        }, new SCMHeadObserver.Collector(), listener);

        final Map<SCMHead, SCMRevision> result = collector.result();
        assertThat(result.entrySet(), hasSize(4));
        assertThat(result, hasKey(allOf(
                instanceOf(GitRefSCMHead.class),
                hasProperty("name", equalTo("custom-1"))
        )));
    }

    @Issue("JENKINS-48061")
    @Test
    public void fetchOtherRevisions() throws Exception {
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
        String v3 = sampleRepo.head();
        sampleRepo.git("update-ref", "refs/custom/1", v3);
        sampleRepo.git("reset", "--hard", "HEAD^"); // dev
        sampleRepo.write("file", "v4");
        sampleRepo.git("commit", "--all", "--message=v4"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait(), new DiscoverOtherRefsTrait("custom/*")));
        StreamTaskListener listener = StreamTaskListener.fromStderr();

        final Set<String> revisions = source.fetchRevisions(listener, null);

        assertThat(revisions, hasSize(4));
        assertThat(revisions, containsInAnyOrder(
                equalTo("custom-1"),
                equalTo("v1"),
                equalTo("dev"),
                equalTo("master")
        ));
    }

    @Issue("JENKINS-37727")
    @Test
    @Deprecated // Check GitSCMSource deprecated constructor
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
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());
        // And reuse cache:
        assertEquals(GitBranchSCMHead_DEV_MASTER, source.fetch(listener).toString());

        /* Create dev2 branch and write a file to it */
        sampleRepo.git("checkout", "-b", "dev2", "master");
        sampleRepo.write("dev2-file", "dev2-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "dev2-file");
        sampleRepo.git("commit", "--message=dev2-branch-commit-message");

        // Verify new branch is visible
        assertEquals(GitBranchSCMHead_DEV_DEV2_MASTER, source.fetch(listener).toString());

        /* Delete the dev branch */
        sampleRepo.git("branch", "-D", "dev");

        /* Fetch and confirm dev branch was pruned */
        assertEquals("[GitBranchSCMHead{name='dev2', ref='refs/heads/dev2'}, GitBranchSCMHead{name='master', ref='refs/heads/master'}]", source.fetch(listener).toString());
    }

    @Test
    @Deprecated // Tests deprecated getExtensions() and setExtensions()
    public void testSpecificRevisionBuildChooser() throws Exception {
        sampleRepo.init();

        /* Write a file to the master branch */
        sampleRepo.write("master-file", "master-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "master-file");
        sampleRepo.git("commit", "--message=master-branch-commit-message");

        /* Fetch from sampleRepo */
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Collections.<SCMSourceTrait>singletonList(new IgnoreOnPushNotificationTrait()));
        List<GitSCMExtension> extensions = new ArrayList<>();
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
    @Deprecated // Tests deprecated GitSCMSource constructor
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
    @Deprecated // Tests deprecated GitSCMSource constructor
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

    @Test
    public void refLockEncounteredIfPruneTraitNotPresentOnNotFoundRetrieval() throws Exception {
        TaskListener listener = StreamTaskListener.fromStderr();
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits((Collections.singletonList(new BranchDiscoveryTrait())));

        createRefLockEnvironment(listener, source);

        try {
            source.fetch("v1.2", listener, null);
        } catch (GitException e){
            assertFalse(e.getMessage().contains("--prune"));
            return;
        }
        //fail if ref lock does not occur
        fail();
    }

    @Test
    public void refLockEncounteredIfPruneTraitNotPresentOnTagRetrieval() throws Exception {
        TaskListener listener = StreamTaskListener.fromStderr();
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits((Collections.singletonList(new TagDiscoveryTrait())));

        createRefLockEnvironment(listener, source);

        try {
            source.fetch("v1.2", listener, null);
        } catch (GitException e){
            assertFalse(e.getMessage().contains("--prune"));
            return;
        }
        //fail if ref lock does not occur
        fail();
    }

    @Test
    public void refLockAvoidedIfPruneTraitPresentOnNotFoundRetrieval() throws Exception {
        /* Older git versions have unexpected behaviors with prune */
        if (!sampleRepo.gitVersionAtLeast(1, 9, 0)) {
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        TaskListener listener = StreamTaskListener.fromStderr();
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits((Arrays.asList(new TagDiscoveryTrait(), new PruneStaleBranchTrait())));

        createRefLockEnvironment(listener, source);

        source.fetch("v1.2", listener, null);

        assertEquals("[SCMHead{'v1.2'}]", source.fetch(listener).toString());
    }

    @Test
    public void refLockAvoidedIfPruneTraitPresentOnTagRetrieval() throws Exception {
        /* Older git versions have unexpected behaviors with prune */
        if (!sampleRepo.gitVersionAtLeast(1, 9, 0)) {
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        TaskListener listener = StreamTaskListener.fromStderr();
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits((Arrays.asList(new TagDiscoveryTrait(), new PruneStaleBranchTrait())));

        createRefLockEnvironment(listener, source);

        source.fetch("v1.2", listener, null);

        assertEquals("[SCMHead{'v1.2'}]", source.fetch(listener).toString());
    }

    private void createRefLockEnvironment(TaskListener listener, GitSCMSource source) throws Exception {
        String branch = "prune";
        String branchRefLock = "prune/prune";
        sampleRepo.init();

        //Create branch x
        sampleRepo.git("checkout", "-b", branch);
        sampleRepo.git("push", "--set-upstream", source.getRemote(), branch);

        //Ensure source retrieval has fetched branch x
        source.fetch("v1.2", listener, null);

        //Remove branch x
        sampleRepo.git("checkout", "master");
        sampleRepo.git("push", source.getRemote(), "--delete", branch);

        //Create branch x/x (ref lock engaged)
        sampleRepo.git("checkout", "-b", branchRefLock);
        sampleRepo.git("push", "--set-upstream", source.getRemote(), branchRefLock);

        //create tag for retrieval
        sampleRepo.git("tag", "v1.2");
        sampleRepo.git("push", source.getRemote(), "v1.2");
    }

    @Test @Issue("JENKINS-50394")
    public void when_commits_added_during_discovery_we_do_not_crash() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        System.setProperty(Git.class.getName() + ".mockClient", MockGitClient.class.getName());
        sharedSampleRepo = sampleRepo;
        try {
            GitSCMSource source = new GitSCMSource(sampleRepo.toString());
            source.setTraits(Arrays.<SCMSourceTrait>asList(new BranchDiscoveryTrait()));
            TaskListener listener = StreamTaskListener.fromStderr();
            SCMHeadObserver.Collector c = source.fetch(new SCMSourceCriteria() {
                @Override
                public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                    return true;
                }
            }, new SCMHeadObserver.Collector(), listener);

            assertThat(c.result().keySet(), containsInAnyOrder(
                    hasProperty("name", equalTo("master")),
                    hasProperty("name", equalTo("dev"))
            ));
        } catch(MissingObjectException me) {
            fail("Not supposed to get MissingObjectException");
        } finally {
            System.clearProperty(Git.class.getName() + ".mockClient");
            sharedSampleRepo = null;
        }
    }
    //Ugly but MockGitClient needs to be static and no good way to pass it on
    static GitSampleRepoRule sharedSampleRepo;

    public static class MockGitClient extends TestJGitAPIImpl {
        final String exe;
        final EnvVars env;

        public MockGitClient(String exe, EnvVars env, File workspace, TaskListener listener) {
            super(workspace, listener);
            this.exe = exe;
            this.env = env;
        }

        @Override
        public Map<String, ObjectId> getRemoteReferences(String url, String pattern, boolean headsOnly, boolean tagsOnly) throws GitException, InterruptedException {
            final Map<String, ObjectId> remoteReferences = super.getRemoteReferences(url, pattern, headsOnly, tagsOnly);
            try {
                //Now update the repo with new commits
                sharedSampleRepo.write("file2", "New");
                sharedSampleRepo.git("add", "file2");
                sharedSampleRepo.git("commit", "--all", "--message=inbetween");
            } catch (Exception e) {
                throw new GitException("Sneaking in something didn't work", e);
            }
            return remoteReferences;
        }

        @Override
        public FetchCommand fetch_() {
            final FetchCommand fetchCommand = super.fetch_();
            //returning something that updates the repo after the fetch is performed
            return new FetchCommand() {
                @Override
                public FetchCommand from(URIish urIish, List<RefSpec> list) {
                    fetchCommand.from(urIish, list);
                    return this;
                }

                @Override
                @Deprecated
                public FetchCommand prune() {
                    fetchCommand.prune(true);
                    return this;
                }

                @Override
                public FetchCommand prune(boolean b) {
                    fetchCommand.prune(b);
                    return this;
                }

                @Override
                public FetchCommand shallow(boolean b) {
                    fetchCommand.shallow(b);
                    return this;
                }

                @Override
                public FetchCommand timeout(Integer integer) {
                    fetchCommand.timeout(integer);
                    return this;
                }

                @Override
                public FetchCommand tags(boolean b) {
                    fetchCommand.tags(b);
                    return this;
                }

                @Override
                public FetchCommand depth(Integer integer) {
                    fetchCommand.depth(integer);
                    return this;
                }

                @Override
                public void execute() throws GitException, InterruptedException {
                    fetchCommand.execute();
                    try {
                        //Now update the repo with new commits
                        sharedSampleRepo.write("file3", "New");
                        sharedSampleRepo.git("add", "file3");
                        sharedSampleRepo.git("commit", "--all", "--message=inbetween");
                    } catch (Exception e) {
                        throw new GitException(e);
                    }
                }
            };
        }
    }

    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
