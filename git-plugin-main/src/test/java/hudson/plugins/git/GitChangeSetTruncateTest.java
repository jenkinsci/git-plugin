package hudson.plugins.git;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

import hudson.EnvVars;
import hudson.model.TaskListener;
import jenkins.plugins.git.CliGitCommand;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;

@RunWith(Parameterized.class)
public class GitChangeSetTruncateTest {

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    @ClassRule
    public static GitSampleRepoRule versionCheckRepo = new GitSampleRepoRule();

    private static File repoRoot = null;

    private static final Random random = new Random();

    /* Arguments to the constructor */
    private final String gitImpl;
    private final String commitSummary;
    private final String truncatedSummary;

    /* Computed in the constructor, used in tests */
    private final GitChangeSet changeSet;
    private final GitChangeSet changeSetFullSummary;
    private final GitChangeSet changeSetTruncatedSummary;

    private static class TestData {

        final public String testDataCommitSummary;
        final public String testDataTruncatedSummary;

        TestData(String commitSummary, String truncatedSummary) {
            this.testDataCommitSummary = commitSummary;
            this.testDataTruncatedSummary = truncatedSummary;
        }
    }

    //                                                    1         2         3         4         5         6         7
    //                                           1234567890123456789012345678901234567890123456789012345678901234567890
    private final static String SEVENTY_CHARS = "[JENKINS-012345] 8901 34567 90 23456 8901 34567 9012 4567890 2345678 0";
    private final static String EIGHTY_CHARS  = "12345678901234567890123456789012345678901234567890123456789012345678901234567890";

    private final static TestData[] TEST_DATA = {
        new TestData(EIGHTY_CHARS,                         EIGHTY_CHARS), // surprising that longer than 72 is returned
        new TestData(EIGHTY_CHARS + " A B C",              EIGHTY_CHARS), // surprising that longer than 72 is returned
        new TestData(SEVENTY_CHARS,                        SEVENTY_CHARS),
        new TestData(SEVENTY_CHARS + " 2",                 SEVENTY_CHARS + " 2"),
        new TestData(SEVENTY_CHARS + " 2 4",               SEVENTY_CHARS + " 2"),
        new TestData(SEVENTY_CHARS + " 23",                SEVENTY_CHARS),
        new TestData(SEVENTY_CHARS + " 2&4",               SEVENTY_CHARS),
        new TestData(SEVENTY_CHARS + "1",                  SEVENTY_CHARS + "1"),
        new TestData(SEVENTY_CHARS + "1 3",                SEVENTY_CHARS + "1"),
        new TestData(SEVENTY_CHARS + "1 <4",               SEVENTY_CHARS + "1"),
        new TestData(SEVENTY_CHARS + "1 3 5",              SEVENTY_CHARS + "1"),
        new TestData(SEVENTY_CHARS + "1;",                 SEVENTY_CHARS + "1;"),
        new TestData(SEVENTY_CHARS + "1; 4",               SEVENTY_CHARS + "1;"),
        new TestData(SEVENTY_CHARS + " " + SEVENTY_CHARS,  SEVENTY_CHARS),
        new TestData(SEVENTY_CHARS + "  " + SEVENTY_CHARS, SEVENTY_CHARS + " ") // surprising that trailing space is preserved
    };

    public GitChangeSetTruncateTest(String gitImpl, String commitSummary, String truncatedSummary) throws Exception {
        this.gitImpl = gitImpl;
        this.commitSummary = commitSummary;
        this.truncatedSummary = truncatedSummary;
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).in(repoRoot).using(gitImpl).getClient();
        final ObjectId head = commitOneFile(gitClient, commitSummary);
        StringWriter changelogStringWriter = new StringWriter();
        gitClient.changelog().includes(head).to(changelogStringWriter).execute();
        List<String> changeLogList = Arrays.asList(changelogStringWriter.toString().split("\n"));
        changeSet = new GitChangeSet(changeLogList, random.nextBoolean());
        changeSetFullSummary = new GitChangeSet(changeLogList, random.nextBoolean(), true);
        changeSetTruncatedSummary = new GitChangeSet(changeLogList, random.nextBoolean(), false);
    }

    @Parameterized.Parameters(name = "{0} \"{1}\" --->>> \"{2}\"")
    public static Collection gitObjects() {
        /* If CLI git is older than 1.8.3, don't test CLI git message truncation */
        /* CLI git 1.7.1 (CentOS 6) does not support the message truncation command line flags */
        String[] bothGitImplementations = {"git", "jgit"};
        String[] jgitImplementation = {"jgit"};
        List<Object[]> arguments = new ArrayList<>();
        for (String implementation : versionCheckRepo.gitVersionAtLeast(1, 8, 3) ? bothGitImplementations : jgitImplementation) {
            for (TestData sample : TEST_DATA) {
                Object[] item = {implementation, sample.testDataCommitSummary, sample.testDataTruncatedSummary};
                arguments.add(item);
            }
        }
        Collections.shuffle(arguments); // Execute in random order
        return arguments;
    }

    @BeforeClass
    public static void createRepo() throws Exception {
        repoRoot = tempFolder.newFolder();
        String initialImpl = random.nextBoolean() ? "git" : "jgit";
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars()).in(repoRoot).using(initialImpl).getClient();
        gitClient.init_().workspace(repoRoot.getAbsolutePath()).execute();
        String[] expectedResult = {""};
        CliGitCommand gitCmd = new CliGitCommand(gitClient, "config", "user.name", "ChangeSet Truncation Test");
        assertThat(gitCmd.run(), is(expectedResult));
        gitCmd = new CliGitCommand(gitClient, "config", "user.email", "ChangeSetTruncation@mail.example.com");
        assertThat(gitCmd.run(), is(expectedResult));
    }

    private ObjectId commitOneFile(GitClient gitClient, final String commitSummary) throws Exception {
        String path = "One-File.txt";
        String content = String.format("A random UUID: %s\n", UUID.randomUUID().toString());
        /* randomize whether commit message is single line or multi-line */
        String commitMessageBody = random.nextBoolean() ? "\n\n" + "committing " + path + " with content:\n\n" + content : "";
        String commitMessage = commitSummary + commitMessageBody;
        createFile(path, content);
        gitClient.add(path);
        gitClient.commit(commitMessage);
        List<ObjectId> headList = gitClient.revList(Constants.HEAD);
        assertThat(headList.size(), is(greaterThan(0)));
        return headList.get(0);
    }

    private void createFile(String path, String content) throws Exception {
        File aFile = new File(repoRoot, path);
        File parentDir = aFile.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }
        try (PrintWriter writer = new PrintWriter(aFile, "UTF-8")) {
            writer.printf(content);
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
            throw new GitException(ex);
        }
    }

    @Test
    @Issue("JENKINS-29977") // CLI git truncates first line of commit message in Changes page, JGit doesn't
    public void summaryTruncatedAtLastWord72CharactersOrLess() throws Exception {
        /**
         * Before git plugin 4.0, calls to GitChangeSet(x, y) truncated CLI git, did not truncate JGit.
         * After git plugin 4.0, calls to GitChangeSet(x, y) truncates CLI git, truncates JGit.
         * Callers after git plugin 4.0 must use the GitChangeSet(x, y, z) call to specify truncation behavior.
         */
        assertThat(changeSet.getMsg(), is(truncatedSummary));
    }

    @Test
    @Issue("JENKINS-29977")
    public void summaryAlwaysTruncatedAtLastWord72CharactersOrLess() throws Exception {
        assertThat(changeSetTruncatedSummary.getMsg(), is(truncatedSummary));
    }

    @Test
    @Issue("JENKINS-29977")
    public void summaryNotTruncatedAtLastWord72CharactersOrLess() throws Exception {
        assertThat(changeSetFullSummary.getMsg(), is(commitSummary));
    }
}
