package hudson.plugins.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.util.StreamTaskListener;
import hudson.scm.SCM;

import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Verifies the git plugin interacts correctly with the multiple SCMs plugin.
 * 
 * @author corey@ooyala.com
 */
public class MultipleSCMTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

	protected TaskListener listener;
	
	protected TestGitRepo repo0;
	protected TestGitRepo repo1;
	
	@Before public void setUp() throws Exception {
		listener = StreamTaskListener.fromStderr();
		
		repo0 = new TestGitRepo("repo0", tmp.newFolder(), listener);
		repo1 = new TestGitRepo("repo1", tmp.newFolder(), listener);
	}

    @Ignore("https://github.com/jenkinsci/multiple-scms-plugin/pull/5 broke this; cf. JENKINS-14537")
	@Test public void basic() throws Exception
	{
		FreeStyleProject project = setupBasicProject("master");

        repo0.commit("repo0-init", repo0.johnDoe, "repo0 initial commit");

        assertTrue("scm polling should detect a change after initial commit",
                project.pollSCMChanges(listener));

        repo1.commit("repo1-init", repo1.janeDoe, "repo1 initial commit");

		build(project, Result.SUCCESS);
		
		assertFalse("scm polling should not detect any more changes after build", 
				project.pollSCMChanges(listener));

        repo1.commit("repo1-1", repo1.johnDoe, "repo1 commit 1");

        build(project, Result.SUCCESS);

        assertFalse("scm polling should not detect any more changes after build",
                project.pollSCMChanges(listener));

        repo0.commit("repo0-1", repo0.janeDoe, "repo0 commit 1");

        build(project, Result.SUCCESS);

        assertFalse("scm polling should not detect any more changes after build",
                project.pollSCMChanges(listener));
	}
	
	private FreeStyleProject setupBasicProject(String name) throws IOException
	{
		FreeStyleProject project = r.createFreeStyleProject(name);
		
		List<BranchSpec> branch = Collections.singletonList(new BranchSpec("master"));
		
		SCM repo0Scm = new GitSCM(
                repo0.remoteConfigs(),
				  branch,
                false,
				  Collections.<SubmoduleConfig>emptyList(),
                null,
				  null,
                Collections.<GitSCMExtension>emptyList());

		SCM repo1Scm = new GitSCM(
                repo1.remoteConfigs(),
				  branch,
                false,
				  Collections.<SubmoduleConfig>emptyList(),
                null,
				  null,
                Collections.<GitSCMExtension>emptyList());

		List<SCM> testScms = new ArrayList<SCM>();
		testScms.add(repo0Scm);
		testScms.add(repo1Scm);
		
		MultiSCM scm = new MultiSCM(testScms);
		
		project.setScm(scm);
		project.getBuildersList().add(new CaptureEnvironmentBuilder());
		return project;
	}
	
	private FreeStyleBuild build(final FreeStyleProject project, 
			final Result expectedResult) throws Exception {
		final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
		if(expectedResult != null) {
			r.assertBuildStatus(expectedResult, build);
		}
		return build;
	}
}
