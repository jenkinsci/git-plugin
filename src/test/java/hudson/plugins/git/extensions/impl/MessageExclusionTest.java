package hudson.plugins.git.extensions.impl;

import hudson.model.*;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.util.StreamTaskListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Kanstantsin Shautsou
 * based on {@link hudson.plugins.git.MultipleSCMTest}
 */
public class MessageExclusionTest {

	protected TaskListener listener;
	protected TestGitRepo repo;

	@Rule
	public JenkinsRule j = new JenkinsRule();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	public void setUp() throws IOException, InterruptedException {
		listener = StreamTaskListener.fromStderr();
		repo = new TestGitRepo("repo", tmp.newFolder(), listener);
	}

	@Test
	public void test() throws Exception {
		FreeStyleProject project = setupProject(".*\\[maven-release-plugin\\].*");

		repo.commit("repo-init", repo.johnDoe, "repo0 initial commit");

		assertTrue("scm polling should detect a change after initial commit", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, " [maven-release-plugin] excluded message commit");

		assertFalse("scm polling should not detect excluded message", project.poll(listener).hasChanges());

		// should be enough, but let's test more

		build(project, Result.SUCCESS);

		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

	}

	protected FreeStyleProject setupProject(String messageExclusion) throws Exception {
		FreeStyleProject project = j.createFreeStyleProject("messageExclusionProject");
		List<BranchSpec> branches = Collections.singletonList(new BranchSpec("master"));
		GitSCM scm = new GitSCM(
				repo.remoteConfigs(),
				branches,
				false, Collections.<SubmoduleConfig>emptyList(),
				null, null,
				Collections.<GitSCMExtension>emptyList());
		scm.getExtensions().add(new MessageExclusion(messageExclusion));
		project.setScm(scm);
		project.getBuildersList().add(new CaptureEnvironmentBuilder());
		return project;
	}

	private FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult) throws Exception {
		final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
		if(expectedResult != null) {
			j.assertBuildStatus(expectedResult, build);
		}
		return build;
	}

}
