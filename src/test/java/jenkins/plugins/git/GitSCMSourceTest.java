package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitStatus;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.TagDiscoveryTrait;
import jenkins.scm.api.SCMEventListener;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Robin Müller
 */
public class GitSCMSourceTest {

    public static final String REMOTE = "git@remote:test/project.git";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private GitStatus gitStatus;

    @Before
    public void setup() {
        gitStatus = new GitStatus();
    }

    @Test
    public void testSourceOwnerTriggeredByDoNotifyCommit() throws Exception {
        GitSCMSource gitSCMSource = new GitSCMSource("id", REMOTE, "", "*", "", false);
        GitSCMSourceOwner scmSourceOwner = setupGitSCMSourceOwner(gitSCMSource);
        jenkins.getInstance().add(scmSourceOwner, "gitSourceOwner");

        gitStatus.doNotifyCommit(mock(HttpServletRequest.class), REMOTE, "master", "");

        SCMHeadEvent event = jenkins.getInstance().getExtensionList(SCMEventListener.class).get(SCMEventListenerImpl.class).waitSCMHeadEvent(1, TimeUnit.SECONDS);
        assertThat(event, notNullValue());
        assertThat((Iterable<SCMHead>)event.heads(gitSCMSource).keySet(), hasItem(is(new SCMHead("master"))));
        verify(scmSourceOwner, times(0)).onSCMSourceUpdated(gitSCMSource);

    }

    private GitSCMSourceOwner setupGitSCMSourceOwner(GitSCMSource gitSCMSource) {
        GitSCMSourceOwner owner = mock(GitSCMSourceOwner.class);
        when(owner.hasPermission(Item.READ)).thenReturn(true, true, true);
        when(owner.getSCMSources()).thenReturn(Collections.<SCMSource>singletonList(gitSCMSource));
        return owner;
    }

    private interface GitSCMSourceOwner extends TopLevelItem, SCMSourceOwner {}

    @TestExtension
    public static class SCMEventListenerImpl extends SCMEventListener {

        SCMHeadEvent<?> head = null;

        @Override
        public void onSCMHeadEvent(SCMHeadEvent<?> event) {
            synchronized (this) {
                head = event;
                notifyAll();
            }
        }

        public SCMHeadEvent<?> waitSCMHeadEvent(long timeout, TimeUnit units)
                throws TimeoutException, InterruptedException {
            long giveUp = System.currentTimeMillis() + units.toMillis(timeout);
            while (System.currentTimeMillis() < giveUp) {
                synchronized (this) {
                    SCMHeadEvent h = head;
                    if (h != null) {
                        head = null;
                        return h;
                    }
                    wait(Math.max(1L, giveUp - System.currentTimeMillis()));
                }
            }
            throw new TimeoutException();
        }
    }

    @Issue("JENKINS-47526")
    @Test
    public void telescopeFetch() throws Exception {

        GitSCMSource instance = new GitSCMSource("http://git.test/telescope.git");
        assertThat(GitSCMTelescope.of(instance), nullValue());
        instance.setOwner(mock(SCMSourceOwner.class));
        assertThat(GitSCMTelescope.of(instance), notNullValue());

        instance.setTraits(Arrays.<SCMSourceTrait>asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        Map<SCMHead, SCMRevision> result = instance.fetch(SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("manchu"), "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc"
                ),
                new GitTagSCMRevision(
                        new GitTagSCMHead("v1.0.0", 15086193840000L), "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                )));

        instance.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        result = instance.fetch(SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("manchu"), "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc"
                )));

        instance.setTraits(Collections.<SCMSourceTrait>singletonList(new TagDiscoveryTrait()));
        result = instance.fetch(SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new GitTagSCMRevision(
                        new GitTagSCMHead("v1.0.0", 15086193840000L), "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                )));
    }

    @TestExtension("telescopeFetch")
    public static class MyGitSCMTelescope extends GitSCMTelescope {
        @Override
        public boolean supports(@NonNull String remote) {
            return true;
        }

        @Override
        public void validate(@NonNull String remote, StandardCredentials credentials)
                throws IOException, InterruptedException {
        }

        @Override
        protected SCMFileSystem build(@NonNull String remote, StandardCredentials credentials,
                                      @NonNull SCMHead head,
                                      SCMRevision rev) throws IOException, InterruptedException {
            return null;
        }

        @Override
        public long getTimestamp(@NonNull String remote, StandardCredentials credentials, @NonNull String refOrHash)
                throws IOException, InterruptedException {
            return 0;
        }

        @Override
        public SCMRevision getRevision(@NonNull String remote, StandardCredentials credentials,
                                       @NonNull String refOrHash)
                throws IOException, InterruptedException {
            return null;
        }

        @Override
        public Iterable<SCMRevision> getRevisions(@NonNull String remote, StandardCredentials credentials,
                                                  @NonNull Set<ReferenceType> referenceTypes)
                throws IOException, InterruptedException {
            return Arrays.<SCMRevision>asList(
                    new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                    ),
                    new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                    ),
                    new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("manchu"), "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc"
                    ),
                    new GitTagSCMRevision(
                            new GitTagSCMHead("v1.0.0", 15086193840000L), "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                    )
            );
        }

        @Override
        public String getDefaultTarget(@NonNull String remote, StandardCredentials credentials)
                throws IOException, InterruptedException {
            return "manchu";
        }
    }
}
