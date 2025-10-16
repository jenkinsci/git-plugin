package jenkins.plugins.git;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertNull;

@WithJenkins
class GitCommitDetailTest {

    @Test
    void testIsHiddenIfNoScm(JenkinsRule j) throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition("""
                echo 'hello world'
                """, true));

        WorkflowRun workflowRun = j.buildAndAssertStatus(Result.SUCCESS, project);

        GitCommitDetail gitCommitDetail = new GitCommitDetail(workflowRun, null);

        assertNull(gitCommitDetail.getIconClassName());

    }
}