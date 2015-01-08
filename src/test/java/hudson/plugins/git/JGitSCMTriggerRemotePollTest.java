package hudson.plugins.git;

import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JGitSCMTriggerRemotePollTest extends SCMTriggerTest
{

    /**
     * Currently some tests still fail due to bugs in productive code.
     * TODO: Fix bugs and enable tests.
     */
    
    @Override
    protected EnforceGitClient getGitClient()
    {
        return new EnforceGitClient().set(GitClientType.JGIT);
    }
    
    @Override
    protected boolean isDisableRemotePoll()
    {
        return false;
    }

    @Override
    @Ignore
    @Test
    public void testNamespaces_with_master() throws Exception {
        super.testNamespaces_with_master();
    }

    @Override
    @Ignore
    @Test
    public void testNamespaces_with_namespace2Master() throws Exception {
        super.testNamespaces_with_namespace2Master();
    }
    
    @Override
    @Ignore
    @Test
    public void testCommitAsBranchSpec() throws Exception {
        super.testCommitAsBranchSpec();
    }

}