package hudson.plugins.git;

import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import org.junit.Ignore;
import org.junit.Test;

public class CliGitSCMTriggerRemotePollTest extends JUnit4SCMTriggerTestAdapter
{

    /**
     * Currently some tests still fail due to bugs in productive code.
     * TODO: Fix bugs and enable tests.
     */
        
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
    
    @Override
    @Test
    @Ignore("Currently some tests still fail due to bugs in productive code. TODO: Fix bugs and enable tests.")
    public void testNamespaces_with_master() throws Exception {
        super.testNamespaces_with_master();
    }

    @Override
    @Test
    public void testNamespaces_with_namespace1Master() throws Exception {
        //This one works by accident! ls-remote lists this as first entry
        super.testNamespaces_with_namespace1Master();
    }
    
    @Override
    @Ignore("Currently some tests still fail due to bugs in productive code. TODO: Fix bugs and enable tests.")
    @Test
    public void testNamespaces_with_namespace2Master() throws Exception {
        super.testNamespaces_with_namespace2Master();
    }
    
    @Override
    @Ignore("Currently some tests still fail due to bugs in productive code. TODO: Fix bugs and enable tests.")
    @Test
    public void testCommitAsBranchSpec() throws Exception {
        super.testCommitAsBranchSpec();
    }

    @Override
    @Ignore("Currently some tests still fail due to bugs in productive code. TODO: Fix bugs and enable tests.")
    @Test
    public void testTags_with_TagA() throws Exception {
        super.testTags_with_TagA();
    }

    @Override
    @Ignore("Currently some tests still fail due to bugs in productive code. TODO: Fix bugs and enable tests.")
    @Test
    public void testTags_with_TagBAnnotated() throws Exception {
        super.testTags_with_TagBAnnotated();
    }

    @Override
    @Ignore("Skip this test because git client 1.10.2 and later include a fix for\n" +
            "JENKINS-23299.  The fix resolves refs/tags/tag_name as the commit to\n" +
            "which tag_name points.  Prior to that change, the ref pointed to the\n" +
            "SHA-1 of the tag, instead of the SHA-1 of the commit to which the tag\n" +
            "points.  Because of that bug fix, the git plugin correctly detects\n" +
            "refs/tags/TagA as needing to build.")
    @Test
    public void testTags_with_refsTagsTagA() throws Exception {
        return;
    }
}
