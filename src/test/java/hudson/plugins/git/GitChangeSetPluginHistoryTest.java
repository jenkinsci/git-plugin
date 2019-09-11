package hudson.plugins.git;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import org.eclipse.jgit.lib.ObjectId;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jenkins.plugins.git.GitSampleRepoRule;

@RunWith(Parameterized.class)
public class GitChangeSetPluginHistoryTest {

    private static final long FIRST_COMMIT_TIMESTAMP = 1198029565000L;
    private static final long NOW = System.currentTimeMillis();

    private final GitClient git;
    private final boolean authorOrCommitter;
    private final ObjectId sha1;

    private final GitChangeSet changeSet;

    @ClassRule
    public static GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    /* git 1.7.1 on CentOS 6.7 "whatchanged" generates no output for
     * the SHA1 hashes (from this repository) in this list. Rather
     * than skip testing on that old git version, this exclusion list
     * allows most tests to run. Debian 6 / git 1.7.2.5 also has the issue.
     */
    private static final String[] git171exceptions = {
        "6e467b23",
        "750b6806",
        "7eeb070b",
        "87988f4d",
        "94d982c2",
        "a571899e",
        "b9e497b0",
        "bc71cd2d",
        "bca98ea9",
        "c73b4ff3",
        "dcd329f4",
        "edf066f3",
    };

    public GitChangeSetPluginHistoryTest(GitClient git, boolean authorOrCommitter, String sha1String) throws IOException, InterruptedException {
        this.git = git;
        this.authorOrCommitter = authorOrCommitter;
        this.sha1 = ObjectId.fromString(sha1String);
        StringWriter stringWriter = new StringWriter();
        git.changelog().includes(sha1).max(1).to(stringWriter).execute();
        List<String> changeLogStrings = new ArrayList<>(Arrays.asList(stringWriter.toString().split("\n")));
        changeSet = new GitChangeSet(changeLogStrings, authorOrCommitter);
    }

    /**
     * Merge changes won't compute their date in GitChangeSet, apparently as an
     * intentional design choice. Return all changes for this repository which
     * are not merges.
     *
     * @return ObjectId list for all changes which aren't merges
     */
    private static List<ObjectId> getNonMergeChanges(boolean honorExclusions) throws IOException {
        List<ObjectId> nonMergeChanges = new ArrayList<>();
        Process process = new ProcessBuilder("git", "rev-list", "--no-merges", "HEAD").start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (honorExclusions) {
                    boolean ignore = false;
                    for (String exclusion : git171exceptions) {
                        if (line.startsWith(exclusion)) {
                            ignore = true;
                            break;
                        }
                    }
                    if (!ignore) {
                        nonMergeChanges.add(ObjectId.fromString(line));
                    }
                } else {
                    nonMergeChanges.add(ObjectId.fromString(line));
                }
            }
        }
        process.destroy();
        Collections.shuffle(nonMergeChanges);
        return nonMergeChanges;
    }

    @Parameterized.Parameters(name = "{2}-{1}")
    public static Collection<Object[]> generateData() throws IOException, InterruptedException {
        List<Object[]> args = new ArrayList<>();
        String[] implementations = new String[]{"git", "jgit"};
        boolean[] choices = {true, false};

        for (final String implementation : implementations) {
            EnvVars envVars = new EnvVars();
            TaskListener listener = StreamTaskListener.fromStdout();
            GitClient git = Git.with(listener, envVars).in(new FilePath(new File("."))).using(implementation).getClient();
            boolean honorExclusions = implementation.equals("git") && !sampleRepo.gitVersionAtLeast(1, 7, 10);
            List<ObjectId> allNonMergeChanges = getNonMergeChanges(honorExclusions);
            int count = allNonMergeChanges.size() / 10; /* 10% of all changes */

            for (boolean authorOrCommitter : choices) {
                for (int index = 0; index < count; index++) {
                    ObjectId sha1 = allNonMergeChanges.get(index);
                    Object[] argList = {git, authorOrCommitter, sha1.getName()};
                    args.add(argList);
                }
            }
        }
        return args;
    }

    @Test
    public void timestampInRange() {
        long timestamp = changeSet.getTimestamp();
        assertThat(timestamp, is(greaterThanOrEqualTo(FIRST_COMMIT_TIMESTAMP)));
        assertThat(timestamp, is(lessThan(NOW)));
    }
}
