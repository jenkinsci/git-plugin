package hudson.plugins.git;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Represents a change set.
 * @author Nigel Magnay
 */
public class GitChangeSet extends ChangeLogSet.Entry {

	String author;
	String msg;
	String id;
	
	Collection<String> affectedPaths = new HashSet<String>();

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
				} else if (line.startsWith("tree")) {
				} else if (line.startsWith("parent")) {
					// parent
				} else if (line.startsWith("committer")) {
					
					this.author = line.substring(10, line.indexOf(" <"));

				} else if (line.startsWith("author ")) {

				} else if (line.startsWith("    ")) {
					comment += line + "\n";
				} else {

					if (line.startsWith(" create")) {
						// " create mode 101010 path"
						String[] items = line.split(" ");

						String pathString = line.substring(line
								.indexOf(items[4]));
						affectedPaths.add(pathString);
					} else if (line.startsWith(" delete")) {
						// " delete mode 101010 path"
						String[] items = line.split(" ");

						String pathString = line.substring(line
								.indexOf(items[4]));
						affectedPaths.add(pathString);

					} else if (line.startsWith(" rename")) {
						// " rename path (change amount%)"
						String[] items = line.split(" ");
						String pathString = line.substring(line
								.indexOf(items[2]));
						// remove the trailing percentage
						pathString = pathString.substring(0, pathString
								.lastIndexOf(" "));

						String[] paths = unsplit(pathString);

						affectedPaths.add(paths[0]);
						affectedPaths.add(paths[1]);

					} else if (line.startsWith(" copy")) {
						// " copy path (change amount%)"
						String[] items = line.split(" ");
						String pathString = line.substring(line
								.indexOf(items[2]));

						// remove the trailing percentage
						pathString = pathString.substring(0, pathString
								.lastIndexOf(" "));

						String[] paths = unsplit(pathString);

						// only affect the target..
						affectedPaths.add(paths[1]);

					} else if (line.startsWith(" mode")) {
						// Ignore mode change
					} else if (line.startsWith(" ")) {
						throw new RuntimeException(
								"Log contains line that is not expected: "
										+ line);
					} else {
						// Ignore
					}

				}
			}

		}

		this.msg = comment;
	}

	public String[] unsplit(String data) {
		// Given modules/intray/{mergeFiles/WEB-INF/classes =>
		// src/main/resources/com/nirima}/modules.xml
		// return the two paths specified
		try {
			if (!data.contains("{")) {
				String left = data.substring(0, data.indexOf(" => "));
				String right = data.substring(data.indexOf(" => ") + 4);
				return new String[] { left, right };
			} else {

				String pre = data.substring(0, data.indexOf('{'));
				String post = data.substring(data.indexOf('}') + 1);

				String left = data.substring(data.indexOf('{') + 1, data
						.indexOf(" => "));
				String right = data.substring(data.indexOf(" => ") + 4, data
						.indexOf("}"));

				String leftItem = pre + left + post;
				String rightItem = pre + right + post;

				// Special - repace any // with /
				leftItem = leftItem.replaceAll("//", "/");
				rightItem = rightItem.replaceAll("//", "/");
				return new String[] { leftItem, rightItem };
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
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
		return User.get(this.author, true);
	}

	@Override
	public String getMsg() {
		return this.msg;
	}

	public String getId() {
		return this.id;
	}

}
