package hudson.plugins.git;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Verifies the git plugin interacts correctly with the multiple SCMs plugin.
 * 
 * @author corey@ooyala.com
 */
public class MultipleSCMTest extends HudsonTestCase {
	protected TaskListener listener;
	
	protected TestGitRepo repo0;
	protected TestGitRepo repo1;
	
	protected void setUp() throws Exception {
		super.setUp();
		
		listener = StreamTaskListener.fromStderr();
		
		repo0 = new TestGitRepo("repo0", this, listener);
		repo1 = new TestGitRepo("repo1", this, listener);
	}
}
