package hudson.plugins.git;

import hudson.EnvVars;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitAPIImpl;

import java.io.File;
import java.io.FileWriter;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests of {@link GitChangeLogParser}
 */
public class GitChangeLogParserTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private final String firstMessageTruncated = "123456789 123456789 123456789 123456789 123456789 123456789 123456789 1";
    private final String firstMessage = firstMessageTruncated + " 345 789";

    /* Test duplicate changes filtered from parsed CLI git change set list. */
    @Test
    public void testDuplicatesFilteredCliGit() throws Exception {
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).using("Default").in(new File(".")).getClient();
        assertThat(gitClient, instanceOf(CliGitAPIImpl.class));
        /* JENKINS-29977 notes that CLI git impl truncates summary message - confirm default behavior retained */
        generateDuplicateChanges(gitClient, firstMessageTruncated);
    }

    /* Test duplicate changes filtered from parsed JGit change set list. */
    @Test
    public void testDuplicatesFilteredJGit() throws Exception {
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).using("jgit").in(new File(".")).getClient();
        assertThat(gitClient, instanceOf(JGitAPIImpl.class));
        /* JENKINS-29977 notes that JGit impl retains full summary message - confirm default behavior retained */
        generateDuplicateChanges(gitClient, firstMessage);
    }

    private void generateDuplicateChanges(GitClient gitClient, String expectedMessage) throws Exception {
        GitChangeLogParser parser = new GitChangeLogParser(gitClient, true, null);
        File log = tmpFolder.newFile();
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
        assertTrue("Temp file delete failed for " + log, log.delete());
    }
}
