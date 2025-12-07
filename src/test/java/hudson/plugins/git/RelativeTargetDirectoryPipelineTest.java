package hudson.plugins.git;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import jenkins.plugins.git.GitSampleRepoRule;

@WithJenkins
public class RelativeTargetDirectoryPipelineTest {

    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @BeforeEach
    public void setupRepo() throws Throwable {
        sampleRepo.before();
    }

    @AfterEach
    public void tearDownRepo() {
        sampleRepo.after();
    }

    @Test
    public void warningIsLoggedWhenUsedInPipeline(JenkinsRule j) throws Exception {
        // 1. Init a git repo
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo 'hello'");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "-m", "init");

        // 2. Create Pipeline Job
        WorkflowJob job = j.createProject(WorkflowJob.class, "test-job");
        String repoUrl = sampleRepo.toString();

        // 3. Script that uses the bad feature
        String pipelineScript =
                "node {\n" +
                        "  checkout([$class: 'GitSCM',\n" +
                        "    branches: [[name: '*/master']],\n" +
                        "    userRemoteConfigs: [[url: '" + repoUrl + "']],\n" +
                        "    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'subdir']]\n" +
                        "  ])\n" +
                        "}\n";
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        // 4. Run and Assert
        WorkflowRun run = j.buildAndAssertSuccess(job);
        j.assertLogContains("'Check out to a sub-directory' is not intended for use with Pipeline jobs", run);
    }
}