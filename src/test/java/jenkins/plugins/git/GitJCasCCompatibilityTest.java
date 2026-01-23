package jenkins.plugins.git;

import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.jenkinsci.plugins.workflow.libs.SCMRetriever;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;


import static org.hamcrest.MatcherAssert.assertThat;

@WithJenkins
class GitJCasCCompatibilityTest extends AbstractRoundTripTest {

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule rule, String s) {
        LibraryRetriever retriever = GlobalLibraries.get().getLibraries().get(0).getRetriever();
        assertThat(retriever, CoreMatchers.instanceOf(SCMRetriever.class));
        SCM scm =  ((SCMRetriever) retriever).getScm();
        assertThat(scm, CoreMatchers.instanceOf(GitSCM.class));
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class hudson.plugins.git.GitSCM.extensions = [cleanCheckout, lfs, {checkoutOption={}}, {userIdentity={}}, {preBuildMerge={}}]";
    }
}
