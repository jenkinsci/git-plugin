package hudson.plugins.git;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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

    public List<GitChangeSet> parse(@Nonnull List<String> changelog) {
        return parse(changelog.iterator());
    }

    public GitChangeSetList parse(AbstractBuild build, File changelogFile)
        throws IOException, SAXException {
        // Parse the log file into GitChangeSet items - each one is a commit
        LineIterator lineIterator = null;
        try {
        	lineIterator = FileUtils.lineIterator(changelogFile,"UTF-8");
        	return new GitChangeSetList(build, parse(lineIterator));
        } finally {
        	LineIterator.closeQuietly(lineIterator);
        }
    }

    private List<GitChangeSet> parse(Iterator<String> changelog) {
        Set<GitChangeSet> r = new LinkedHashSet<GitChangeSet>();
        List<String> lines = null;
        while (changelog.hasNext()) {
            String line = changelog.next();
            if (line.startsWith("commit ")) {
                if (lines != null) {
                    r.add(parseCommit(lines, authorOrCommitter));
                }
                lines = new ArrayList<String>();
            }

            if (lines != null && lines.size()<THRESHOLD)
                lines.add(line);    // TODO: if we ignored some lines, tell the user so.
        }

        if (lines != null) {
            r.add(parseCommit(lines, authorOrCommitter));
        }
        return new ArrayList<GitChangeSet>(r);
    }

    private GitChangeSet parseCommit(List<String> lines, boolean authorOrCommitter) {
        return new GitChangeSet(lines, authorOrCommitter);
    }

    /**
     * To control the memory overhead of a large change, we ignore beyond certain number of lines.
     */
    private static int THRESHOLD = 1000;
}
