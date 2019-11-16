package jenkins.plugins.git.traits;

import hudson.model.TaskListener;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Test for JENKINS-57683 - Class cast exception when an SCMSource or
 * SCMSourceContext was passed that was not a GitSCMSource.
 *
 * @author Mark Waite
 */
public class PruneStaleBranchTraitTest {

    public PruneStaleBranchTraitTest() {
    }

    @Test
    public void testDecorateContextWithGitSCMSourceContent() {
        GitSCMSourceContext context = new GitSCMSourceContext(null, null);
        assertThat(context.pruneRefs(), is(false));
        PruneStaleBranchTrait pruneStaleBranchTrait = new PruneStaleBranchTrait();
        pruneStaleBranchTrait.decorateContext(context);
        assertThat(context.pruneRefs(), is(true));
    }

    @Test
    @Issue("JENKINS-57683")
    public void testDecorateContextWithNonGitSCMSourceContent() {
        SCMSourceContext context = new FakeSCMSourceContext(null, null);
        PruneStaleBranchTrait pruneStaleBranchTrait = new PruneStaleBranchTrait();
        pruneStaleBranchTrait.decorateContext(context);
        /* JENKINS-57683 would cause this test to throw an exception */
    }

    private static class FakeSCMSourceContext extends SCMSourceContext {

        public FakeSCMSourceContext(SCMSourceCriteria scmsc, SCMHeadObserver scmho) {
            super(scmsc, scmho);
        }

        @Override
        public SCMSourceRequest newRequest(SCMSource scms, TaskListener tl) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
