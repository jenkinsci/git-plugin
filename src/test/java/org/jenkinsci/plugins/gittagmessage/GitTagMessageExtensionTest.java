package org.jenkinsci.plugins.gittagmessage;

import hudson.Functions;
import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.Shell;

import java.util.Collections;

import static org.jenkinsci.plugins.gittagmessage.GitTagMessageAction.ENV_VAR_NAME_MESSAGE;
import static org.jenkinsci.plugins.gittagmessage.GitTagMessageAction.ENV_VAR_NAME_TAG;

public class GitTagMessageExtensionTest extends AbstractGitTagMessageExtensionTest<FreeStyleProject, FreeStyleBuild> {

    /**
     * @param refSpec The refspec to check out.
     * @param branchSpec The branch spec to build.
     * @param useMostRecentTag true to use the most recent tag rather than the exact one.
     * @return A job configured with the test Git repo, given settings, and the Git Tag Message extension.
     */
    protected FreeStyleProject configureGitTagMessageJob(String refSpec, String branchSpec, boolean useMostRecentTag) throws Exception {
        GitTagMessageExtension extension = new GitTagMessageExtension();
        extension.setUseMostRecentTag(useMostRecentTag);
        UserRemoteConfig remote = new UserRemoteConfig(repoDir.getRoot().getAbsolutePath(), "origin", refSpec, null);
        GitSCM scm = new GitSCM(
                Collections.singletonList(remote),
                Collections.singletonList(new BranchSpec(branchSpec)),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>singletonList(extension));

        FreeStyleProject job = jenkins.createFreeStyleProject();
        job.getBuildersList().add(createEnvEchoBuilder("tag", ENV_VAR_NAME_TAG));
        job.getBuildersList().add(createEnvEchoBuilder("msg", ENV_VAR_NAME_MESSAGE));
        job.setScm(scm);
        return job;
    }

    /** Asserts that the given build exported tag information, or not, if {@code null}. */
    protected void assertBuildEnvironment(FreeStyleBuild build, String expectedName, String expectedMessage)
            throws Exception {
        // In the freestyle shell step, unknown environment variables are returned as empty strings
        jenkins.assertLogContains(String.format("tag='%s'", Util.fixNull(expectedName)), build);
        jenkins.assertLogContains(String.format("msg='%s'", Util.fixNull(expectedMessage)), build);
    }

    private static Builder createEnvEchoBuilder(String key, String envVarName) {
        if (Functions.isWindows()) {
            return new BatchFile(String.format("echo %s='%%%s%%'", key, envVarName));
        }
        return new Shell(String.format("echo \"%s='${%s}'\"", key, envVarName));
    }

}
