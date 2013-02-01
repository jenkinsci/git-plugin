package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;

import org.jvnet.hudson.test.HudsonTestCase;

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

	public void testGetTagNamesFromCloned() throws IOException, InterruptedException, URISyntaxException {
		StreamTaskListener listener = StreamTaskListener.fromStderr();
        FilePath moduleRemoteWs = new FilePath(createTmpDir());
        GitAPI moduleRemoteRepo = new GitAPI("git", moduleRemoteWs, listener, new EnvVars());
        moduleRemoteRepo.init();
        FilePath fileA=moduleRemoteWs.child("a");
        fileA.touch(0);
        moduleRemoteRepo.add("a");
        moduleRemoteRepo.launchCommand("commit", "-m", "Initial commit");
        moduleRemoteRepo.tag("foo", "foo comment");
        moduleRemoteRepo.tag("foo1", "foo1 comment");
        moduleRemoteRepo.tag("bar", "bar comment");
        // there must be an easier way to clone another file repo ...
        FilePath moduleLocalWs = new FilePath(createTmpDir());
        GitAPI tempRepo = new GitAPI("git", moduleLocalWs, listener, new EnvVars());
        tempRepo.launchCommand("clone", "file://"+moduleRemoteWs.getRemote(), "jenkins-git");
        GitAPI moduleLocalRepo = new GitAPI("git", moduleLocalWs.child("jenkins-git"), listener, new EnvVars());
        Set<String> names=moduleLocalRepo.getTagNames("foo*");
        assertEquals(2, names.size());
	}

}
