package jenkins.plugins.git;

import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.plugins.git.GitStatus;
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

import static org.mockito.Mockito.mock;
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
    public void testSourceOwnerTriggeredByDoNotifyCommit() throws ServletException, IOException {
        GitSCMSource gitSCMSource = new GitSCMSource("id", REMOTE, "", "*", "", false);
        GitSCMSourceOwner scmSourceOwner = setupGitSCMSourceOwner(gitSCMSource);
        jenkins.getInstance().add(scmSourceOwner, "gitSourceOwner");

        gitStatus.doNotifyCommit(mock(HttpServletRequest.class), REMOTE, "master", "");

        verify(scmSourceOwner).onSCMSourceUpdated(gitSCMSource);
    }

    private GitSCMSourceOwner setupGitSCMSourceOwner(GitSCMSource gitSCMSource) {
        GitSCMSourceOwner owner = mock(GitSCMSourceOwner.class);
        when(owner.getSCMSources()).thenReturn(Collections.<SCMSource>singletonList(gitSCMSource));
        when(owner.hasPermission(Item.READ)).thenReturn(true);
        return owner;
    }

    private interface GitSCMSourceOwner extends TopLevelItem, SCMSourceOwner {}
}
