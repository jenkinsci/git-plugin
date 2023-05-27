package hudson.plugins.git.extensions;

import hudson.model.*;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.TestGitRepo;
import hudson.util.StreamTaskListener;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;

/**
 * @author Kanstantsin Shautsou
 */
public abstract class GitSCMExtensionTest {

	protected TaskListener listener;

	@ClassRule
	public static BuildWatcher buildWatcher = new BuildWatcher();

	@Rule
	public JenkinsRule r = new JenkinsRule();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		SystemReader.getInstance().getUserConfig().clear();
		listener = StreamTaskListener.fromStderr();
		before();
	}

	@Before
	public void allowNonRemoteCheckout() {
		GitSCM.ALLOW_LOCAL_CHECKOUT = true;
	}

	@After
	public void disallowNonRemoteCheckout() {
		GitSCM.ALLOW_LOCAL_CHECKOUT = false;
	}

	protected abstract void before() throws Exception;

	/**
	 * The {@link GitSCMExtension} being tested - this will be added to the
	 * project built in {@link #setupBasicProject(TestGitRepo)}
	 * @return the extension
	 */
	protected abstract GitSCMExtension getExtension();

	protected FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult) throws Exception {
		final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
		if(expectedResult != null) {
			r.assertBuildStatus(expectedResult, build);
		}
		return build;
	}

	/**
	 * Create a {@link FreeStyleProject} configured with a {@link GitSCM}
	 * building on the {@code master} branch of the provided {@code repo},
	 * and with the extension described in {@link #getExtension()} added.
	 * @param repo git repository
	 * @return the created project
	 * @throws Exception on error
	 */
	protected FreeStyleProject setupBasicProject(TestGitRepo repo) throws Exception {
		GitSCMExtension extension = getExtension();
		FreeStyleProject project = r.createFreeStyleProject("p");
		List<BranchSpec> branches = Collections.singletonList(new BranchSpec("master"));
		GitSCM scm = new GitSCM(
				repo.remoteConfigs(),
				branches,
				null, null,
				Collections.emptyList());
		scm.getExtensions().add(extension);
		project.setScm(scm);
		project.getBuildersList().add(new CaptureEnvironmentBuilder());
		return project;
	}
}
