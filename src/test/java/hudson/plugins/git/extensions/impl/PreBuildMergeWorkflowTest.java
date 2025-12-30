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
    void preBuildMergeTriggersDeprecatedWarning(
            JenkinsRule r,
            GitSampleRepoRule sampleRepo
    ) throws Exception {

        sampleRepo.init();

        WorkflowJob job =
                r.jenkins.createProject(WorkflowJob.class,
                        "pre-build-merge-deprecated-warning");

        job.setDefinition(new CpsFlowDefinition(
                """
                node {
                  checkout(
                    scmGit(
                      extensions: [[
                        $class: 'PreBuildMerge',
                        options: [
                          mergeRemote: 'origin',
                          mergeTarget: 'master'
                        ]
                      ]],
                      userRemoteConfigs: [[url: '%s']]
                    )
                  )
                }
                """.formatted(sampleRepo),
                true
        ));

        WorkflowRun run = r.buildAndAssertSuccess(job);

        r.waitForMessage(
                "DEPRECATED: The 'Merge before build' extension is deprecated for Pipeline jobs. "
                        + "Pipeline users should perform merges explicitly using shell steps (e.g. sh 'git merge').",
                run
        );
    }
}
