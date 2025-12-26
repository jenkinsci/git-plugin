package hudson.plugins.git.extensions.impl;

import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@WithGitSampleRepo
class PreBuildMergeWorkflowTest {

    @Test
    void preBuildMergeDeprecationWarning(JenkinsRule r, GitSampleRepoRule sampleRepo) throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', extensions: [[$class: 'PreBuildMerge', options: [mergeRemote: 'origin', mergeTarget: 'master']]],\n"
                        + "      userRemoteConfigs: [[url: $/" + sampleRepo + "/$]]]\n"
                        + "  )\n"
                        + "}",
                true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("DEPRECATED:", b);
        r.waitForMessage("git merge", b);
    }
}
