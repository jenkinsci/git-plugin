package hudson.plugins.git;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.tasks.Mailer;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Represents a change set.
 * @author Nigel Magnay
 */
public class GitChangeSet extends ChangeLogSet.Entry {

	private String author;
	private String authorEmail;
	private String comment;
	private String title;
	private String id;

	private Collection<String> affectedPaths = new HashSet<String>();

	public GitChangeSet(List<String> lines) {
		if (lines.size() > 0) {
			parseCommit(lines);
		}
	}

	private void parseCommit(List<String> lines) {

		String comment = "";

		for (String line : lines) {
			if (line.length() > 0) {
				if (line.startsWith("commit ")) {
					this.id = line.split(" ")[1];
				} else if (line.startsWith("tree ")) {
				} else if (line.startsWith("parent ")) {
					// parent
				} else if (line.startsWith("committer ")) {
					this.author = line.substring(10, line.indexOf(" <"));
					this.authorEmail = line.substring(line.indexOf(" <") + 2, line.indexOf("> "));
				} else if (line.startsWith("author ")) {
				} else if (line.startsWith("    ")) {
					comment += line.substring(4) + "\n";
				} else if (line.startsWith("A\t") || line.startsWith("C\t") || line.startsWith("D\t")
						|| line.startsWith("M\t") || line.startsWith("R\t") || line.startsWith("T\t")) {
					this.affectedPaths.add(line.substring(2));
				} else {
					// Ignore
				}
			}
		}

		this.comment = comment;

		int endOfFirstLine = this.comment.indexOf('\n');
		if (endOfFirstLine == -1) {
			this.title = this.comment;
		} else {
			this.title = this.comment.substring(0, endOfFirstLine);
		}
	}

	public void setParent(ChangeLogSet parent) {
		super.setParent(parent);
	}

	@Override
	public Collection<String> getAffectedPaths() {
		return this.affectedPaths;
	}

	@Override
	public User getAuthor() {
		if (this.author == null)
			throw new RuntimeException("No author in this changeset!");

		User user = User.get(this.author, false);

		// if the user doesn't exist create it and set the email address
		if (user == null) {
			user = User.get(this.author, true);
			try {
				user.addProperty(new Mailer.UserProperty(this.authorEmail));
			} catch (IOException e) {
				// ignore error
			}
		}

		return user;
	}

	@Override
	public String getMsg() {
		return this.title;
	}

	public String getId() {
		return this.id;
	}

	public String getComment() {
		return this.comment;
	}

}
