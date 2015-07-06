package hudson.plugins.git;

import hudson.EnvVars;
import java.util.HashMap;
import static org.junit.Assert.*;
import org.junit.Test;


public class TestBranchSpec {
    @Test
    public void testMatch() {

        BranchSpec l = new BranchSpec("master");
        assertTrue(l.matches("origin/master"));
        assertFalse(l.matches("origin/something/master"));
        assertTrue(l.matches("master"));
        assertFalse(l.matches("dev"));
        
        
        BranchSpec est = new BranchSpec("origin/*/dev");
        
        assertFalse(est.matches("origintestdev"));
        assertTrue(est.matches("origin/test/dev"));
        assertFalse(est.matches("origin/test/release"));
        assertFalse(est.matches("origin/test/somthing/release"));
        
        BranchSpec s = new BranchSpec("origin/*");
        
        assertTrue(s.matches("origin/master"));
      
        BranchSpec m = new BranchSpec("**/magnayn/*");
        
        assertTrue(m.matches("origin/magnayn/b1"));
        assertTrue(m.matches("remote/origin/magnayn/b1"));
        assertTrue(m.matches("remotes/origin/magnayn/b1"));
      
        BranchSpec n = new BranchSpec("*/my.branch/*");
        
        assertTrue(n.matches("origin/my.branch/b1"));
        assertFalse(n.matches("origin/my-branch/b1"));
        assertFalse(n.matches("remote/origin/my.branch/b1"));
        assertTrue(n.matches("remotes/origin/my.branch/b1"));
      
        BranchSpec o = new BranchSpec("**");
        
        assertTrue(o.matches("origin/my.branch/b1"));
        assertTrue(o.matches("origin/my-branch/b1"));
        assertTrue(o.matches("remote/origin/my.branch/b1"));
        assertTrue(o.matches("remotes/origin/my.branch/b1"));
      
        BranchSpec p = new BranchSpec("*");

        assertTrue(p.matches("origin/x"));
        assertFalse(p.matches("origin/my-branch/b1"));
    }
    
    @Test
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
        assertTrue(l.matches("origin/master", env));
        assertFalse(l.matches("origin/something/master", env));
        assertTrue(l.matches("master", env));
        assertFalse(l.matches("dev", env));


        BranchSpec est = new BranchSpec("${origin}/*/${dev}");

        assertFalse(est.matches("origintestdev", env));
        assertTrue(est.matches("origin/test/dev", env));
        assertFalse(est.matches("origin/test/release", env));
        assertFalse(est.matches("origin/test/somthing/release", env));

        BranchSpec s = new BranchSpec("${origin}/*");

        assertTrue(s.matches("origin/master", env));

        BranchSpec m = new BranchSpec("**/${magnayn}/*");

        assertTrue(m.matches("origin/magnayn/b1", env));
        assertTrue(m.matches("remote/origin/magnayn/b1", env));

        BranchSpec n = new BranchSpec("*/${mybranch}/*");

        assertTrue(n.matches("origin/my.branch/b1", env));
        assertFalse(n.matches("origin/my-branch/b1", env));
        assertFalse(n.matches("remote/origin/my.branch/b1", env));

        BranchSpec o = new BranchSpec("${anyLong}");

        assertTrue(o.matches("origin/my.branch/b1", env));
        assertTrue(o.matches("origin/my-branch/b1", env));
        assertTrue(o.matches("remote/origin/my.branch/b1", env));

        BranchSpec p = new BranchSpec("${anyShort}");

        assertTrue(p.matches("origin/x", env));
        assertFalse(p.matches("origin/my-branch/b1", env));
    }

    @Test
    public void testEmptyName() {
    	BranchSpec branchSpec = new BranchSpec("");
    	assertEquals("**",branchSpec.getName());
    }
    
    @Test
    public void testNullName() {
    	boolean correctExceptionThrown = false;
    	try {
    		BranchSpec branchSpec = new BranchSpec(null);
    	} catch (IllegalArgumentException e) {
    		correctExceptionThrown = true;
    	}
    	assertTrue(correctExceptionThrown);
    }
    
    @Test
    public void testNameTrimming() {
    	BranchSpec branchSpec = new BranchSpec(" master ");
    	assertEquals("master",branchSpec.getName());
    	branchSpec.setName(" other ");
    	assertEquals("other",branchSpec.getName());
    }
    
    @Test
    public void testUsesRefsHeads() {
    	BranchSpec m = new BranchSpec("refs/heads/j*n*");
    	assertTrue(m.matches("refs/heads/jenkins"));
    	assertTrue(m.matches("refs/heads/jane"));
    	assertTrue(m.matches("refs/heads/jones"));

    	assertFalse(m.matches("origin/jenkins"));
    	assertFalse(m.matches("remote/origin/jane"));
    }
    
    @Test
    public void testUsesJavaPatternDirectlyIfPrefixedWithColon() {
    	BranchSpec m = new BranchSpec(":^(?!(origin/prefix)).*");
    	assertTrue(m.matches("origin"));
    	assertTrue(m.matches("origin/master"));
    	assertTrue(m.matches("origin/feature"));

    	assertFalse(m.matches("origin/prefix_123"));
    	assertFalse(m.matches("origin/prefix"));
    	assertFalse(m.matches("origin/prefix-abc"));
    }

    @Test
    public void testUsesJavaPatternWithRepetition() {
    	// match pattern from JENKINS-26842
    	BranchSpec m = new BranchSpec(":origin/release-\\d{8}");
    	assertTrue(m.matches("origin/release-20150101"));
    	assertFalse(m.matches("origin/release-2015010"));
    	assertFalse(m.matches("origin/release-201501011"));
    	assertFalse(m.matches("origin/release-20150101-something"));
    }
}
