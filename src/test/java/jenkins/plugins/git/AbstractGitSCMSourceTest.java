package jenkins.plugins.git;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCMRevisionState;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.util.StreamTaskListener;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import static org.hamcrest.Matchers.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
public class AbstractGitSCMSourceTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    // TODO AbstractGitSCMSourceRetrieveHeadsTest *sounds* like it would be the right place, but it does not in fact retrieve any heads!
    @Issue("JENKINS-37482")
    @Test
    public void retrieveHeads() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());
        // And reuse cache:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());
        sampleRepo.git("checkout", "-b", "dev2");
        sampleRepo.write("file", "modified again");
        sampleRepo.git("commit", "--all", "--message=dev2");
        // After changing data:
        assertEquals("[SCMHead{'dev'}, SCMHead{'dev2'}, SCMHead{'master'}]", source.fetch(listener).toString());
    }

    @Issue("JENKINS-31155")
    @Test
    public void retrieveRevision() throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "v1");
        sampleRepo.git("commit", "--all", "--message=v1");
        sampleRepo.git("tag", "v1");
        String v1 = sampleRepo.head();
        sampleRepo.write("file", "v2");
        sampleRepo.git("commit", "--all", "--message=v2"); // master
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "v3");
        sampleRepo.git("commit", "--all", "--message=v3"); // dev
        // SCM.checkout does not permit a null build argument, unfortunately.
        Run<?,?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        GitSCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        StreamTaskListener listener = StreamTaskListener.fromStderr();
        // Test retrieval of branches:
        assertEquals("v2", fileAt("master", run, source, listener));
        assertEquals("v3", fileAt("dev", run, source, listener));
        // Tags:
        assertEquals("v1", fileAt("v1", run, source, listener));
        // And commit hashes:
        assertEquals("v1", fileAt(v1, run, source, listener));
        assertEquals("v1", fileAt(v1.substring(0, 7), run, source, listener));
        // Nonexistent stuff:
        assertNull(fileAt("nonexistent", run, source, listener));
        assertNull(fileAt("1234567", run, source, listener));
        assertNull(fileAt("", run, source, listener));
        assertNull(fileAt("\n", run, source, listener));
        assertThat(source.fetchRevisions(listener), hasItems("master", "dev", "v1"));
        // we do not care to return commit hashes or other references
    }
    private String fileAt(String revision, Run<?,?> run, SCMSource source, TaskListener listener) throws Exception {
        SCMRevision rev = source.fetch(revision, listener);
        if (rev == null) {
            return null;
        } else {
            FilePath ws = new FilePath(run.getRootDir()).child("tmp-" + revision);
            source.build(rev.getHead(), rev).checkout(run, new Launcher.LocalLauncher(listener), ws, listener, null, SCMRevisionState.NONE);
            return ws.child("file").readToString();
        }
    }

    @Issue("JENKINS-37727")
    @Test
    public void pruneRemovesDeletedBranches() throws Exception {
        sampleRepo.init();

        /* Write a file to the master branch */
        sampleRepo.write("master-file", "master-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "master-file");
        sampleRepo.git("commit", "--message=master-branch-commit-message");

        /* Write a file to the dev branch */
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("dev-file", "dev-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "dev-file");
        sampleRepo.git("commit", "--message=dev-branch-commit-message");

        /* Fetch from sampleRepo */
        GitSCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        TaskListener listener = StreamTaskListener.fromStderr();
        // SCMHeadObserver.Collector.result is a TreeMap so order is predictable:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());
        // And reuse cache:
        assertEquals("[SCMHead{'dev'}, SCMHead{'master'}]", source.fetch(listener).toString());

        /* Create dev2 branch and write a file to it */
        sampleRepo.git("checkout", "-b", "dev2", "master");
        sampleRepo.write("dev2-file", "dev2-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "dev2-file");
        sampleRepo.git("commit", "--message=dev2-branch-commit-message");

        // Verify new branch is visible
        assertEquals("[SCMHead{'dev'}, SCMHead{'dev2'}, SCMHead{'master'}]", source.fetch(listener).toString());

        /* Delete the dev branch */
        sampleRepo.git("branch", "-D", "dev");

        /* Fetch and confirm dev branch was pruned */
        assertEquals("[SCMHead{'dev2'}, SCMHead{'master'}]", source.fetch(listener).toString());
    }

    @Test
    public void testSpecificRevisionBuildChooser() throws Exception {
        sampleRepo.init();

        /* Write a file to the master branch */
        sampleRepo.write("master-file", "master-content-" + UUID.randomUUID().toString());
        sampleRepo.git("add", "master-file");
        sampleRepo.git("commit", "--message=master-branch-commit-message");

        /* Fetch from sampleRepo */
        GitSCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        List<GitSCMExtension> extensions = new ArrayList<GitSCMExtension>();
        assertThat(source.getExtensions(), is(empty()));
        LocalBranch localBranchExtension = new LocalBranch("**");
        extensions.add(localBranchExtension);
        source.setExtensions(extensions);
        assertEquals(source.getExtensions(), extensions);
        TaskListener listener = StreamTaskListener.fromStderr();

        SCMHead head = new SCMHead("master");
        SCMRevision revision = new AbstractGitSCMSource.SCMRevisionImpl(head, "beaded4deed2bed4feed2deaf78933d0f97a5a34");

        /* Check that BuildChooserSetting not added to extensions by build() */
        GitSCM scm = (GitSCM) source.build(head);
        assertEquals(extensions, scm.getExtensions());

        /* Check that BuildChooserSetting has been added to extensions by build() */
        GitSCM scmRevision = (GitSCM) source.build(head, revision);
        assertEquals(extensions.get(0), scmRevision.getExtensions().get(0));
        assertTrue(scmRevision.getExtensions().get(1) instanceof BuildChooserSetting);
        assertEquals(2, scmRevision.getExtensions().size());
    }
}
