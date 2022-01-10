package hudson.plugins.git;

import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;

public class CliGitSCMTriggerLocalPollTest extends SCMTriggerTest
{

    @Override
    protected EnforceGitClient getGitClient()
    {
        return new EnforceGitClient().set(GitClientType.GITCLI);
    }

    @Override
    protected boolean isDisableRemotePoll()
    {
        return true;
    }

}
