package org.jenkinsci.plugins.gittagmessage;

import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public abstract class AbstractGitTagMessageExtensionTest<J extends Job<J, R>, R extends Run<J, R>> {

    @Rule public final JenkinsRule jenkins = new JenkinsRule();

    @Rule public final TemporaryFolder repoDir = new TemporaryFolder();

    private GitClient repo;

    /**
     * @param refSpec The refspec to check out.
     * @param branchSpec The branch spec to build.
     * @param useMostRecentTag true to use the most recent tag rather than the exact one.
     * @return A job configured with the test Git repo, given settings, and the Git Tag Message extension.
     */
    protected abstract J configureGitTagMessageJob(String refSpec, String branchSpec, boolean useMostRecentTag) throws Exception;

    /** @return A job configured with the test Git repo, default settings, and the Git Tag Message extension. */
    private J configureGitTagMessageJob() throws Exception {
        return configureGitTagMessageJob("", "**", false);
    }

    /** Asserts that the given build exported tag information, or not, if {@code null}. */
    protected abstract void assertBuildEnvironment(R run, String expectedName, String expectedMessage) throws Exception;

    @Before
    public void setUp() throws IOException, InterruptedException {
        // Set up a temporary git repository for each test case
        repo = Git.with(jenkins.createTaskListener(), null).in(repoDir.getRoot()).getClient();
        repo.init();
    }

    @Test
    public void commitWithoutTagShouldNotExportMessage() throws Exception {
        // Given a git repo without any tags
        repo.commit("commit 1");

        // When a build is executed
        J job = configureGitTagMessageJob();
        R build = buildJobAndAssertSuccess(job);

        // Then no git tag information should have been exported
        assertBuildEnvironment(build, null, null);
    }

    @Test
    public void commitWithEmptyTagMessageShouldNotExportMessage() throws Exception {
        // Given a git repo which has been tagged, but without a message
        repo.commit("commit 1");
        repo.tag("release-1.0", null);

        // When a build is executed
        J job = configureGitTagMessageJob();
        R run = buildJobAndAssertSuccess(job);

        // Then the git tag name message, but no message should have been exported
        assertBuildEnvironment(run, "release-1.0", null);
    }

    @Test
    public void commitWithTagShouldExportMessage() throws Exception {
        // Given a git repo which has been tagged
        repo.commit("commit 1");
        repo.tag("release-1.0", "This is the first release. ");

        // When a build is executed
        J job = configureGitTagMessageJob();
        R run = buildJobAndAssertSuccess(job);

        // Then the (trimmed) git tag message should have been exported
        assertBuildEnvironment(run, "release-1.0", "This is the first release.");
    }

    @Test
    public void commitWithMultipleTagsShouldExportMessage() throws Exception {
        // Given a commit with multiple tags pointing to it
        repo.commit("commit 1");
        repo.tag("release-candidate-1.0", "This is the first release candidate.");
        repo.tag("release-1.0", "This is the first release.");
        // TODO: JGit seems to list tags in alphabetical order rather than in reverse chronological order

        // When a build is executed
        J job = configureGitTagMessageJob();
        R run = buildJobAndAssertSuccess(job);

        // Then the most recent tag info should have been exported
        assertBuildEnvironment(run, "release-1.0", "This is the first release.");
    }

    @Test
    public void jobWithMatchingTagShouldExportThatTagMessage() throws Exception {
        // Given a commit with multiple tags pointing to it
        repo.commit("commit 1");
        repo.tag("alpha/1", "Alpha #1");
        repo.tag("beta/1", "Beta #1");
        repo.tag("gamma/1", "Gamma #1");

        // When a build is executed which is configured to only build beta/* tags
        J job = configureGitTagMessageJob("+refs/tags/beta/*:refs/remotes/origin/tags/beta/*",
                "*/tags/beta/*", false);
        R run = buildJobAndAssertSuccess(job);

        // Then the selected tag info should be exported, even although it's not the latest tag
        assertBuildEnvironment(run, "beta/1", "Beta #1");
    }

    @Test
    public void commitWithTagOnPreviousCommitWithConfigurationOptInShouldExportThatTagMessage() throws Exception {
        // Given a git repo which has been tagged on a previous commit
        repo.commit("commit 1");
        repo.tag("release-1.0", "This is the first release");
        repo.commit("commit 2");

        // When a build is executed
        J job = configureGitTagMessageJob("", "**", true);
        R run = buildJobAndAssertSuccess(job);

        // Then the git tag name message should be exported, even it is not on the current commit
        assertBuildEnvironment(run, "release-1.0", "This is the first release");
    }

    @Test
    public void commitWithMultipleTagsOnPreviousCommitWithConfigurationOptInShouldExportThatTagMessage() throws Exception {
        // Given a git repo which has been tagged on a previous commit with multiple tags
        repo.commit("commit 1");
        repo.tag("release-candidate-1.0", "This is the first release candidate.");
        repo.tag("release-1.0", "This is the first release.");
        repo.commit("commit 2");

        // When a build is executed
        J job = configureGitTagMessageJob("", "**", true);
        R run = buildJobAndAssertSuccess(job);

        // Then the most recent git tag name message should be exported, even it is not on the current commit
        assertBuildEnvironment(run, "release-1.0", "This is the first release.");
    }

    /**
     * Builds the given job and asserts that it succeeded, and the Git SCM ran.
     *
     * @param job The job to build.
     * @return The build that was executed.
     */
    private R buildJobAndAssertSuccess(J job) throws Exception {
        R build = jenkins.buildAndAssertSuccess(job);
        assertNotNull(build.getAction(BuildData.class));
        return build;
    }

}