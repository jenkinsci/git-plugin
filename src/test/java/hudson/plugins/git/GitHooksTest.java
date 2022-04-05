package hudson.plugins.git;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import hudson.tools.ToolProperty;
import jenkins.plugins.git.CliGitCommand;
import jenkins.plugins.git.GitHooksConfiguration;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.LoggerRule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class GitHooksTest extends AbstractGitTestCase {

    @Rule
    public LoggerRule lr = new LoggerRule();

    @BeforeClass
    public static void setGitDefaults() throws Exception {
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
    }

    @Before
    public void setGitTool() {
        lr.record(GitHooksConfiguration.class.getName(), Level.ALL).capture(1024);
        GitTool tool = new GitTool("my-git", "git", Collections.<ToolProperty<?>>emptyList());
        rule.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(tool);
    }

    @After
    public void tearDown() {
        GitHooksConfiguration.get().setAllowedOnController(false);
        GitHooksConfiguration.get().setAllowedOnAgents(false);
        assertThat(lr.getMessages(), not(hasItem(startsWith("core.hooksPath explicitly set to "))));
    }

    @Test
    public void testPipelineFromScm() throws Exception {
        assumeNotJenkinsCiWindows();
        GitHooksConfiguration.get().setAllowedOnController(true);
        GitHooksConfiguration.get().setAllowedOnAgents(true);
        final DumbSlave agent = rule.createOnlineSlave(Label.get("somewhere"));
        commit("test.txt", "Test", johnDoe, "First");
        String jenkinsfile = lines(
                "node('somewhere') {",
                "  checkout scm",
                "  echo 'Hello Pipeline'",
                "}"
        );
        commit("Jenkinsfile", jenkinsfile, johnDoe, "Jenkinsfile");
        final WorkflowJob job = rule.createProject(WorkflowJob.class);
        final GitSCM scm = new GitSCM(
                this.createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("master")),
                null, "my-git", Collections.emptyList()
        );
        CpsScmFlowDefinition definition = new CpsScmFlowDefinition(scm, "Jenkinsfile");
        definition.setLightweight(false);
        job.setDefinition(definition);
        job.save();
        WorkflowRun run = rule.buildAndAssertSuccess(job);
        rule.assertLogContains("Hello Pipeline", run);

        final FilePath workspace = agent.getWorkspaceFor(job);
        assertNotNull(workspace);
        TemporaryFolder tf = new TemporaryFolder();
        tf.create();
        final File postCheckoutOutput1 = new File(tf.newFolder(), "svn-git-fun-post-checkout-1");
        final File postCheckoutOutput2 = new File(tf.newFolder(), "svn-git-fun-post-checkout-2");

        //Add hook on agent workspace
        FilePath hook = workspace.child(".git/hooks/post-checkout");
        createHookScriptAt(postCheckoutOutput1, hook);

        //TODO change when pom is updated to the related security fixes in "Pipeline: Groovy" > 2648.va9433432b33c
        final FilePath scriptWorkspace = rule.jenkins.getWorkspaceFor(job).withSuffix("@script");
        createHookScriptAt(postCheckoutOutput2, scriptWorkspace.child(".git/hooks/post-checkout"));

        commit("test.txt", "Second", johnDoe, "Second");
        commit("Jenkinsfile", "/*2*/\n" + jenkinsfile, johnDoe, "Jenkinsfile");

        //Allowed
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        Instant before = Instant.now().minus(2, ChronoUnit.SECONDS);
        run = rule.buildAndAssertSuccess(job);
        assertTrue(postCheckoutOutput1.exists());
        assertTrue(postCheckoutOutput2.exists());
        rule.assertLogContains("Hello Pipeline", run);
        Instant after = Instant.now().plus(2, ChronoUnit.SECONDS);
        checkFileOutput(postCheckoutOutput1, before, after);
        assertFalse(postCheckoutOutput1.exists());
        checkFileOutput(postCheckoutOutput2, before, after);
        assertFalse(postCheckoutOutput2.exists());

        commit("test.txt", "Third", johnDoe, "Third");
        commit("Jenkinsfile", "/*3*/\n" + jenkinsfile, johnDoe, "Jenkinsfile");
        //Denied
        GitHooksConfiguration.get().setAllowedOnController(false);
        GitHooksConfiguration.get().setAllowedOnAgents(false);
        run = rule.buildAndAssertSuccess(job);
        rule.assertLogContains("Hello Pipeline", run);
        assertFalse(postCheckoutOutput1.exists());
        assertFalse(postCheckoutOutput2.exists());

        commit("test.txt", "Four", johnDoe, "Four");
        commit("Jenkinsfile", "/*4*/\n" + jenkinsfile, johnDoe, "Jenkinsfile");
        //Allowed On Agent
        GitHooksConfiguration.get().setAllowedOnController(false);
        GitHooksConfiguration.get().setAllowedOnAgents(true);
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        before = Instant.now().minus(2, ChronoUnit.SECONDS);
        run = rule.buildAndAssertSuccess(job);
        assertFalse(postCheckoutOutput2.exists());
        assertTrue(postCheckoutOutput1.exists());
        rule.assertLogContains("Hello Pipeline", run);
        after = Instant.now().plus(2, ChronoUnit.SECONDS);
        checkFileOutput(postCheckoutOutput1, before, after);
        assertFalse(postCheckoutOutput1.exists());

        commit("test.txt", "Five", johnDoe, "Five");
        commit("Jenkinsfile", "/*5*/\n" + jenkinsfile, johnDoe, "Jenkinsfile");
        //Denied
        GitHooksConfiguration.get().setAllowedOnController(false);
        GitHooksConfiguration.get().setAllowedOnAgents(false);
        run = rule.buildAndAssertSuccess(job);
        rule.assertLogContains("Hello Pipeline", run);
        assertFalse(postCheckoutOutput1.exists());
        assertFalse(postCheckoutOutput2.exists());
    }

    private void createHookScriptAt(final File postCheckoutOutput, final FilePath hook) throws IOException, InterruptedException {
        final String nl = System.lineSeparator();
        StringBuilder scriptContent = new StringBuilder("#!/bin/bash -v").append(nl);
        scriptContent.append("date +%s > \"")
                .append(postCheckoutOutput.getAbsolutePath().replace("\\", "\\\\")) //Git bash does the bash escaping so need to do more escaping
                .append('"').append(nl);
        hook.write(scriptContent.toString(), Charset.defaultCharset().name());
        hook.chmod(0777);
    }

    private void checkFileOutput(final File postCheckoutOutput, final Instant before, final Instant after) throws IOException {
        assertTrue("Output file should exist", postCheckoutOutput.exists());
        final String s = FileUtils.readFileToString(postCheckoutOutput, Charset.defaultCharset()).trim();
        final Instant when = Instant.ofEpochSecond(Integer.parseInt(s));
        assertTrue("Sometime else", when.isAfter(before) && when.isBefore(after));
        assert postCheckoutOutput.delete();
    }

    @Test
    public void testPipelineCheckoutController() throws Exception {
        assumeNotJenkinsCiWindows();

        final WorkflowJob job = setupAndRunPipelineCheckout("master");
        WorkflowRun run;
        commit("Commit3", janeDoe, "Commit number 3");
        GitHooksConfiguration.get().setAllowedOnController(true);
        run = rule.buildAndAssertSuccess(job);
        rule.assertLogContains("h4xor3d", run);
        GitHooksConfiguration.get().setAllowedOnController(false);
        GitHooksConfiguration.get().setAllowedOnAgents(true);
        commit("Commit4", janeDoe, "Commit number 4");
        run = rule.buildAndAssertSuccess(job);
        rule.assertLogNotContains("h4xor3d", run);
    }

    @Test
    public void testPipelineCheckoutAgent() throws Exception {
        assumeNotJenkinsCiWindows();

        rule.createOnlineSlave(Label.get("belsebob"));
        final WorkflowJob job = setupAndRunPipelineCheckout("belsebob");
        WorkflowRun run;
        commit("Commit3", janeDoe, "Commit number 3");
        GitHooksConfiguration.get().setAllowedOnAgents(true);
        run = rule.buildAndAssertSuccess(job);
        rule.assertLogContains("h4xor3d", run);
        GitHooksConfiguration.get().setAllowedOnAgents(false);
        GitHooksConfiguration.get().setAllowedOnController(true);
        commit("Commit4", janeDoe, "Commit number 4");
        run = rule.buildAndAssertSuccess(job);
        rule.assertLogNotContains("h4xor3d", run);
    }

    private WorkflowJob setupAndRunPipelineCheckout(String node) throws Exception {
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");

        final WorkflowJob job = rule.createProject(WorkflowJob.class);
        final String uri = testRepo.gitDir.getAbsolutePath().replace("\\", "/");
        job.setDefinition(new CpsFlowDefinition(lines(
                "node('" + node + "') {",
                "  checkout([$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[url: '" + uri + "']]])",
                "  if (!fileExists('.git/hooks/post-checkout')) {",
                "    writeFile file: '.git/hooks/post-checkout', text: \"#!/bin/bash\\necho h4xor3d\"",
                "    if (isUnix()) {",
                "      sh 'chmod +x .git/hooks/post-checkout'",
                "    }",
                "  } else {",
                "    if (isUnix()) {",
                "      sh 'git checkout -B test origin/master'",
                "    } else {",
                "      bat 'git.exe checkout -B test origin/master'",
                "    }",
                "  }",
                "}")
                , true));
        WorkflowRun run = rule.buildAndAssertSuccess(job);
        rule.assertLogNotContains("h4xor3d", run);
        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        run = rule.buildAndAssertSuccess(job);
        rule.assertLogNotContains("h4xor3d", run);
        return job;
    }

    /**
     * Approximates a multiline string in Java.
     *
     * @param lines the lines to concatenate with a newline separator
     * @return the concatenated multiline string
     */
    private static String lines(String... lines) {
        return String.join("\n", lines);
    }

    /**
     * Assume that it is not running on a ci.jenkins.io Windows agent.
     *
     * The tests are tested and confirmed working on Windows,
     * but for unknown reason is not working on the Win agents on ci.jenkins.io.
     */
    private void assumeNotJenkinsCiWindows() {
        final String jenkinsUrl = System.getenv("JENKINS_URL");
        assumeFalse(Functions.isWindows() && jenkinsUrl.contains("ci.jenkins.io"));
    }
}
