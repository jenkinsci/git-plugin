package hudson.plugins.git;

import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
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
        
        // Try to match this changelog to the correct BuildData by comparing commit SHAs
        if (build != null) {
            List<hudson.plugins.git.util.BuildData> buildDataList = build.getActions(hudson.plugins.git.util.BuildData.class);
            if (!buildDataList.isEmpty()) {
            hudson.plugins.git.util.BuildData matchedBuildData = null;
            
            // If we have parsed commits, try to match them to BuildData by checking if ANY commit
            // in the changelog matches the BuildData's last built revision
            if (!parsedCommits.isEmpty()) {
                for (hudson.plugins.git.util.BuildData bd : buildDataList) {
                    if (bd.getLastBuiltRevision() != null) {
                        String buildDataSha = bd.getLastBuiltRevision().getSha1String();
                        
                        // Check if any commit in this changelog matches this BuildData's revision
                        for (GitChangeSet commit : parsedCommits) {
                            String commitSha = commit.getId();
                            // Match if either SHA starts with the other (handles short vs full SHAs)
                            if (commitSha.equals(buildDataSha) || 
                                commitSha.startsWith(buildDataSha.substring(0, Math.min(7, buildDataSha.length()))) ||
                                buildDataSha.startsWith(commitSha.substring(0, Math.min(7, commitSha.length())))) {
                                matchedBuildData = bd;
                                break;
                            }
                        }
                        if (matchedBuildData != null) {
                            break;
                        }
                    }
                }
            }
            
            // If no match found by SHA, use the first available BuildData
            // In practice, SHA matching should work for most cases
            if (matchedBuildData == null && !buildDataList.isEmpty()) {
                // Default to the first BuildData if no SHA match found
                matchedBuildData = buildDataList.get(0);
            }
            
            if (matchedBuildData != null) {
                scmName = matchedBuildData.getScmName();
                if (matchedBuildData.getRemoteUrls() != null) {
                    remoteUrls.addAll(matchedBuildData.getRemoteUrls());
                }
            }
        }
        }
        
        return new GitChangeSetList(build, browser, parsedCommits, scmName, remoteUrls);
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
