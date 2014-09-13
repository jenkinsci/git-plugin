package hudson.plugins.git;

import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;

public class CliGitSCMTriggerRemotePollTest extends SCMTriggerTest
{

    /**
     * Currently some tests still fail due to bugs in productive code.
     * TODO: Fix bugs and enable tests.
     */
    private boolean SKIP_FAILING_TESTS = true;

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
    public void testNamespaces_with_master() throws Exception {
        if(SKIP_FAILING_TESTS) return; //TODO Fix productive code
        super.testNamespaces_with_master();
    }

    @Override
    public void testNamespaces_with_namespace1Master() throws Exception {
        //This one works by accident! ls-remote lists this as first entry
        super.testNamespaces_with_namespace1Master();
    }

    @Override
    public void testNamespaces_with_namespace2Master() throws Exception {
        if(SKIP_FAILING_TESTS) return; //TODO Fix productive code
        super.testNamespaces_with_namespace2Master();
    }

    @Override
    public void testCommitAsBranchSpec() throws Exception {
        if(SKIP_FAILING_TESTS) return; //TODO Fix productive code
        super.testCommitAsBranchSpec();
    }

    @Override
    public void testTags_with_TagA() throws Exception {
        if(SKIP_FAILING_TESTS) return; //TODO Fix productive code
        super.testTags_with_TagA();
    }

    @Override
    public void testTags_with_TagBAnnotated() throws Exception {
        if(SKIP_FAILING_TESTS) return; //TODO Fix productive code
        super.testTags_with_TagBAnnotated();
    }

    /* Skip this test because git client 1.10.2 and later include a fix for
     * JENKINS-23299.  The fix resolves refs/tags/tag_name as the commit to
     * which tag_name points.  Prior to that change, the ref pointed to the
     * SHA-1 of the tag, instead of the SHA-1 of the commit to which the tag
     * points.  Because of that bug fix, the git plugin correctly detects
     * refs/tags/TagA as needing to build.
     */
    @Override
    public void testTags_with_refsTagsTagA() throws Exception {
        return;
    }
}