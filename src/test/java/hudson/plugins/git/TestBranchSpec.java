package hudson.plugins.git;

import junit.framework.Assert;
import junit.framework.TestCase;

public class TestBranchSpec extends TestCase
{
    public void testMatch()
    {
        BranchSpec l = new BranchSpec("master");
        Assert.assertTrue(l.matches("master"));
        Assert.assertFalse(l.matches("dev"));
        
        
        BranchSpec est = new BranchSpec("origin/*/dev");
        
        Assert.assertFalse(est.matches("origintestdev"));
        Assert.assertTrue(est.matches("origin/test/dev"));
        Assert.assertFalse(est.matches("origin/test/release"));
        Assert.assertFalse(est.matches("origin/test/somthing/release"));
        
        BranchSpec s = new BranchSpec("origin/*");
        
        Assert.assertTrue(s.matches("origin/master"));
      
        
    }
}
