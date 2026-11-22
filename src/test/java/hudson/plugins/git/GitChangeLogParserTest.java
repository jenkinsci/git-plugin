package hudson.plugins.git;

import hudson.EnvVars;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitAPIImpl;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    /* Test SHA matching handles full and short SHAs */
    @Test
    void testShaMatching() throws Exception {
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).using("Default").in(new File(".")).getClient();
        GitChangeLogParser parser = new GitChangeLogParser(gitClient, true);
        Method shaMatches = GitChangeLogParser.class.getDeclaredMethod("shaMatches", String.class, String.class);
        shaMatches.setAccessible(true);

        String fullSha = "abc123def456789012345678901234567890abcd";
        String shortSha = "abc123d";
        String differentSha = "999xyz123456789012345678901234567890abcd";

        assertTrue((Boolean) shaMatches.invoke(parser, fullSha, fullSha));
        assertTrue((Boolean) shaMatches.invoke(parser, shortSha, fullSha));
        assertTrue((Boolean) shaMatches.invoke(parser, fullSha, shortSha));
        assertFalse((Boolean) shaMatches.invoke(parser, fullSha, differentSha));
        assertFalse((Boolean) shaMatches.invoke(parser, null, fullSha));
        assertFalse((Boolean) shaMatches.invoke(parser, fullSha, null));
    }

    /* Test GitChangeSetList stores repository information */
    @Test
    void testChangeSetListWithRepositoryInfo() throws Exception {
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).using("Default").in(new File(".")).getClient();
        GitChangeLogParser parser = new GitChangeLogParser(gitClient, true);
        File log = File.createTempFile("junit", null, tmpFolder);
        try (FileWriter writer = new FileWriter(log)) {
            writer.write("commit abc123def456\n");
            writer.write("    Test commit\n");
        }

        Set<String> remoteUrls = new HashSet<>();
        remoteUrls.add("https://github.com/jenkinsci/git-plugin.git");
        
        GitChangeSetList changeSetList = parser.parse(null, null, log);
        List<GitChangeSet> logs = changeSetList.getLogs();
        GitChangeSetList list = new GitChangeSetList(null, null, logs, "origin", remoteUrls);

        assertEquals("origin", list.getScmName());
        assertEquals(1, list.getRemoteUrls().size());
        assertTrue(list.getRemoteUrls().contains("https://github.com/jenkinsci/git-plugin.git"));
        assertTrue(log.delete(), "Temp file delete failed for " + log);
    }

    /* Test GitChangeSetList handles null repository information */
    @Test
    void testChangeSetListWithNullRepositoryInfo() throws Exception {
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).using("Default").in(new File(".")).getClient();
        GitChangeLogParser parser = new GitChangeLogParser(gitClient, true);
        File log = File.createTempFile("junit", null, tmpFolder);
        try (FileWriter writer = new FileWriter(log)) {
            writer.write("commit abc123def456\n");
            writer.write("    Test commit\n");
        }

        GitChangeSetList changeSetList = parser.parse(null, null, log);
        List<GitChangeSet> logs = changeSetList.getLogs();
        GitChangeSetList list = new GitChangeSetList(null, null, logs, null, null);

        assertNull(list.getScmName());
        assertNull(list.getRemoteUrls());
        assertTrue(log.delete(), "Temp file delete failed for " + log);
    }
}
