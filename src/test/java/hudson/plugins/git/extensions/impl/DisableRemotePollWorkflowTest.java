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
class DisableRemotePollWorkflowTest {

        @Test
        void disableRemotePollTriggersDeprecatedWarning(
                JenkinsRule r,
                GitSampleRepoRule sampleRepo
        ) throws Exception {

                sampleRepo.init();

                WorkflowJob job =
                        r.jenkins.createProject(WorkflowJob.class,
                                "disable-remote-poll-deprecated-warning");

                job.setDefinition(new CpsFlowDefinition(
                        """
                        node {
                          checkout(
                            scmGit(
                              extensions: [[
                                $class: 'DisableRemotePoll'
                              ]],
                              userRemoteConfigs: [[url: '%s']]
                            )
                          )
                        }
                        """.formatted(sampleRepo.toString().replace("\\", "\\\\")),
                        true
                ));

                WorkflowRun run = r.buildAndAssertSuccess(job);

                r.waitForMessage(
                        "DEPRECATED: The extension that requires a workspace for polling is deprecated for Pipeline jobs. "
                                + "Use Pipeline-native SCM polling instead.",
                        run
                );
        }
}
