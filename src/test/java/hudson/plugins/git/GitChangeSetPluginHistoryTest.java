package hudson.plugins.git;

import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
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
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

import jenkins.plugins.git.GitSampleRepoRule;

@ParameterizedClass(name = "{2}-{1}")
@MethodSource("generateData")
@WithGitSampleRepo
class GitChangeSetPluginHistoryTest {

    private static final long FIRST_COMMIT_TIMESTAMP = 1198029565000L;

    private final ObjectId sha1;

    private final GitChangeSet changeSet;

    private static GitSampleRepoRule sampleRepo;

    public GitChangeSetPluginHistoryTest(GitClient git, boolean authorOrCommitter, String sha1String) throws Exception {
        this.sha1 = ObjectId.fromString(sha1String);
        StringWriter stringWriter = new StringWriter();
        git.changelog().includes(sha1).max(1).to(stringWriter).execute();
        List<String> changeLogStrings = new ArrayList<>(Arrays.asList(stringWriter.toString().split("\n")));
        changeSet = new GitChangeSet(changeLogStrings, authorOrCommitter);
    }

    @BeforeAll
    static void beforeAll(GitSampleRepoRule repo) {
        sampleRepo = repo;
    }

    /**
     * Merge changes won't compute their date in GitChangeSet, apparently as an
     * intentional design choice. Return all changes for this repository which
     * are not merges.
     *
     * @return ObjectId list for all changes which aren't merges
     */
    private static List<ObjectId> getNonMergeChanges() throws IOException {
        List<ObjectId> nonMergeChanges = new ArrayList<>();
        ProcessBuilder pb = new ProcessBuilder("git", "rev-list", "--no-merges", "HEAD");
        pb.directory(sampleRepo.getRoot());
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                nonMergeChanges.add(ObjectId.fromString(line));
            }
        }
        process.destroy();
        Collections.shuffle(nonMergeChanges);
        return nonMergeChanges;
    }

    static Collection<Object[]> generateData() throws Exception {
        List<Object[]> args = new ArrayList<>();
        String[] implementations = new String[]{"git", "jgit"};
        boolean[] choices = {true, false};

        List<ObjectId> allNonMergeChanges = getNonMergeChanges();
        if (allNonMergeChanges.isEmpty()) {
            return args;
        }

        for (final String implementation : implementations) {
            EnvVars envVars = new EnvVars();
            TaskListener listener = StreamTaskListener.fromStdout();
            GitClient git = Git.with(listener, envVars).in(new FilePath(new File("."))).using(implementation).getClient();
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
    void timestampInRange() {
        long timestamp = changeSet.getTimestamp();
        long now = System.currentTimeMillis();
        assertThat(timestamp, is(greaterThanOrEqualTo(FIRST_COMMIT_TIMESTAMP)));
        // Allow 1 second tolerance for timestamp being at or near the current time
        assertThat(timestamp, is(lessThanOrEqualTo(now + 1000)));
    }
}
