package hudson.plugins.git.browser;

import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitLabWorkflowTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test
    public void checkoutWithVersion() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                + "  checkout(\n"
                + "    [$class: 'GitSCM', browser: [$class: 'GitLab',\n"
                + "     repoUrl: 'https://a.org/a/b', version: '9.0'],\n"
                + "    userRemoteConfigs: [[url: $/" + sampleRepo + "/$]]]\n"
                + "  )"
                + "}", true));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    public void checkoutWithoutVersion() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', browser: [$class: 'GitLab',\n"
                        + "     repoUrl: 'https://a.org/a/b'],\n"
                        + "    userRemoteConfigs: [[url: $/" + sampleRepo + "/$]]]\n"
                        + "  )"
                        + "}", true));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }
}
