package hudson.plugins.git;

import static hudson.Functions.isWindows;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import hudson.util.RunList;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

@TestMethodOrder(MethodOrderer.Random.class)
public abstract class SCMTriggerTest extends AbstractGitProject {

    private ZipFile namespaceRepoZip;
    private Properties namespaceRepoCommits;
    private ExecutorService singleThreadExecutor;
    protected boolean expectChanges = false;

    private static final Instant START_TIME = Instant.now();

    private static final int MAX_SECONDS_FOR_THESE_TESTS = 120;

    private boolean isTimeAvailable() {
        String env = System.getenv("CI");
        if (!Boolean.parseBoolean(env)) {
            // Run all tests when not in CI environment
            return true;
        }
        return Duration.between(START_TIME, Instant.now()).toSeconds() <= MAX_SECONDS_FOR_THESE_TESTS;
    }

    @TempDir
    private File tempFolder;

    @BeforeEach
    public void beforeEach() throws Exception {
        expectChanges = false;
        namespaceRepoZip = new ZipFile("src/test/resources/namespaceBranchRepo.zip");
        namespaceRepoCommits = parseLsRemote(new File("src/test/resources/namespaceBranchRepo.ls-remote"));
        singleThreadExecutor = Executors.newSingleThreadExecutor();
    }

    protected abstract EnforceGitClient getGitClient();

    protected abstract boolean isDisableRemotePoll();

    @Test
    public void testNamespaces_with_refsHeadsMaster() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "refs/heads/master",
                namespaceRepoCommits.getProperty("refs/heads/master"),
                "origin/master");
    }

    @Test
    public void testNamespaces_with_remotesOriginMaster() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "remotes/origin/master",
                namespaceRepoCommits.getProperty("refs/heads/master"),
                "origin/master");
    }

    @Test
    public void testNamespaces_with_refsRemotesOriginMaster() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "refs/remotes/origin/master",
                namespaceRepoCommits.getProperty("refs/heads/master"),
                "origin/master");
    }

    @Test
    public void testNamespaces_with_master() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "master",
                namespaceRepoCommits.getProperty("refs/heads/master"),
                "origin/master");
    }

    @Test
    public void testNamespaces_with_namespace1Master() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "a_tests/b_namespace1/master",
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace1/master"),
                "origin/a_tests/b_namespace1/master");
    }

    @Test
    public void testNamespaces_with_refsHeadsNamespace1Master() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "refs/heads/a_tests/b_namespace1/master",
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace1/master"),
                "origin/a_tests/b_namespace1/master");
    }

    @Test
    public void testNamespaces_with_namespace2Master() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "a_tests/b_namespace2/master",
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace2/master"),
                "origin/a_tests/b_namespace2/master");
    }

    @Test
    public void testNamespaces_with_refsHeadsNamespace2Master() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "refs/heads/a_tests/b_namespace2/master",
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace2/master"),
                "origin/a_tests/b_namespace2/master");
    }

    @Test
    public void testNamespaces_with_namespace3_feature3_sha1() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace3/feature3"),
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace3/feature3"),
                "detached");
    }

    @Test
    public void testNamespaces_with_namespace3_feature3_branchName() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "a_tests/b_namespace3/feature3",
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace3/feature3"),
                "origin/a_tests/b_namespace3/feature3");
    }

    @Test
    public void testNamespaces_with_refsHeadsNamespace3_feature3_sha1() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace3/feature3"),
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace3/feature3"),
                "detached");
    }

    @Test
    public void testNamespaces_with_refsHeadsNamespace3_feature3_branchName() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "refs/heads/a_tests/b_namespace3/feature3",
                namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace3/feature3"),
                "origin/a_tests/b_namespace3/feature3");
    }

    @Test
    public void testTags_with_TagA() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "TagA",
                namespaceRepoCommits.getProperty("refs/tags/TagA"),
                "TagA"); // TODO: What do we expect!?
    }

    @Test
    public void testTags_with_TagBAnnotated() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "TagBAnnotated",
                namespaceRepoCommits.getProperty("refs/tags/TagBAnnotated^{}"),
                "TagBAnnotated"); // TODO: What do we expect!?
    }

    @Test
    public void testTags_with_refsTagsTagA() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "refs/tags/TagA",
                namespaceRepoCommits.getProperty("refs/tags/TagA"),
                "refs/tags/TagA"); // TODO: What do we expect!?
    }

    @Test
    public void testTags_with_refsTagsTagBAnnotated() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "refs/tags/TagBAnnotated",
                namespaceRepoCommits.getProperty("refs/tags/TagBAnnotated^{}"),
                "refs/tags/TagBAnnotated");
    }

    @Test
    public void testCommitAsBranchSpec_feature4_sha1() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                namespaceRepoCommits.getProperty("refs/heads/b_namespace3/feature4"),
                namespaceRepoCommits.getProperty("refs/heads/b_namespace3/feature4"),
                "detached");
    }

    @Test
    public void testCommitAsBranchSpec_feature4_branchName() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                "refs/heads/b_namespace3/feature4",
                namespaceRepoCommits.getProperty("refs/heads/b_namespace3/feature4"),
                "origin/b_namespace3/feature4");
    }

    @Test
    public void testCommitAsBranchSpec() throws Exception {
        if (isWindows()) { // Low value test - skip on Windows
            return;
        }
        assumeTrue(isTimeAvailable(), "Test class max time " + MAX_SECONDS_FOR_THESE_TESTS + " exceeded");
        check(
                namespaceRepoZip,
                namespaceRepoCommits,
                namespaceRepoCommits.getProperty("refs/heads/b_namespace3/master"),
                namespaceRepoCommits.getProperty("refs/heads/b_namespace3/master"),
                "detached");
    }

    public void check(
            ZipFile repoZip,
            Properties commits,
            String branchSpec,
            String expected_GIT_COMMIT,
            String expected_GIT_BRANCH)
            throws Exception {
        String remote = prepareRepo(repoZip);

        FreeStyleProject project = setupProject(
                Collections.singletonList(new UserRemoteConfig(remote, null, null, null)),
                Collections.singletonList(new BranchSpec(branchSpec)),
                // empty scmTriggerSpec, SCMTrigger triggered manually
                "",
                isDisableRemotePoll(),
                getGitClient());

        // Speedup test - avoid waiting 1 minute
        triggerSCMTrigger(project.getTrigger(SCMTrigger.class));

        FreeStyleBuild build1 = waitForBuildFinished(project, 1, 60000);
        assertNotNull(build1, "Job has not been triggered");

        TaskListener listener = StreamTaskListener.fromStderr();
        PollingResult poll = project.poll(listener);
        assertFalse(poll.hasChanges(), "Expected and actual polling results disagree");

        // Speedup test - avoid waiting 1 minute
        triggerSCMTrigger(project.getTrigger(SCMTrigger.class)).get(20, SECONDS);

        FreeStyleBuild build2 = waitForBuildFinished(project, 2, 2000);
        assertNull(build2, "Found build 2 although no new changes and no multi candidate build");

        assertEquals(
                expected_GIT_COMMIT,
                build1.getEnvironment(null).get("GIT_COMMIT"),
                "Unexpected GIT_COMMIT");
        assertEquals(
                expected_GIT_BRANCH,
                build1.getEnvironment(null).get("GIT_BRANCH"),
                "Unexpected GIT_BRANCH");
    }

    private String prepareRepo(ZipFile repoZip) throws IOException {
        File tempRemoteDir = newFolder(tempFolder, "junit");
        extract(repoZip, tempRemoteDir);
        return tempRemoteDir.getAbsolutePath();
    }

    private Future<Void> triggerSCMTrigger(final SCMTrigger trigger) {
        if (trigger == null) return null;
        Callable<Void> callable = () -> {
            trigger.run();
            return null;
        };
        return singleThreadExecutor.submit(callable);
    }

    private FreeStyleBuild waitForBuildFinished(FreeStyleProject project, int expectedBuildNumber, long timeout)
            throws Exception {
        long endTime = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < endTime) {
            RunList<FreeStyleBuild> builds = project.getBuilds();
            for (FreeStyleBuild build : builds) {
                if (build.getNumber() == expectedBuildNumber) {
                    if (build.getResult() != null) return build;
                    break; // Wait until build finished
                }
            }
            Thread.sleep(10);
        }
        return null;
    }

    private Properties parseLsRemote(File file) throws IOException {
        Properties properties = new Properties();
        Pattern pattern = Pattern.compile("([a-f0-9]{40})\\s*(.*)");
        for (String lineO : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String line = lineO.trim();
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                properties.setProperty(matcher.group(2), matcher.group(1));
            } else {
                System.err.println("ls-remote pattern does not match '" + line + "'");
            }
        }
        return properties;
    }

    private void extract(ZipFile zipFile, File outputDir) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File entryDestination = new File(outputDir, entry.getName());
            entryDestination.getParentFile().mkdirs();
            if (entry.isDirectory()) entryDestination.mkdirs();
            else {
                try (InputStream in = zipFile.getInputStream(entry)) {
                    Files.copy(in, entryDestination.toPath());
                }
            }
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
