/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.git;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import hudson.util.StreamTaskListener;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GitStatusMultipleSCMTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private GitStatus gitStatus;
    private TestGitRepo repo0;
    private TestGitRepo repo1;
    private FreeStyleProject project;

    @Before
    public void setUp() throws Exception {
        gitStatus = new GitStatus();
        TaskListener listener = StreamTaskListener.fromStderr();
        repo0 = new TestGitRepo("repo0", tmp.newFolder(), listener);
        repo1 = new TestGitRepo("repo1", tmp.newFolder(), listener);
        project = r.createFreeStyleProject();
    }

    /**
     * verifies the fix for
     * https://issues.jenkins-ci.org/browse/JENKINS-26587
     */
    @Test
    public void commitNotificationIsPropagatedOnlyToSourceRepository() throws Exception {
        setupProject(repo0.remoteConfigs().get(0).getUrl(), "master", false);

        repo0.commit("repo0", repo1.johnDoe, "repo0 commit 1");
        final String repo1sha1 = repo1.commit("repo1", repo1.janeDoe, "repo1 commit 1");

        this.gitStatus.doNotifyCommit(repo1.remoteConfigs().get(0).getUrl(), null, repo1sha1);
        assertTrue("expected a build start on notify", r.isSomethingHappening());

        r.waitUntilNoActivity();

        final List<AbstractProject> projects = r.getInstance().getAllItems(AbstractProject.class);
        assertThat("should contain previously created project", projects.size(), greaterThan(0));

        for (AbstractProject project : projects) {
            final AbstractBuild lastBuild = project.getLastBuild();
            assertNotNull("one build should've been built after notification", lastBuild);
            r.assertBuildStatusSuccess(lastBuild);
        }
    }

    private SCMTrigger setupProject(String url, String branchString, boolean ignoreNotifyCommit) throws Exception {
        SCMTrigger trigger = new SCMTrigger("", ignoreNotifyCommit);
        setupProject(url, branchString, trigger);
        return trigger;
    }

    private void setupProject(String url, String branchString, SCMTrigger trigger) throws Exception {
        List<BranchSpec> branch = Collections.singletonList(new BranchSpec(branchString));

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

        if (trigger != null) project.addTrigger(trigger);
    }
}
