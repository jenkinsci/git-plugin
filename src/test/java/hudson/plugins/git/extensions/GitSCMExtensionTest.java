package hudson.plugins.git.extensions;

import hudson.model.*;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.impl.MessageExclusion;
import hudson.util.StreamTaskListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Kanstantsin Shautsou
 */
public abstract class GitSCMExtensionTest {

	protected TaskListener listener;

	@Rule
	public JenkinsRule j = new JenkinsRule();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		listener = StreamTaskListener.fromStderr();
		before();
	}

	protected abstract void before() throws Exception;
	protected abstract GitSCMExtension getExtension();

	protected FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult) throws Exception {
		final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
		if(expectedResult != null) {
			j.assertBuildStatus(expectedResult, build);
		}
		return build;
	}

	protected FreeStyleProject setupBasicProject(TestGitRepo repo, List<BranchSpec> branches) throws Exception {
		GitSCMExtension extension = getExtension();
		FreeStyleProject project = j.createFreeStyleProject(extension.getClass() + "Project");
		GitSCM scm = new GitSCM(
				repo.remoteConfigs(),
				branches,
				false, Collections.<SubmoduleConfig>emptyList(),
				null, null,
				Collections.<GitSCMExtension>emptyList());
		scm.getExtensions().add(extension);
		project.setScm(scm);
		project.getBuildersList().add(new CaptureEnvironmentBuilder());
		return project;
	}

	protected FreeStyleProject setupBasicProject(TestGitRepo repo) throws Exception {
		return setupBasicProject(repo, Collections.singletonList(new BranchSpec("master")));
	}
}
