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
class WipeWorkspaceWorkflowTest {

        @Test
        void wipeWorkspaceTriggersDeprecatedWarning(
                JenkinsRule r,
                GitSampleRepoRule sampleRepo
        ) throws Exception {

                sampleRepo.init();

                WorkflowJob job =
                        r.jenkins.createProject(WorkflowJob.class,
                                "wipe-workspace-deprecated-warning");

                job.setDefinition(new CpsFlowDefinition(
                        """
                        node {
                          checkout(
                            scmGit(
                              extensions: [[
                                $class: 'WipeWorkspace'
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
                        "DEPRECATED: The 'Wipe out repository & force clone' extension is deprecated for Pipeline jobs. "
                                + "Pipeline users should use the deleteDir() step instead.",
                        run
                );
        }
}
