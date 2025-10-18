package hudson.plugins.git.extensions.impl;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.BeforeEach;

import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutOptionTest {

    private CheckoutOption option;
    private static final int INITIAL_TIMEOUT = 10;

    @BeforeEach
    void beforeEach() {
        option = new CheckoutOption(INITIAL_TIMEOUT);
    }

    @Test
    void testGetTimeout() {
        assertEquals(INITIAL_TIMEOUT, (int) option.getTimeout());
    }

    @Test
    void testRequiresWorkspaceForPolling() {
        assertFalse(option.requiresWorkspaceForPolling());
    }

    @Test
    void testDecorateCheckoutCommand() throws Exception {
        final int NEW_TIMEOUT = 13;

        CheckoutCommandImpl cmd = new CheckoutCommandImpl();
        assertEquals(INITIAL_TIMEOUT, cmd.getTimeout());

        GitSCM scm = null;
        Run build = null;
        TaskListener listener = null;
        GitClient git = null;

        option = new CheckoutOption(NEW_TIMEOUT);
        option.decorateCheckoutCommand(scm, build, git, listener, cmd);
        assertEquals(NEW_TIMEOUT, cmd.getTimeout());
    }

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(CheckoutOption.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }

    @Test
    void checkToString() {
        assertEquals("CheckoutOption{timeout=" + INITIAL_TIMEOUT + "}", option.toString());
    }

    public static class CheckoutCommandImpl implements CheckoutCommand {

        private int timeout = INITIAL_TIMEOUT;

        public int getTimeout() {
            return timeout;
        }

        @Override
        public CheckoutCommand timeout(Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        @Override
        public CheckoutCommand ref(String ref) {
            throw new UnsupportedOperationException("Don't call me");
        }

        @Override
        public CheckoutCommand branch(String branch) {
            throw new UnsupportedOperationException("Don't call me");
        }

        @Override
        public CheckoutCommand deleteBranchIfExist(boolean deleteBranch) {
            throw new UnsupportedOperationException("Don't call me");
        }

        @Override
        public CheckoutCommand sparseCheckoutPaths(List<String> sparseCheckoutPaths) {
            throw new UnsupportedOperationException("Don't call me");
        }

        @Override
        public CheckoutCommand lfsRemote(String lfsRemote) {
            throw new UnsupportedOperationException("Don't call me");
        }

        @Override
        public CheckoutCommand lfsCredentials(StandardCredentials lfsCredentials) {
            throw new UnsupportedOperationException("Don't call me");
        }

        @Override
        public void execute() throws GitException, InterruptedException {
            throw new UnsupportedOperationException("Don't call me");
        }
    }
}
