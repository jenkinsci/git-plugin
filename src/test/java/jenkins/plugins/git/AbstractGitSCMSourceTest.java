package jenkins.plugins.git;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCMRevisionState;
import hudson.util.StreamTaskListener;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
public class AbstractGitSCMSourceTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
  
  /*
   * Test excluded branches
   * TODO seems to overlap AbstractGitSCMSourceTrivialTest.testIsExcluded
   */
  @WithoutJenkins // unnecessary if moved into, say, AbstractGitSCMSourceTrivialTest
  @Test
  public void basicTestIsExcluded(){
    AbstractGitSCMSource abstractGitSCMSource = mock(AbstractGitSCMSource.class);
    
    when(abstractGitSCMSource.getIncludes()).thenReturn("*master release* fe?ture");
    when(abstractGitSCMSource.getExcludes()).thenReturn("release bugfix*");
    when(abstractGitSCMSource.isExcluded(Mockito.anyString())).thenCallRealMethod();
    
    assertFalse(abstractGitSCMSource.isExcluded("master"));
    assertFalse(abstractGitSCMSource.isExcluded("remote/master"));
    assertFalse(abstractGitSCMSource.isExcluded("release/X.Y"));
    assertFalse(abstractGitSCMSource.isExcluded("releaseX.Y"));
    assertFalse(abstractGitSCMSource.isExcluded("fe?ture"));
    assertTrue(abstractGitSCMSource.isExcluded("feature"));
    assertTrue(abstractGitSCMSource.isExcluded("release"));
    assertTrue(abstractGitSCMSource.isExcluded("bugfix"));
    assertTrue(abstractGitSCMSource.isExcluded("bugfix/test"));
    assertTrue(abstractGitSCMSource.isExcluded("test"));

    when(abstractGitSCMSource.getIncludes()).thenReturn("master feature/*");
    when(abstractGitSCMSource.getExcludes()).thenReturn("feature/*/private");
    assertFalse(abstractGitSCMSource.isExcluded("master"));
    assertTrue(abstractGitSCMSource.isExcluded("devel"));
    assertFalse(abstractGitSCMSource.isExcluded("feature/spiffy"));
    assertTrue(abstractGitSCMSource.isExcluded("feature/spiffy/private"));
  }

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
        assertThat(source.fetchRevisions(listener), Matchers.hasItems("master", "dev", "v1"));
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

}
