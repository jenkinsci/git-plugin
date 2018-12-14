package hudson.plugins.git;

import hudson.EnvVars;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.SCM;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MultipleSCMEnvVarsTest {

  @Rule public JenkinsRule r = new JenkinsRule();
  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  protected TaskListener listener;

  protected TestGitRepo repo0;

  @Before public void setUp() throws Exception {
    listener = StreamTaskListener.fromStderr();
    repo0 = new TestGitRepo("repo0", tmp.newFolder(), listener);
  }

  @Issue("JENKINS-53346")
  @Test public void testEnvVarsForSameRepo() throws Exception
  {
    FreeStyleProject project = setupBasicProject("same-repo",
      new Tuple(repo0, Collections.singletonList(new BranchSpec("master"))),
      new Tuple(repo0, Collections.singletonList(new BranchSpec("dev")))
    );

    String sha1 = repo0.commit("file", repo0.johnDoe, "message");
    repo0.git.checkoutBranch("dev","master");
    String sha2 = repo0.commit("file2", repo0.janeDoe, "message");

    FreeStyleBuild b = build(project, Result.SUCCESS);

    EnvVars env = new EnvVars();
    project.getScm().buildEnvironment(b, env);

    assertEquals("origin/master", env.get(GitSCM.GIT_BRANCH));
    assertEquals(sha1, env.get(GitSCM.GIT_COMMIT));
    assertEquals("origin/dev", env.get(GitSCM.GIT_BRANCH + "_1"));
    assertEquals(sha2, env.get(GitSCM.GIT_COMMIT + "_1"));
  }

  private FreeStyleProject setupBasicProject(String name, Tuple... repos) throws IOException
  {
    FreeStyleProject project = r.createFreeStyleProject(name);

    List<SCM> testScms = new ArrayList<>();

    for (Tuple repo : repos) {
      testScms.add(new GitSCM(
        repo.getRepo().remoteConfigs(),
        repo.getBranch(),
        false,
        Collections.<SubmoduleConfig>emptyList(),
        null,
        null,
        Collections.<GitSCMExtension>emptyList()));
    }

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

  private class Tuple {
    TestGitRepo repo;
    List<BranchSpec> branch;

    public Tuple(TestGitRepo repo, List<BranchSpec> branch) {
      this.repo = repo;
      this.branch = branch;
    }

    public TestGitRepo getRepo() {
      return repo;
    }

    public List<BranchSpec> getBranch() {
      return branch;
    }
  }
}
