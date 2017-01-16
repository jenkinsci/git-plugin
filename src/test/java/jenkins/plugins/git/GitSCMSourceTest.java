package jenkins.plugins.git;

import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.plugins.git.GitStatus;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jenkins.scm.api.SCMEventListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
}
