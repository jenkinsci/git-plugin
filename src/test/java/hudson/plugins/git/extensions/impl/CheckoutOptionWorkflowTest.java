package hudson.plugins.git.extensions.impl;

import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CheckoutOptionWorkflowTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test
    public void checkoutTimeout() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                + "  checkout(\n"
                + "    [$class: 'GitSCM', extensions: [[$class: 'CheckoutOption', timeout: 1234]],\n"
                + "      userRemoteConfigs: [[url: $/" + sampleRepo + "/$]]]\n"
                + "  )"
                + "}"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("# timeout=1234", b);
    }
}
