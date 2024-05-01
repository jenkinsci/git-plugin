package hudson.plugins.git;

import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;

public class JGitSCMTriggerLocalPollTest extends SCMTriggerTest
{
    
    @Override
    protected EnforceGitClient getGitClient()
    {
        return new EnforceGitClient().set(GitClientType.JGIT);
    }
    
    @Override
    protected boolean isDisableRemotePoll()
    {
        return true;
    }

}
