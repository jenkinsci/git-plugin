package hudson.plugins.git.extensions;

import hudson.model.*;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.impl.MessageExclusion;
import hudson.plugins.git.extensions.impl.PathRestriction;
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
 */
public class GitSCMExtensionsCrossTest {
	protected TaskListener listener;
	protected TestGitRepo repo;

	@Rule
	public JenkinsRule j = new JenkinsRule();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		listener = StreamTaskListener.fromStderr();
		repo = new TestGitRepo("repo", tmp.newFolder(), listener);
	}

	@Test
	public void testPathExclusionWithMessageExclusion() throws Exception {
		FreeStyleProject project = j.createFreeStyleProject("Project-testPathExclusionWithMessageExclusion");
		List<BranchSpec> branches = Collections.singletonList(new BranchSpec("master"));
		GitSCM scm = new GitSCM(
				repo.remoteConfigs(),
				branches,
				false, Collections.<SubmoduleConfig>emptyList(),
				null, null,
				Collections.<GitSCMExtension>emptyList());
		scm.getExtensions().add(new MessageExclusion("(?s).*\\[maven-release-plugin\\].*"));
		scm.getExtensions().add(new PathRestriction("included/.*", "excluded/.*"));
		project.setScm(scm);
		project.getBuildersList().add(new CaptureEnvironmentBuilder());

		repo.commit("init-file", repo.johnDoe, "init commit");
		build(project, Result.SUCCESS);
		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

		repo.commit("excluded/file3", repo.janeDoe, "excluded/file3 commit");
		repo.commit("excluded/file4", repo.janeDoe, "excluded/file4 commit");
		repo.commit("repo-init", repo.janeDoe, " [maven-release-plugin] excluded message commit");
		repo.commit("repo-init2", repo.janeDoe, " [maven-release-plugin] excluded message commit2");
		assertFalse("scm polling should not detect 4 excluded commits at once!", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);
		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

		repo.commit("excluded/file3", repo.janeDoe, "2 excluded/file3 commit");
		repo.commit("excluded/file4", repo.janeDoe, "2 excluded/file4 commit");
		assertFalse("scm polling should not detect 2 excluded commits at once!", project.poll(listener).hasChanges());

		repo.commit("repo-init", repo.janeDoe, "2 [maven-release-plugin] excluded message commit");
		repo.commit("repo-init2", repo.janeDoe, "2 [maven-release-plugin] excluded message commit2");
		assertFalse("scm polling should not detect 2 excluded commits at the next poll run when previous polling " +
				"had everything excluded!", project.poll(listener).hasChanges());

		build(project, Result.SUCCESS);
		assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
	}

	protected FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult) throws Exception {
		final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
		if(expectedResult != null) {
			j.assertBuildStatus(expectedResult, build);
		}
		return build;
	}

}
