package hudson.plugins.git;

import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import org.junit.*;


public class JGitSCMTriggerRemotePollTest extends JUnit4SCMTriggerTestAdapter
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
    @Ignore(value = "Currently some tests still fail due to bugs in productive code. TODO: Fix bugs and enable tests.")
    @Test
    public void testNamespaces_with_master() throws Exception {
        super.testNamespaces_with_master();
    }

    @Override
    @Ignore(value = "Currently some tests still fail due to bugs in productive code. TODO: Fix bugs and enable tests.")
    @Test
    public void testNamespaces_with_namespace2Master() throws Exception {
        super.testNamespaces_with_namespace2Master();
    }
    
    @Override
    @Ignore(value = "Currently some tests still fail due to bugs in productive code. TODO: Fix bugs and enable tests.")
    @Test
    public void testCommitAsBranchSpec() throws Exception {
        super.testCommitAsBranchSpec();
    }


}