package jenkins.plugins.git;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
public class AbstractGitSCMSourceTest {

  
  /*
   * Test excluded branches
   * 
   */
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

}
