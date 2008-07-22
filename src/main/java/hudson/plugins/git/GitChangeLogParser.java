package hudson.plugins.git;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.SAXException;

/**
 * Parse the git log
 * @author Nigel Magnay
 */
public class GitChangeLogParser extends ChangeLogParser {
	public GitChangeSetList parse(AbstractBuild build, File changelogFile)
			throws IOException, SAXException {

		ArrayList<GitChangeSet> r = new ArrayList<GitChangeSet>();

		// Parse the log file into GitChangeSet items - each one is a commit

		BufferedReader rdr = new BufferedReader(new FileReader(changelogFile));

		try
    {
		String line;
		List<String> lines = null;

		while ((line = rdr.readLine()) != null) {
			if (line.startsWith("commit ")) {
				if (lines != null) {
					r.add(parseCommit(lines));
				}
				lines = new ArrayList<String>();
			}
			

			lines.add(line);
		}

		if (lines != null) {
			r.add(parseCommit(lines));
		}

		return new GitChangeSetList(build, r);
		}
    finally
    {
      rdr.close();
    }
	}

	private GitChangeSet parseCommit(List<String> lines) {
		return new GitChangeSet(lines);
	}

}
