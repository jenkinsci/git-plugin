package jenkins.plugins.git;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.scm.SCM;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.jenkinsci.plugins.workflow.libs.SCMRetriever;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.Collection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class GitJCasCCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        LibraryRetriever retriever = GlobalLibraries.get().getLibraries().get(0).getRetriever();
        assertThat(retriever, CoreMatchers.instanceOf(SCMRetriever.class));
        SCM scm =  ((SCMRetriever) retriever).getScm();
        assertThat(scm, CoreMatchers.instanceOf(GitSCM.class));
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class hudson.plugins.git.GitSCM.extensions = [cleanCheckout, gitLFSPull, {checkoutOption={}}, {userIdentity={}}, {preBuildMerge={}}]";
    }
}
