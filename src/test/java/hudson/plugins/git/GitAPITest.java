package hudson.plugins.git;

import java.io.IOException;
import java.util.Set;

import org.jvnet.hudson.test.HudsonTestCase;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.util.StreamTaskListener;

public class GitAPITest extends HudsonTestCase {

	public void testGetTagNames() throws IOException, InterruptedException {
		StreamTaskListener listener = StreamTaskListener.fromStderr();
        FilePath moduleWs = new FilePath(createTmpDir());
        GitAPI moduleRepo = new GitAPI("git", moduleWs, listener, new EnvVars());
        moduleRepo.init();
        FilePath fileA=moduleWs.child("a");
        fileA.touch(0);
        moduleRepo.add("a");
        moduleRepo.launchCommand("commit", "-m", "Initial commit");
        moduleRepo.tag("foo", "foo comment");
        moduleRepo.tag("foo1", "foo1 comment");
        moduleRepo.tag("bar", "bar comment");
        Set<String> names=moduleRepo.getTagNames("foo*");
        assertEquals(2, names.size());
	}

}
