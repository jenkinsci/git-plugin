package hudson.plugins.git;

import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.GitClient;

import org.apache.commons.io.LineIterator;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Parse the git log
 * @author Nigel Magnay
 */
public class GitChangeLogParser extends ChangeLogParser {

    private boolean authorOrCommitter;
    private boolean showEntireCommitSummaryInChanges;

    /**
     * Git client plugin 2.x silently truncated the first line of a commit message when showing the changelog summary in
     * the 'Changes' page using command line git. They did not truncate when using JGit. In order to simplify the git
     * client plugin implementation, the truncation was removed from git client plugin 3.0. In order to retain backward
     * compatibility, git plugin 4.0 became responsible to truncate the summary at the correct points.
     * As a result of that change of responsibility, this class needs to know which implementation is being used so
     * that it can adapt for appropriate compatibility.
     *
     * @param authorOrCommitter read author name instead of committer name if true
     * @deprecated use #GitChangeLogParser(GitClient, boolean)
     */
    @Deprecated
    public GitChangeLogParser(boolean authorOrCommitter) {
        this(null, authorOrCommitter);
    }

    /**
     * Git client plugin 2.x silently truncated the first line of a commit message when showing the changelog summary in
     * the 'Changes' page using command line git. They did not truncate when using JGit. In order to simplify the git
     * client plugin implementation, the truncation was removed from git client plugin 3.0. In order to retain backward
     * compatibility, git plugin 4.0 became responsible to truncate the summary at the correct points.
     * As a result of that change of responsibility, this class needs to know which implementation is being used so
     * that it can adapt for compatibility.
     *
     * @param git the GitClient implementation to be used by the change log parser
     * @param authorOrCommitter read author name instead of committer name if true
     */
    public GitChangeLogParser(GitClient git, boolean authorOrCommitter) {
        super();
        this.authorOrCommitter = authorOrCommitter;
        /* Retain full commit summary if globally configured to retain full commit summary or if not using command line git.
         * That keeps change summary truncation compatible with git client plugin 2.x and git plugin 3.x for users of
         * command line git.
         */
        this.showEntireCommitSummaryInChanges = GitChangeSet.isShowEntireCommitSummaryInChanges() || !(git instanceof CliGitAPIImpl);
    }
    
    public List<GitChangeSet> parse(@NonNull InputStream changelog) throws IOException {
        try (Reader r = new InputStreamReader(changelog, StandardCharsets.UTF_8); LineIterator it = new LineIterator(r)) {
            return parse(it);
        }
    }

    public List<GitChangeSet> parse(@NonNull List<String> changelog) {
        return parse(changelog.iterator());
    }

    @Override
    public GitChangeSetList parse(Run build, RepositoryBrowser<?> browser, File changelogFile)
        throws IOException {
        // Parse commits first to get their SHAs
        List<GitChangeSet> parsedCommits;
        try (Stream<String> lineStream = Files.lines(changelogFile.toPath(), StandardCharsets.UTF_8)) {
            parsedCommits = parse(lineStream.iterator());
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
        
        // Extract repository information from BuildData actions on the Run
        String scmName = null;
        Set<String> remoteUrls = new HashSet<>();
        
        if (build != null) {
            BuildData matchedBuildData = findMatchingBuildData(build, parsedCommits);
            
            if (matchedBuildData != null) {
                scmName = matchedBuildData.getScmName();
                if (matchedBuildData.getRemoteUrls() != null) {
                    remoteUrls.addAll(matchedBuildData.getRemoteUrls());
                }
            }
        }
        
        return new GitChangeSetList(build, browser, parsedCommits, scmName, remoteUrls);
    }

    /**
     * Finds the BuildData that matches the parsed commits by comparing commit hashes.
     * In multi-repository builds, this makes sure changelog is associated with the correct repo.
     *
     * @param build The build containing BuildData actions
     * @param parsedCommits The commits parsed from the changelog
     * @return The matching BuildData, or the first available BuildData if no SHA match is found, or null if no BuildData exists
     */
    private BuildData findMatchingBuildData(Run build, List<GitChangeSet> parsedCommits) {
        List<BuildData> buildDataList = build.getActions(BuildData.class);
        if (buildDataList.isEmpty()) {
            return null;
        }
        
        // Try to match by comparing commit SHAs with BuildData's last built revision
        if (!parsedCommits.isEmpty()) {
            for (BuildData buildData : buildDataList) {
                if (buildData.getLastBuiltRevision() != null) {
                    String buildDataSha = buildData.getLastBuiltRevision().getSha1String();
                    
                    for (GitChangeSet commit : parsedCommits) {
                        if (shaMatches(commit.getId(), buildDataSha)) {
                            return buildData;
                        }
                    }
                }
            }
        }  
        // Fall back to first BuildData if no SHA match found
        return buildDataList.get(0);
    }

    /**
     * Checks if two SHAs match, handling both full and short SHA formats.
     * Git uses 40-character full SHAs but often displays 7-character short SHAs.
     * @param sha1 First SHA
     * @param sha2 Second SHA
     * @return true if the SHAs match (either exact match or one starts with the other)
     */
    private boolean shaMatches(String sha1, String sha2) {
        if (sha1 == null || sha2 == null) {
            return false;
        }
        
        if (sha1.equals(sha2)) {
            return true;
        }
        
        // Handle short vs full SHA: one may be a prefix of the other
        return sha1.startsWith(sha2) || sha2.startsWith(sha1);
    }

    private List<GitChangeSet> parse(Iterator<String> changelog) {
        Set<GitChangeSet> r = new LinkedHashSet<>();
        List<String> lines = null;
        while (changelog.hasNext()) {
            String line = changelog.next();
            if (line.startsWith("commit ")) {
                if (lines != null) {
                    r.add(parseCommit(lines, authorOrCommitter));
                }
                lines = new ArrayList<>();
            }

            if (lines != null && lines.size()<THRESHOLD)
                lines.add(line);    // TODO: if we ignored some lines, tell the user so.
        }

        if (lines != null) {
            r.add(parseCommit(lines, authorOrCommitter));
        }
        return new ArrayList<>(r);
    }

    private GitChangeSet parseCommit(List<String> lines, boolean authorOrCommitter) {
        return new GitChangeSet(lines, authorOrCommitter, showEntireCommitSummaryInChanges);
    }

    /**
     * To control the memory overhead of a large change, we ignore beyond certain number of lines.
     */
    private static int THRESHOLD = 1000;
}
