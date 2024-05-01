package hudson.plugins.git;

import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;

/**
 * Remote polling and local polling behave differently due to bugs in productive
 * code which probably cannot be fixed without serious compatibility problems.
 * The isChangeExpected() method adjusts the tests to the difference between
 * local and remote polling.
 */
public class CliGitSCMTriggerRemotePollTest extends SCMTriggerTest {

    @Override
    protected EnforceGitClient getGitClient()
    {
        return new EnforceGitClient().set(GitClientType.GITCLI);
    }

    @Override
    protected boolean isDisableRemotePoll()
    {
        return false;
    }

}
