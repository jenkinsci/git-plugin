package hudson.plugins.git.extensions.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import java.util.List;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@WithGitSampleRepo
@Issue("JENKINS-19022")
class LimitBuildsByBranchHistoryPipelineTest {

    @BeforeEach
    void beforeEach() {
        GitSCM.ALLOW_LOCAL_CHECKOUT = true;
    }

    @AfterEach
    void afterEach() {
        GitSCM.ALLOW_LOCAL_CHECKOUT = false;
    }

    @Test
    void pipelineBuildingOneBranchPerRunKeepsOneEntry(JenkinsRule r, GitSampleRepoRule repo) throws Exception {
        repo.init();
        for (int i = 1; i <= 5; i++) {
            repo.git("checkout", "-q", "-b", "feature-" + i, "master");
            repo.write("f" + i, "content " + i);
            repo.git("add", "f" + i);
            repo.git("commit", "--message=commit on feature-" + i);
        }
        WorkflowJob job = r.createProject(WorkflowJob.class, "limit-history");
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("BRANCH", "master")));
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                + "  checkout scmGit(branches: [[name: '*/' + params.BRANCH]],\n"
                + "                  extensions: [limitBuildsByBranchHistory()],\n"
                + "                  userRemoteConfigs: [[url: $/" + repo.fileUrl() + "/$]])\n"
                + "}\n", true));
        WorkflowRun run = null;
        for (int i = 1; i <= 5; i++) {
            run = r.assertBuildStatusSuccess(job.scheduleBuild2(0, new ParametersAction(
                    List.of(new StringParameterValue("BRANCH", "feature-" + i)))));
        }
        List<BuildData> data = run.getActions(BuildData.class);
        assertThat(data, hasSize(1));
        assertThat(data.get(0).getBuildsByBranchName().keySet(), contains(containsString("origin/feature-5")));
        r.assertLogContains("Removed 1 builds-by-branch entries", run);
    }
}
