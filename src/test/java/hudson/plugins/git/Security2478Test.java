package hudson.plugins.git;

import hudson.model.Result;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static org.junit.Assert.assertFalse;

public class Security2478Test {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();


    @Before
    public void setUpAllowNonRemoteCheckout() {
        GitSCM.ALLOW_LOCAL_CHECKOUT = false;
    }

    @After
    public void disallowNonRemoteCheckout() {
        GitSCM.ALLOW_LOCAL_CHECKOUT = false;
    }

    @Issue("SECURITY-2478")
    @Test
    public void checkoutShouldNotAbortWhenLocalSourceAndRunningOnAgent() throws Exception {
        assertFalse("Non Remote checkout should be disallowed", GitSCM.ALLOW_LOCAL_CHECKOUT);
        r.createOnlineSlave();
        sampleRepo.init();
        sampleRepo.write("file", "v1");
        sampleRepo.git("commit", "--all", "--message=test commit");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pipeline");

        String script = "node {\n" +
                "   checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [], userRemoteConfigs: [[url: '" + sampleRepo.fileUrl() + "', credentialsId: '']]])\n" +
                "}";
        p.setDefinition(new CpsFlowDefinition(script, true));
        WorkflowRun run = r.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
        r.assertLogNotContains("aborted because it references a local directory, which may be insecure. " +
                "You can allow local checkouts anyway by setting the system property 'hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT' to true.", run);
    }

    @Issue("SECURITY-2478")
    @Test
    public void checkoutShouldAbortWhenSourceIsNonRemoteAndRunningOnController() throws Exception {
        assertFalse("Non Remote checkout should be disallowed", GitSCM.ALLOW_LOCAL_CHECKOUT);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pipeline");
        String workspaceDir = r.jenkins.getRootDir().getAbsolutePath();

        String path = "file://" + workspaceDir + File.separator + "jobName@script" + File.separator + "anyhmachash";
        String escapedPath = path.replace("\\", "\\\\"); // for windows
        String script = "node {\n" +
                "   checkout([$class: 'GitSCM', branches: [[name: '*/main']], extensions: [], userRemoteConfigs: [[" +
                "url: '" + escapedPath + "'," +
                " credentialsId: '']]])\n" +
                "}";
        p.setDefinition(new CpsFlowDefinition(script, true));
        WorkflowRun run = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("Checkout of Git remote '" + path + "' " +
                        "aborted because it references a local directory, which may be insecure. " +
                        "You can allow local checkouts anyway by setting the system property 'hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT' to true.", run);
    }
}
