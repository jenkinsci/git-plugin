package jenkins.plugins.git;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import jenkins.scm.api.SCMSource;
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

}
