package hudson.plugins.git;

import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.GitClient;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parse the git log
 * @author Nigel Magnay
 */
public class GitChangeLogParser extends ChangeLogParser {

    private boolean authorOrCommitter;
    private final String scmName;

    private boolean showEntireCommitSummaryInChanges;

    @Deprecated
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
     * @param git the GitClient implmentation to be used by the change log parser
     * @param authorOrCommitter read author name instead of committer name if true
     */
    public GitChangeLogParser(GitClient git, boolean authorOrCommitter) {
        this(git, authorOrCommitter, null);
    }

    /**
     * Git client plugin 2.x silently truncated the first line of a commit message when showing the changelog summary in
     * the 'Changes' page using command line git. They did not truncate when using JGit. In order to simplify the git
     * client plugin implementation, the truncation was removed from git client plugin 3.0. In order to retain backward
     * compatibility, git plugin 4.0 became responsible to truncate the summary at the correct points.
     * As a result of that change of responsibility, this class needs to know which implementation is being used so
     * that it can adapt for compatibility.
     *
     * @param git the GitClient implmentation to be used by the change log parser
     * @param authorOrCommitter read author name instead of committer name if true
     * @param scmName the ScmName to include in the changelog
     */
    public GitChangeLogParser(GitClient git, boolean authorOrCommitter, String scmName) {
        super();
        this.authorOrCommitter = authorOrCommitter;
        this.scmName = scmName;
        /* Retain full commit summary if globally configured to retain full commit summary or if not using command line git.
         * That keeps change summary truncation compatible with git client plugin 2.x and git plugin 3.x for users of
         * command line git.
         */
        this.showEntireCommitSummaryInChanges = GitChangeSet.isShowEntireCommitSummaryInChanges() || !(git instanceof CliGitAPIImpl);
    }
    
    public List<GitChangeSet> parse(@Nonnull InputStream changelog) throws IOException {
        return parse(IOUtils.readLines(changelog, "UTF-8"));
    }

    public List<GitChangeSet> parse(@Nonnull List<String> changelog) {
        return parse(changelog.iterator());
    }

    @Override public GitChangeSetList parse(Run build, RepositoryBrowser<?> browser, File changelogFile)
        throws IOException, SAXException {
        // Parse the log file into GitChangeSet items - each one is a commit
        LineIterator lineIterator = null;
        try {
        	lineIterator = FileUtils.lineIterator(changelogFile,"UTF-8");
        	return new GitChangeSetList(build, browser, parse(lineIterator), scmName);
        } finally {
        	LineIterator.closeQuietly(lineIterator);
        }
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
