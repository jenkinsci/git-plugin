package hudson.plugins.git;

import hudson.EnvVars;
import java.util.HashMap;

import junit.framework.Assert;
import junit.framework.TestCase;

public class TestBranchSpec extends TestCase {
    public void testMatch() {
        BranchSpec l = new BranchSpec("master");
        Assert.assertTrue(l.matches("origin/master"));
        Assert.assertFalse(l.matches("origin/something/master"));
        Assert.assertFalse(l.matches("master"));
        Assert.assertFalse(l.matches("dev"));
        
        
        BranchSpec est = new BranchSpec("origin/*/dev");
        
        Assert.assertFalse(est.matches("origintestdev"));
        Assert.assertTrue(est.matches("origin/test/dev"));
        Assert.assertFalse(est.matches("origin/test/release"));
        Assert.assertFalse(est.matches("origin/test/somthing/release"));
        
        BranchSpec s = new BranchSpec("origin/*");
        
        Assert.assertTrue(s.matches("origin/master"));
      
        BranchSpec m = new BranchSpec("**/magnayn/*");
        
        Assert.assertTrue(m.matches("origin/magnayn/b1"));
        Assert.assertTrue(m.matches("remote/origin/magnayn/b1"));
      
        BranchSpec n = new BranchSpec("*/my.branch/*");
        
        Assert.assertTrue(n.matches("origin/my.branch/b1"));
        Assert.assertFalse(n.matches("origin/my-branch/b1"));
        Assert.assertFalse(n.matches("remote/origin/my.branch/b1"));
      
        BranchSpec o = new BranchSpec("**");
        
        Assert.assertTrue(o.matches("origin/my.branch/b1"));
        Assert.assertTrue(o.matches("origin/my-branch/b1"));
        Assert.assertTrue(o.matches("remote/origin/my.branch/b1"));
      
        BranchSpec p = new BranchSpec("*");

        Assert.assertTrue(p.matches("origin/x"));
        Assert.assertFalse(p.matches("origin/my-branch/b1"));
    }
    
    public void testMatchEnv() {
        HashMap<String, String> envMap = new HashMap<String, String>();
        envMap.put("master", "master");
        envMap.put("origin", "origin");
        envMap.put("dev", "dev");
        envMap.put("magnayn", "magnayn");
        envMap.put("mybranch", "my.branch");
        envMap.put("anyLong", "**");
        envMap.put("anyShort", "*");
        EnvVars env = new EnvVars(envMap);

        BranchSpec l = new BranchSpec("${master}");
        Assert.assertTrue(l.matches("origin/master", env));
        Assert.assertFalse(l.matches("origin/something/master", env));
        Assert.assertFalse(l.matches("master", env));
        Assert.assertFalse(l.matches("dev", env));


        BranchSpec est = new BranchSpec("${origin}/*/${dev}");

        Assert.assertFalse(est.matches("origintestdev", env));
        Assert.assertTrue(est.matches("origin/test/dev", env));
        Assert.assertFalse(est.matches("origin/test/release", env));
        Assert.assertFalse(est.matches("origin/test/somthing/release", env));

        BranchSpec s = new BranchSpec("${origin}/*");

        Assert.assertTrue(s.matches("origin/master", env));

        BranchSpec m = new BranchSpec("**/${magnayn}/*");

        Assert.assertTrue(m.matches("origin/magnayn/b1", env));
        Assert.assertTrue(m.matches("remote/origin/magnayn/b1", env));

        BranchSpec n = new BranchSpec("*/${mybranch}/*");

        Assert.assertTrue(n.matches("origin/my.branch/b1", env));
        Assert.assertFalse(n.matches("origin/my-branch/b1", env));
        Assert.assertFalse(n.matches("remote/origin/my.branch/b1", env));

        BranchSpec o = new BranchSpec("${anyLong}");

        Assert.assertTrue(o.matches("origin/my.branch/b1", env));
        Assert.assertTrue(o.matches("origin/my-branch/b1", env));
        Assert.assertTrue(o.matches("remote/origin/my.branch/b1", env));

        BranchSpec p = new BranchSpec("${anyShort}");

        Assert.assertTrue(p.matches("origin/x", env));
        Assert.assertFalse(p.matches("origin/my-branch/b1", env));
    }

    public void testEmptyName() {
    	BranchSpec branchSpec = new BranchSpec("");
    	assertEquals("**",branchSpec.getName());
    }
    
    public void testNullName() {
    	boolean correctExceptionThrown = false;
    	try {
    		BranchSpec branchSpec = new BranchSpec(null);
    	} catch (IllegalArgumentException e) {
    		correctExceptionThrown = true;
    	}
    	assertTrue(correctExceptionThrown);
    }
    
    public void testNameTrimming() {
    	BranchSpec branchSpec = new BranchSpec(" master ");
    	assertEquals("master",branchSpec.getName());
    	branchSpec.setName(" other ");
    	assertEquals("other",branchSpec.getName());
    }
    
    public void testUsesJavaPatternDirectlyIfPrefixedWithColon() {
    	BranchSpec m = new BranchSpec(":^(?!(origin/prefix)).*");
    	assertTrue(m.matches("origin"));
    	assertTrue(m.matches("origin/master"));
    	assertTrue(m.matches("origin/feature"));

    	assertFalse(m.matches("origin/prefix_123"));
    	assertFalse(m.matches("origin/prefix"));
    	assertFalse(m.matches("origin/prefix-abc"));
    }
}
