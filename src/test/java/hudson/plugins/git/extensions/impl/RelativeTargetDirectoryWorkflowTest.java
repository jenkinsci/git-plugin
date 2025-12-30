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
class RelativeTargetDirectoryWorkflowTest {

    @Test
    void relativeTargetDirectoryTriggersDeprecatedWarning(
            JenkinsRule r,
            GitSampleRepoRule sampleRepo
    ) throws Exception {

        sampleRepo.init();

        WorkflowJob job =
                r.jenkins.createProject(WorkflowJob.class,
                        "relative-target-directory-deprecated-warning");

        job.setDefinition(new CpsFlowDefinition(
                """
                node {
                  checkout(
                    scmGit(
                      extensions: [[
                        $class: 'RelativeTargetDirectory',
                        relativeTargetDir: 'subdir'
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
                "DEPRECATED: Relative target directory is deprecated for Pipeline jobs. "
                        + "Use the 'dir' step instead.",
                run
        );
    }
}
