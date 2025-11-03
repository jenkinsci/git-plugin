package hudson.plugins.git.extensions;

import hudson.model.*;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.TestGitRepo;
import hudson.util.StreamTaskListener;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Kanstantsin Shautsou
 */
@WithJenkins
public abstract class GitSCMExtensionTest {

	protected TaskListener listener;

	@RegisterExtension
	protected static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

	protected JenkinsRule r;

	@TempDir
    protected File tmp;

    @BeforeEach
    protected void beforeEach(JenkinsRule rule) throws Exception {
        r = rule;
		SystemReader.getInstance().getUserConfig().clear();
		listener = StreamTaskListener.fromStderr();
		before();

		GitSCM.ALLOW_LOCAL_CHECKOUT = true;
	}

	@AfterEach
	protected void afterEach() {
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
