/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Label;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitStatus;
import hudson.plugins.git.GitTagAction;
import hudson.plugins.git.util.BuildData;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import jenkins.util.VirtualFile;
import jenkins.plugins.git.CliGitCommand;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@TestMethodOrder(MethodOrderer.Random.class)
@WithJenkins
@WithGitSampleRepo
class GitStepTest {

    private JenkinsRule r;

    private GitSampleRepoRule sampleRepo;
    private GitSampleRepoRule otherRepo;

    @BeforeAll
    static void beforeAll() throws Exception {
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
    }

    @BeforeEach
    void beforeEach(JenkinsRule rule, GitSampleRepoRule repo1, GitSampleRepoRule repo2) {
        r = rule;
        sampleRepo = repo1;
        otherRepo = repo2;
    }

    private static final Instant START_TIME = Instant.now();

    private static final int MAX_SECONDS_FOR_THESE_TESTS = 200;

    private boolean isTimeAvailable() {
        String env = System.getenv("CI");
        if (!Boolean.parseBoolean(env)) {
            // Run all tests when not in CI environment
            return true;
        }
        return Duration.between(START_TIME, Instant.now()).toSeconds() <= MAX_SECONDS_FOR_THESE_TESTS;
    }

    private static String NOTIFY_COMMIT_ACCESS_CONTROL_ORIGINAL = GitStatus.NOTIFY_COMMIT_ACCESS_CONTROL;

    @AfterEach
    void afterEach() {
        GitStatus.NOTIFY_COMMIT_ACCESS_CONTROL = NOTIFY_COMMIT_ACCESS_CONTROL_ORIGINAL;
    }

    @Test
    void roundtrip() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        GitStep step = new GitStep("git@github.com:jenkinsci/workflow-plugin.git");
        Step roundtrip = new StepConfigTester(r).configRoundTrip(step);
        r.assertEqualDataBoundBeans(step, roundtrip);
    }

    @Test
    void roundtrip_withcredentials() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        IdCredentials c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, null, "user", "password-needs-to-be-14");
        CredentialsProvider.lookupStores(r.jenkins).iterator().next()
                .addCredentials(Domain.global(), c);
        GitStep step = new GitStep("git@github.com:jenkinsci/workflow-plugin.git");
        step.setCredentialsId(c.getId());
        Step roundtrip = new StepConfigTester(r).configRoundTrip(step);
        r.assertEqualDataBoundBeans(step, roundtrip);
    }

    @Test
    void basicCloneAndUpdate() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        r.createOnlineSlave(Label.get("remote"));
        p.setDefinition(new CpsFlowDefinition(
            "node('remote') {\n" +
            "    ws {\n" +
            "        git(url: $/" + sampleRepo + "/$, poll: false, changelog: false)\n" +
            "        archive '**'\n" +
            "    }\n" +
            "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("Cloning the remote Git repository", b); // GitSCM.retrieveChanges
        assertTrue(b.getArtifactManager().root().child("file").isFile());
        sampleRepo.write("nextfile", "");
        sampleRepo.git("add", "nextfile");
        sampleRepo.git("commit", "--message=next");
        b = r.buildAndAssertSuccess(p);
        r.waitForMessage("Fetching changes from the remote Git repository", b); // GitSCM.retrieveChanges
        assertTrue(b.getArtifactManager().root().child("nextfile").isFile());
    }

    @Test
    void changelogAndPolling() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger("")); // no schedule, use notifyCommit only
        r.createOnlineSlave(Label.get("remote"));
        p.setDefinition(new CpsFlowDefinition(
            "node('remote') {\n" +
            "    ws {\n" +
            "        git($/" + sampleRepo + "/$)\n" +
            "        def tokenBranch = tm '${GIT_BRANCH,fullName=false}'\n" +
            "        echo \"token macro expanded branch is ${tokenBranch}\"\n" +
            "    }\n" +
            "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("token macro expanded branch is remotes/origin/master", b); // Unexpected but current behavior
        sampleRepo.write("nextfile", "");
        sampleRepo.git("add", "nextfile");
        sampleRepo.git("commit", "--message=next");
        sampleRepo.notifyCommit(r);
        b = p.getLastBuild();
        assertEquals(2, b.number);
        r.waitForMessage("Fetching changes from the remote Git repository", b);
        r.waitForMessage("token macro expanded branch is remotes/origin/master", b); // Unexpected but current behavior
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(1, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("git", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[nextfile]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }

    @Test
    void multipleSCMs() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        sampleRepo.init();
        otherRepo.init();
        otherRepo.write("otherfile", "");
        otherRepo.git("add", "otherfile");
        otherRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger(""));
        p.setQuietPeriod(3); // so it only does one build
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "    ws {\n" +
            "        dir('main') {\n" +
            "            git($/" + sampleRepo + "/$)\n" +
            "        }\n" +
            "        dir('other') {\n" +
            "            git($/" + otherRepo + "/$)\n" +
            "        }\n" +
            "        archive '**'\n" +
            "    }\n" +
            "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        VirtualFile artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file").isFile());
        assertTrue(artifacts.child("other/otherfile").isFile());
        sampleRepo.write("file2", "");
        sampleRepo.git("add", "file2");
        sampleRepo.git("commit", "--message=file2");
        otherRepo.write("otherfile2", "");
        otherRepo.git("add", "otherfile2");
        otherRepo.git("commit", "--message=otherfile2");
        sampleRepo.notifyCommit(r);
        otherRepo.notifyCommit(r);
        b = p.getLastBuild();
        assertEquals(2, b.number);
        artifacts = b.getArtifactManager().root();
        assertTrue(artifacts.child("main/file2").isFile());
        assertTrue(artifacts.child("other/otherfile2").isFile());
        Iterator<? extends SCM> scms = p.getSCMs().iterator();
        assertTrue(scms.hasNext());
        assertEquals(sampleRepo.toString(), ((GitSCM) scms.next()).getRepositories().get(0).getURIs().get(0).toString());
        assertTrue(scms.hasNext());
        assertEquals(otherRepo.toString(), ((GitSCM) scms.next()).getRepositories().get(0).getURIs().get(0).toString());
        assertFalse(scms.hasNext());
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(2, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("git", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[file2]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
        changeSet = changeSets.get(1);
        iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals("[otherfile2]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }

    @Issue("JENKINS-29326")
    @Test
    void identicalGitSCMs() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        sampleRepo.init();
        otherRepo.init();
        otherRepo.write("firstfile", "");
        otherRepo.git("add", "firstfile");
        otherRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "    dir('main') {\n" +
            "        git($/" + otherRepo + "/$)\n" +
            "    }\n" +
            "    dir('other') {\n" +
            "        git($/" + otherRepo + "/$)\n" +
            "    }\n" +
            "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        assertEquals(1, b.getActions(BuildData.class).size());
        assertEquals(0, b.getActions(GitTagAction.class).size());
        assertEquals(0, b.getChangeSets().size());
        assertEquals(1, p.getSCMs().size());

        otherRepo.write("secondfile", "");
        otherRepo.git("add", "secondfile");
        otherRepo.git("commit", "--message=second");
        WorkflowRun b2 = r.buildAndAssertSuccess(p);
        assertEquals(1, b2.getActions(BuildData.class).size());
        assertEquals(0, b2.getActions(GitTagAction.class).size());
        assertEquals(1, b2.getChangeSets().size());
        assertFalse(b2.getChangeSets().get(0).isEmptySet());
        assertEquals(1, p.getSCMs().size());
    }

    @Test
    void commitToWorkspace() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "def rungit(cmd) {def gitcmd = \"git ${cmd}\"; if (isUnix()) {sh gitcmd} else {bat gitcmd}}\n" +
            "node {\n" +
            "  git url: $/" + sampleRepo + "/$\n" +
            "  writeFile file: 'file', text: 'edited by build'\n" +
            "  rungit 'config --local commit.gpgsign false'\n" +
            "  rungit 'config --local tag.gpgSign false'\n" +
            "  rungit 'commit --all --message=edits'\n" +
            "  rungit 'show master'\n" +
            "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("+edited by build", b);
    }

    private WorkflowJob createJob() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
        p.addTrigger(new SCMTrigger("")); // no schedule, use notifyCommit only
        p.setDefinition(new CpsFlowDefinition(
            """
            node {
                error('this should never be called')
            }
            """, true));
        return p;
    }

    @Test
    @Issue("SECURITY-284")
    void testDoNotifyCommitWithInvalidApiToken() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        createJob();
        String response = sampleRepo.notifyCommitWithResults(r, GitSampleRepoRule.INVALID_NOTIFY_COMMIT_TOKEN);
        assertThat(response, containsString("Invalid access token"));
    }

    @Test
    @Issue("SECURITY-284")
    void testDoNotifyCommitWithAllowModeRandomValue() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        createJob();
        String response = sampleRepo.notifyCommitWithResults(r, null);
        assertThat(response, containsString("An access token is required. Please refer to Git plugin documentation (https://plugins.jenkins.io/git/#plugin-content-push-notification-from-repository) for details."));
    }

    @Test
    @Issue("SECURITY-284")
    void testDoNotifyCommitWithSha1AndAllowModePollWithInvalidToken() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        GitStatus.NOTIFY_COMMIT_ACCESS_CONTROL = "disabled-for-polling";
        createJob();
        /* sha1 is ignored because invalid access token is provided */
        String sha1 = "4b714b66959463a98e9dfb1983db5a39a39fa6d6";
        String response = sampleRepo.notifyCommitWithResults(r, GitSampleRepoRule.INVALID_NOTIFY_COMMIT_TOKEN, sha1);
        assertThat(response, containsString("Invalid access token"));
    }

    @Test
    @Issue("SECURITY-284")
    void testDoNotifyCommitWithSha1AndAllowModePoll() throws Exception {
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        GitStatus.NOTIFY_COMMIT_ACCESS_CONTROL = "disabled-for-polling";
        createJob();
        /* sha1 is ignored because no access token is provided */
        String sha1 = "4b714b66959463a98e9dfb1983db5a39a39fa6d6";
        String response = sampleRepo.notifyCommitWithResults(r, null, sha1);
        assertThat(response, containsString("An access token is required when using the sha1 parameter"));
    }

}
