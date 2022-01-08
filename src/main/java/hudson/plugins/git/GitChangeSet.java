package hudson.plugins.git;

import hudson.MarkupText;
import hudson.Plugin;
import hudson.model.User;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.EditType;
import jenkins.model.Jenkins;
import org.apache.commons.lang.math.NumberUtils;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hudson.Util.fixEmpty;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

/**
 * Represents a change set.
 * @author Nigel Magnay
 */
public class GitChangeSet extends ChangeLogSet.Entry {

    private static final String PREFIX_AUTHOR = "author ";
    private static final String PREFIX_COMMITTER = "committer ";
    private static final String IDENTITY = "([^<]*)<(.*)> (.*)";

    private static final Pattern FILE_LOG_ENTRY = Pattern.compile("^:[0-9]{6} [0-9]{6} ([0-9a-f]{40}) ([0-9a-f]{40}) ([ACDMRTUX])(?>[0-9]+)?\t(.*)$");
    private static final Pattern AUTHOR_ENTRY = Pattern.compile("^"
            + PREFIX_AUTHOR + IDENTITY + "$");
    private static final Pattern COMMITTER_ENTRY = Pattern.compile("^"
            + PREFIX_COMMITTER + IDENTITY + "$");
    private static final Pattern RENAME_SPLIT = Pattern.compile("^(.*?)\t(.*)$");

    private static final String NULL_HASH = "0000000000000000000000000000000000000000";
    private static final String ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String ISO_8601_WITH_TZ = "yyyy-MM-dd'T'HH:mm:ssX";
    static final int TRUNCATE_LIMIT = 72;

    private final DateTimeFormatter [] dateFormatters;

    public static final Logger LOGGER = Logger.getLogger(GitChangeSet.class.getName());

    /**
     * This is broken as a part of the 1.5 refactoring.
     *
     * <p>
     * When we build a commit that multiple branches point to, Git plugin historically recorded
     * changelogs "revOfBranchInPreviousBuild...revToBuild" for each branch separately. This
     * however fails to take full generality of Git commit graph into account, as such rev-lists
     * can share common commits, which then get reported multiple times.
     *
     * <p>
     * In Git, a commit doesn't belong to a branch, in the sense that you cannot look at the object graph
     * and re-construct exactly how branch has evolved. In that sense, trying to attribute commits to
     * branches is a somewhat futile exercise.
     *
     * <p>
     * On the other hand, if this is still deemed important, the right thing to do is to traverse
     * the commit graph and see if a commit can be only reachable from the "revOfBranchInPreviousBuild" of
     * just one branch, in which case it's safe to attribute the commit to that branch.
     */
    private String committer;
    private String committerEmail;
    private String committerTime;
    private String author;
    private String authorEmail;
    private String authorTime;
    private String comment;
    private String title;
    private String id;
    private String parentCommit;
    private Collection<Path> paths = new HashSet<>();
    private boolean authorOrCommitter;
    private boolean showEntireCommitSummaryInChanges;

    /**
     * Create Git change set using information in given lines.
     *
     * @param lines change set lines read to construct change set
     * @param authorOrCommitter if true, use author information (name, time), otherwise use committer information
     */
    public GitChangeSet(List<String> lines, boolean authorOrCommitter) {
        this(lines, authorOrCommitter, isShowEntireCommitSummaryInChanges());
    }

    /* Add time zone parsing for +00:00 offset, +0000 offset, and +00 offset */
    private DateTimeFormatterBuilder addZoneOffset(DateTimeFormatterBuilder builder) {
        builder.optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd();
        builder.optionalStart().appendOffset("+HHMM", "+0000").optionalEnd();
        builder.optionalStart().appendOffset("+HH", "Z").optionalEnd();
        return builder;
    }

    /**
     * Create Git change set using information in given lines.
     *
     * @param lines change set lines read to construct change set
     * @param authorOrCommitter if true, use author information (name, time), otherwise use committer information
     * @param retainFullCommitSummary if true, do not truncate commit summary in the 'Changes' page
     */
    public GitChangeSet(List<String> lines, boolean authorOrCommitter, boolean retainFullCommitSummary) {
        this.authorOrCommitter = authorOrCommitter;
        this.showEntireCommitSummaryInChanges = retainFullCommitSummary;
        if (lines.size() > 0) {
            parseCommit(lines);
        }

        // Nearly ISO dates generated by git whatchanged --format=+ci
        // Look like '2015-09-30 08:21:24 -0600'
        // ISO is    '2015-09-30T08:21:24-06:00'
        // Uses Builder rather than format pattern for more reliable parsing
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.append(DateTimeFormatter.ISO_LOCAL_DATE);
        builder.appendLiteral(' ');
        builder.append(DateTimeFormatter.ISO_LOCAL_TIME);
        builder.optionalStart().appendLiteral(' ').optionalEnd();
        addZoneOffset(builder);
        DateTimeFormatter gitDateFormatter = builder.toFormatter();

        // DateTimeFormat.forPattern("yyyy-MM-DDTHH:mm:ssZ");
        // 2013-03-21T15:16:44+0100
        // Uses Builder rather than format pattern for more reliable parsing
        builder = new DateTimeFormatterBuilder();
        builder.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        addZoneOffset(builder);
        DateTimeFormatter nearlyISOFormatter = builder.toFormatter();

        dateFormatters = new DateTimeFormatter[3];
        dateFormatters[0] = gitDateFormatter; // First priority +%cI format
        dateFormatters[1] = nearlyISOFormatter; // Second priority seen in git-plugin
        dateFormatters[2] = DateTimeFormatter.ISO_OFFSET_DATE_TIME; // Third priority, ISO 8601 format
    }

    /**
     * The git client plugin command line implementation silently truncated changelog summaries (the first line of the
     * commit message) that were longer than 72 characters beginning with git client plugin 2.0. Beginning with git
     * client plugin 3.0 and git plugin 4.0, the git client plugin no longer silently truncates changelog summaries.
     * Truncation responsibility has moved into the git plugin. The git plugin will default to truncate all changelog
     * summaries (including JGit summaries) unless title truncation has been globally disabled or the caller called the
     * GitChangeSet constructor with the argument to retain the full commit summary.
     *
     * See JENKINS-29977 for more details
     *
     * @return true if first line of commit message should be truncated at word boundary before 73 characters
     */
    static boolean isShowEntireCommitSummaryInChanges() {
        try {
            return new DescriptorImpl().isShowEntireCommitSummaryInChanges();
        }catch (Throwable t){
            return false;
        }
    }

    private void parseCommit(List<String> lines) {

        StringBuilder message = new StringBuilder();

        for (String line : lines) {
            if( line.length() < 1)
                continue;
            if (line.startsWith("commit ")) {
                String[] split = line.split(" ");
                if (split.length > 1) this.id = split[1];
                else throw new IllegalArgumentException("Commit has no ID" + lines);
            } else if (line.startsWith("tree ")) {
            } else if (line.startsWith("parent ")) {
                String[] split = line.split(" ");
                // parent may be null for initial commit or changelog computed from a shallow clone
                if (split.length > 1) this.parentCommit = split[1];
            } else if (line.startsWith(PREFIX_COMMITTER)) {
                Matcher committerMatcher = COMMITTER_ENTRY.matcher(line);
                if (committerMatcher.matches()
                        && committerMatcher.groupCount() >= 3) {
                    this.committer = committerMatcher.group(1).trim();
                    this.committerEmail = committerMatcher.group(2);
                    this.committerTime = isoDateFormat(committerMatcher.group(3));
                }
            } else if (line.startsWith(PREFIX_AUTHOR)) {
                Matcher authorMatcher = AUTHOR_ENTRY.matcher(line);
                if (authorMatcher.matches() && authorMatcher.groupCount() >= 3) {
                    this.author = authorMatcher.group(1).trim();
                    this.authorEmail = authorMatcher.group(2);
                    this.authorTime = isoDateFormat(authorMatcher.group(3));
                }
            } else if (line.startsWith("    ")) {
                message.append(line.substring(4)).append('\n');
            } else if (':' == line.charAt(0)) {
                Matcher fileMatcher = FILE_LOG_ENTRY.matcher(line);
                if (fileMatcher.matches() && fileMatcher.groupCount() >= 4) {
                    String mode = fileMatcher.group(3);
                    if (mode.length() == 1) {
                        String src = null;
                        String dst = null;
                        String path = fileMatcher.group(4);
                        char editMode = mode.charAt(0);
                        if (editMode == 'M' || editMode == 'A' || editMode == 'D'
                            || editMode == 'R' || editMode == 'C') {
                            src = parseHash(fileMatcher.group(1));
                            dst = parseHash(fileMatcher.group(2));
                        }

                        // Handle rename as two operations - a delete and an add
                        if (editMode == 'R') {
                            Matcher renameSplitMatcher = RENAME_SPLIT.matcher(path);
                            if (renameSplitMatcher.matches() && renameSplitMatcher.groupCount() >= 2) {
                                String oldPath = renameSplitMatcher.group(1);
                                String newPath = renameSplitMatcher.group(2);
                                this.paths.add(new Path(src, dst, 'D', oldPath, this));
                                this.paths.add(new Path(src, dst, 'A', newPath, this));
                            }
                        }
                        // Handle copy as an add
                        else if (editMode == 'C') {
                            Matcher copySplitMatcher = RENAME_SPLIT.matcher(path);
                            if (copySplitMatcher.matches() && copySplitMatcher.groupCount() >= 2) {
                                String newPath = copySplitMatcher.group(2);
                                this.paths.add(new Path(src, dst, 'A', newPath, this));
                            }
                        }
                        else {
                            this.paths.add(new Path(src, dst, editMode, path, this));
                        }
                    }
                }
            }
        }
        this.comment = message.toString();
        int endOfFirstLine = this.comment.indexOf('\n');
        if (endOfFirstLine == -1) {
            this.title = this.comment.trim();
        } else {
            this.title = this.comment.substring(0, endOfFirstLine).trim();
        }
         if(!showEntireCommitSummaryInChanges){
            this.title = splitString(this.title, TRUNCATE_LIMIT);
        }
    }

    /* Package protected for testing */
    static String splitString(String msg, int lineSize) {
        if (msg ==  null) return "";
        if (msg.matches(".*[\r\n].*")) {
            String [] msgArray = msg.split("[\r\n]");
            msg = msgArray[0];
        }
        if (msg.length() <= lineSize || !msg.contains(" ")) {
            return msg;
        }
        int lastSpace = msg.lastIndexOf(' ', lineSize);
        if (lastSpace == -1) {
            /* String contains a space but space is outside truncation limit, truncate at first space */
            lastSpace = msg.indexOf(' ');
        }
        return (lastSpace == -1) ? msg : msg.substring(0, lastSpace);
    }

    /** Convert to iso date format if required */
    private String isoDateFormat(String s) {
        String date = s;
        String timezone = "Z";
        int spaceIndex = s.indexOf(' ');
        if (spaceIndex > 0) {
            date = s.substring(0, spaceIndex);
            timezone = s.substring(spaceIndex+1);
        }
        if (NumberUtils.isDigits(date)) {
            // legacy mode
            long time = Long.parseLong(date);
            DateFormat formatter = new SimpleDateFormat(ISO_8601);
            formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            return formatter.format(new Date(time * 1000)) + timezone;
        } else {
            // already in ISO format
            return s;
        }
    }

    private String parseHash(String hash) {
        return NULL_HASH.equals(hash) ? null : hash;
    }

    @Exported
    public String getDate() {
        return authorOrCommitter ? authorTime : committerTime;
    }

    @Exported
    public String getAuthorEmail() {
        return authorOrCommitter ? authorEmail : committerEmail;
    }

    @Override
    public long getTimestamp() {
        String date = getDate();
        if (date == null) {
            LOGGER.log(Level.WARNING, "Failed to parse null date");
            return -1;
        }
        if (date.isEmpty()) {
            LOGGER.log(Level.WARNING, "Failed to parse empty date");
            return -1;
        }

        for (DateTimeFormatter dateFormatter : dateFormatters) {
            try {
                ZonedDateTime dateTime = ZonedDateTime.parse(date, dateFormatter);
                return dateTime.toEpochSecond()* 1000L;
            } catch (DateTimeParseException | IllegalArgumentException e) {
            }
        }
        try {
            LOGGER.log(Level.FINE, "Parsing {0} with SimpleDateFormat because other parsers failed", date);
            return new SimpleDateFormat(ISO_8601_WITH_TZ).parse(date).getTime();
        } catch (IllegalArgumentException | ParseException e) {
            return -1;
        }
    }

    @Override
    public String getCommitId() {
        return id;
    }

    @Override
    public void setParent(ChangeLogSet parent) {
        super.setParent(parent);
    }

    public @CheckForNull
    String getParentCommit() {
        return parentCommit;
    }

    @Override
    public Collection<String> getAffectedPaths() {
        Collection<String> affectedPaths = new HashSet<>(this.paths.size());
        for (Path file : this.paths) {
            affectedPaths.add(file.getPath());
        }
        return affectedPaths;
    }

    /**
     * Gets the files that are changed in this commit.
     * @return
     *      can be empty but never null.
     */
    @Exported
    @SuppressFBWarnings(value="EI_EXPOSE_REP", justification="Low risk")
    public Collection<Path> getPaths() {
        return paths;
    }

    @Override
    @SuppressFBWarnings(value="EI_EXPOSE_REP", justification="Low risk")
    public Collection<Path> getAffectedFiles() {
        return this.paths;
    }

    /**
     * Returns user of the change set.
     *
     * @param csAuthor user name.
     * @param csAuthorEmail user email.
     * @param createAccountBasedOnEmail true if create new user based on committer's email.
     * @return {@link User}
     * @deprecated  Use {@link #findOrCreateUser(String,String,boolean,boolean)}
     */
    @Deprecated
    public User findOrCreateUser(String csAuthor, String csAuthorEmail, boolean createAccountBasedOnEmail) {
        return findOrCreateUser(csAuthor, csAuthorEmail, createAccountBasedOnEmail, false);
    }

    /**
     * Returns user of the change set.
     *
     * @param csAuthor user name.
     * @param csAuthorEmail user email.
     * @param createAccountBasedOnEmail true if create new user based on committer's email.
     * @param useExistingAccountWithSameEmail true if users should be searched for their email attribute
     * @return {@link User}
     */
    public User findOrCreateUser(String csAuthor, String csAuthorEmail, boolean createAccountBasedOnEmail,
                                 boolean useExistingAccountWithSameEmail) {
        User user;
        if (csAuthor == null) {
            return User.getUnknown();
        }
        if (createAccountBasedOnEmail) {
            if (csAuthorEmail == null || csAuthorEmail.isEmpty()) {
                // Avoid exception from User.get("", false)
                return User.getUnknown();
            }
            user = User.get(csAuthorEmail, false, Collections.emptyMap());

            if (user == null) {
                try {
                    user = User.get(csAuthorEmail, !useExistingAccountWithSameEmail, Collections.emptyMap());
                    boolean setUserDetails = true;
                    if (user == null && useExistingAccountWithSameEmail && hasMailerPlugin()) {
                        for(User existingUser : User.getAll()) {
                            if (csAuthorEmail.equalsIgnoreCase(getMail(existingUser))) {
                                user = existingUser;
                                setUserDetails = false;
                                break;
                            }
                        }
                    }
                    if (user == null) {
                        user = User.get(csAuthorEmail, true, Collections.emptyMap());
                    }
                    if (user != null && setUserDetails) {
                        user.setFullName(csAuthor);
                        if (hasMailerPlugin())
                            setMail(user, csAuthorEmail);
                        user.save();
                    }
                } catch (IOException e) {
                    // add logging statement?
                }
            }
        } else {
            if (csAuthor.isEmpty()) {
                // Avoid exception from User.get("", false)
                return User.getUnknown();
            }
            user = User.get(csAuthor, false, Collections.emptyMap());

            if (user == null) {
                if (csAuthorEmail == null || csAuthorEmail.isEmpty()) {
                    return User.getUnknown();
                }
                // Ensure that malformed email addresses (in this case, just '@')
                // don't mess us up.
                String[] emailParts = csAuthorEmail.split("@");
                if (emailParts.length > 0) {
                    try {
                        user = User.get(emailParts[0], true, Collections.emptyMap());
                    } catch (org.springframework.security.core.AuthenticationException authException) {
                        // JENKINS-67491 - do not fail due to an authentication exception
                        return User.getUnknown();
                    }
                } else {
                    return User.getUnknown();
                }
            }
        }
        // set email address for user if none is already available
        if (fixEmpty(csAuthorEmail) != null && hasMailerPlugin() && !hasMail(user)) {
            try {
                setMail(user, csAuthorEmail);
            } catch (IOException e) {
                // ignore
            }
        }
        return user;
    }

    private String getMail(User user) {
        hudson.tasks.Mailer.UserProperty property = user.getProperty(hudson.tasks.Mailer.UserProperty.class);
        if (property == null) {
            return null;
        }
        if (!property.hasExplicitlyConfiguredAddress()) {
            return null;
        }
        return property.getExplicitlyConfiguredAddress();
    }

    private void setMail(User user, String csAuthorEmail) throws IOException {
        user.addProperty(new hudson.tasks.Mailer.UserProperty(csAuthorEmail));
    }

    private boolean hasMail(User user) {
        String email = getMail(user);
        return email != null;
    }

    private boolean hasMailerPlugin() {
        Plugin p = Jenkins.get().getPlugin("mailer");
        if (p != null) {
            return p.getWrapper().isActive();
        }
        return false;
    }

    private boolean isCreateAccountBasedOnEmail() {
        DescriptorImpl descriptor = getGitSCMDescriptor();

        return descriptor.isCreateAccountBasedOnEmail();
    }

    private boolean isUseExistingAccountWithSameEmail() {
        DescriptorImpl descriptor = getGitSCMDescriptor();

        if (descriptor == null) {
            return false;
        }

        return descriptor.isUseExistingAccountWithSameEmail();
    }

    private DescriptorImpl getGitSCMDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptor(GitSCM.class);
    }

    @Override
    @Exported
    public User getAuthor() {
        String csAuthor;
        String csAuthorEmail;

        // If true, use the author field from git log rather than the committer.
        if (authorOrCommitter) {
            csAuthor = this.author;
            csAuthorEmail = this.authorEmail;
        }
        else {
            csAuthor = this.committer;
            csAuthorEmail = this.committerEmail;
        }

        return findOrCreateUser(csAuthor, csAuthorEmail, isCreateAccountBasedOnEmail(), isUseExistingAccountWithSameEmail());
    }

    /**
     * Gets the author name for this changeset - note that this is mainly here
     * so that we can test authorOrCommitter without needing a fully instantiated
     * Hudson (which is needed for User.get in getAuthor()).
     *
     * @return author name
     */
    public String getAuthorName() {
        // If true, use the author field from git log rather than the committer.
        String csAuthor = authorOrCommitter ? author : committer;
        return csAuthor;
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

    public String getRevision() {
        return this.id;
    }

    @Exported
    public String getComment() {
        return this.comment;
    }

    /**
     * Gets {@linkplain #getComment() the comment} fully marked up by {@link ChangeLogAnnotator}.
     @return annotated comment
     */
    public String getCommentAnnotated() {
        MarkupText markup = new MarkupText(getComment());
        for (ChangeLogAnnotator a : ChangeLogAnnotator.all())
            a.annotate(getParent().getRun(), this, markup);

        return markup.toString(false);
    }

    public String getBranch() {
        return null;
    }

    @ExportedBean(defaultVisibility=999)
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

        @Exported(name="file")
        public String getPath() {
            return path;
        }

        @SuppressFBWarnings(value="EI_EXPOSE_REP", justification="Low risk")
        public GitChangeSet getChangeSet() {
            return changeSet;
        }

        @Exported
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GitChangeSet that = (GitChangeSet) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : super.hashCode();
    }
}
