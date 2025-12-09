package hudson.plugins.git;

import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WithJenkins
@WithGitSampleRepo
public class RelativeTargetDirectoryPipelineTest {

    private JenkinsRule r;
    private GitSampleRepoRule sampleRepo;

    @BeforeEach
    void beforeEach(JenkinsRule rule, GitSampleRepoRule repo) throws Exception {
        this.r = rule;
        this.sampleRepo = repo;

        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo 'hello'");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "-m", "init");
    }

    @Test
    void warningIsLoggedWhenUsedInPipeline() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "test-job");
        String repoUrl = sampleRepo.toString();

        String pipelineScript =
                "node {\n" +
                        "  checkout([$class: 'GitSCM',\n" +
                        "    branches: [[name: '*/master']],\n" +
                        "    userRemoteConfigs: [[url: '" + repoUrl + "']],\n" +
                        "    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'subdir']]\n" +
                        "  ])\n" +
                        "}\n";
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        WorkflowRun run = r.buildAndAssertSuccess(job);
        r.assertLogContains("'Check out to a sub-directory' is not intended for use with Pipeline jobs", run);
        r.assertLogContains("Please use the 'dir' step instead", run);
    }
}