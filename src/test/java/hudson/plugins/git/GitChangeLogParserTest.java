package hudson.plugins.git;

import hudson.EnvVars;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitAPIImpl;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.LogRecorder;

/**
 * Unit tests of {@link GitChangeLogParser}
 */
class GitChangeLogParserTest {

    @TempDir
    private File tmpFolder;

    private final String firstMessageTruncated = "123456789 123456789 123456789 123456789 123456789 123456789 123456789 1";
    private final String firstMessage = firstMessageTruncated + " 345 789";

    /* Test duplicate changes filtered from parsed CLI git change set list. */
    @Test
    void testDuplicatesFilteredCliGit() throws Exception {
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).using("Default").in(new File(".")).getClient();
        assertThat(gitClient, instanceOf(CliGitAPIImpl.class));
        /* JENKINS-29977 notes that CLI git impl truncates summary message - confirm default behavior retained */
        generateDuplicateChanges(gitClient, firstMessageTruncated);
    }

    /* Test duplicate changes filtered from parsed JGit change set list. */
    @Test
    void testDuplicatesFilteredJGit() throws Exception {
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).using("jgit").in(new File(".")).getClient();
        assertThat(gitClient, instanceOf(JGitAPIImpl.class));
        /* JENKINS-29977 notes that JGit impl retains full summary message - confirm default behavior retained */
        generateDuplicateChanges(gitClient, firstMessage);
    }

    /* Test that lines beyond the per-commit threshold are reported in the log. */
    @Test
    void testIgnoredLinesLogged() throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(null, true);
        List<String> changelog = new ArrayList<>();
        changelog.add("commit 123abc456def");
        for (int i = 0; i < 1004; i++) {
            changelog.add("    line " + i);
        }
        try (LogRecorder recorder = new LogRecorder().record(GitChangeLogParser.class, Level.WARNING).capture(10)) {
            parser.parse(changelog);
            assertThat(recorder.getMessages(), contains("Ignored 5 lines of changelog for commit 123abc456def"));
        }
    }

    /* Test that a large ignored count is logged without a thousands separator. */
    @Test
    void testIgnoredLinesCountNotGrouped() throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(null, true);
        List<String> changelog = new ArrayList<>();
        changelog.add("commit 123abc456def");
        for (int i = 0; i < 2499; i++) {
            changelog.add("    line " + i);
        }
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.US); // US formatting would write 1500 as "1,500"
        try (LogRecorder recorder = new LogRecorder().record(GitChangeLogParser.class, Level.WARNING).capture(10)) {
            parser.parse(changelog);
            assertThat(recorder.getMessages(), contains("Ignored 1500 lines of changelog for commit 123abc456def"));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    /* Test that a commit within the threshold logs nothing. */
    @Test
    void testNoIgnoredLinesNotLogged() throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(null, true);
        try (LogRecorder recorder = new LogRecorder().record(GitChangeLogParser.class, Level.ALL).capture(10)) {
            parser.parse(List.of("commit 123abc456def", "    short message"));
            assertThat(recorder.getMessages(), is(empty()));
        }
    }

    private void generateDuplicateChanges(GitClient gitClient, String expectedMessage) throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(gitClient, true);
        File log = File.createTempFile("junit", null, tmpFolder);
        try (FileWriter writer = new FileWriter(log)) {
            writer.write("commit 123abc456def\n");
            writer.write("    " + firstMessage + "\n");
            writer.write("commit 123abc456def\n");
            writer.write("    second message");
        }
        GitChangeSetList list = parser.parse(null, null, log);
        assertNotNull(list);
        assertNotNull(list.getLogs());
        assertEquals(1, list.getLogs().size());
        GitChangeSet first = list.getLogs().get(0);
        assertNotNull(first);
        assertEquals("123abc456def", first.getId());
        assertThat(first.getMsg(), is(expectedMessage));
        assertTrue(log.delete(), "Temp file delete failed for " + log);
    }
}
