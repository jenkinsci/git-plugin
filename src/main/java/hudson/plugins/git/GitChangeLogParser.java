package hudson.plugins.git;

import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;

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

    public GitChangeLogParser(boolean authorOrCommitter) {
        super();
        this.authorOrCommitter = authorOrCommitter;
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
        	return new GitChangeSetList(build, browser, parse(lineIterator));
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
        return new GitChangeSet(lines, authorOrCommitter);
    }

    /**
     * To control the memory overhead of a large change, we ignore beyond certain number of lines.
     */
    private static int THRESHOLD = 1000;
}
