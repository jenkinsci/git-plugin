package hudson.plugins.git;

import static hudson.Util.fixEmpty;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.EditType;
import hudson.tasks.Mailer;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a change set.
 * @author Nigel Magnay
 */
public class GitChangeSet extends ChangeLogSet.Entry {

    private static final Pattern FILE_LOG_ENTRY = Pattern.compile("^:[0-9]{6} [0-9]{6} ([0-9a-f]{40}) ([0-9a-f]{40}) ([ACDMRTUX])(?>[0-9]+)?\t(.*)$");
    private static final String NULL_HASH = "0000000000000000000000000000000000000000";
    private String author;
    private String authorEmail;
    private String comment;
    private String title;
    private String id;
    private String parentCommit;
    private Collection<Path> paths = new HashSet<Path>();

    public GitChangeSet(List<String> lines) {
        if (lines.size() > 0) {
            parseCommit(lines);
        }
    }

    private void parseCommit(List<String> lines) {

        String message = "";

        for (String line : lines) {
            if (line.length() > 0) {
                if (line.startsWith("commit ")) {
                    this.id = line.split(" ")[1];
                } else if (line.startsWith("tree ")) {
                } else if (line.startsWith("parent ")) {
                    this.parentCommit = line.split(" ")[1];
                } else if (line.startsWith("committer ")) {
                    this.author = line.substring(10, line.indexOf(" <"));
                    this.authorEmail = line.substring(line.indexOf(" <") + 2, line.indexOf("> "));
                } else if (line.startsWith("author ")) {
                } else if (line.startsWith("    ")) {
                    message += line.substring(4) + "\n";
                } else if (':' == line.charAt(0)) {
                    Matcher fileMatcher = FILE_LOG_ENTRY.matcher(line);
                    if (fileMatcher.matches() && fileMatcher.groupCount() >= 4) {
                        String mode = fileMatcher.group(3);
                        if (mode.length() == 1) {
                            String src = null;
                            String dst = null;
                            char editMode = mode.charAt(0);
                            if (editMode == 'M' || editMode == 'A' || editMode == 'D') {
                                src = parseHash(fileMatcher.group(1));
                                dst = parseHash(fileMatcher.group(2));
                            }
                            String path = fileMatcher.group(4);
                            this.paths.add(new Path(src, dst, editMode, path, this));
                        }
                    }
                } else {
                    // Ignore
                }
            }
        }

        this.comment = message;

        int endOfFirstLine = this.comment.indexOf('\n');
        if (endOfFirstLine == -1) {
            this.title = this.comment;
        } else {
            this.title = this.comment.substring(0, endOfFirstLine);
        }
    }

    private String parseHash(String hash) {
        return NULL_HASH.equals(hash) ? null : hash;
    }

    @Override
    public void setParent(ChangeLogSet parent) {
        super.setParent(parent);
    }

    public String getParentCommit() {
        return parentCommit;
    }

    public Collection<Path> getPaths() {
        return this.paths;
    }

    @Override
    @Exported
    public Collection<String> getAffectedPaths() {
        Collection<String> affectedPaths = new HashSet<String>(this.paths.size());
        for (Path file : this.paths) {
            affectedPaths.add(file.getPath());
        }
        return affectedPaths;
    }

    @Override
    @Exported
    public User getAuthor() {
        if (this.author == null) {
            throw new RuntimeException("No author in this changeset!");
        }

        User user = User.get(this.author, true);

        // set email address for user if needed
        if (fixEmpty(this.authorEmail) != null && user.getProperty(Mailer.UserProperty.class) == null) {
            try {
                user.addProperty(new Mailer.UserProperty(this.authorEmail));
            } catch (IOException e) {
                // ignore error
            }
        }

        return user;
    }

    @Override
    @Exported
    public String getMsg() {
        return this.title;
    }

    @Exported
    public String getId() {
        return this.id;
    }

    @Exported
    public String getComment() {
        return this.comment;
    }

    public static class Path implements AffectedFile {

        private String src;
        private String dst;
        private char action;
        private String path;
        private GitChangeSet changeSet;

        private Path(String source, String destination, char action, String filePath, GitChangeSet changeSet) {
            this.src = source;
            this.dst = destination;
            this.action = action;
            this.path = filePath;
            this.changeSet = changeSet;
        }

        public String getSrc() {
            return src;
        }

        public String getDst() {
            return dst;
        }

        public String getPath() {
            return path;
        }

        public GitChangeSet getChangeSet() {
            return changeSet;
        }

        public EditType getEditType() {
            switch (action) {
                case 'A':
                    return EditType.ADD;
                case 'D':
                    return EditType.DELETE;
                default:
                    return EditType.EDIT;
            }
        }
    }
}
