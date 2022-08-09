package hudson.plugins.git;

import java.io.File;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.plugins.git.extensions.impl.LocalBranch;

import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import jenkins.plugins.git.GitSampleRepoRule;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Test git tag action. Low value test that was created as part of
 * another investigation.
 *
 * Unreliable on ci.jenkins.io Windows agents. Results are not worth
 * sacrificing other things in order to investigate.  Runs reliably on
 * Unix-like operating systems.  Runs reliably on Mark Waite's windows
 * computers.
 *
 * @author Mark Waite
 */
public class GitTagActionTest {

    private static GitTagAction noTagAction;
    private static GitTagAction tagOneAction;
    private static GitTagAction tagTwoAction;

    private static final Random random = new Random();

    private static final String NO_BRANCHES = "tagRevision-with-no-branches";

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @ClassRule
    public static GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    public GitTagActionTest() {
    }

    private static FreeStyleProject p;
    private static GitClient workspaceGitClient = null;

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("-yyyy-MM-dd-H-m-ss.SS");
    private static final String TAG_PREFIX = "test-tag-";
    private static final String TAG_SUFFIX = LocalDateTime.now().format(FORMAT);
    private static final String INITIAL_COMMIT_MESSAGE = "init" + TAG_SUFFIX + "-" + random.nextInt(10000);
    private static final String ADDED_COMMIT_MESSAGE_BASE = "added" + TAG_SUFFIX;
    private static String sampleRepoHead = null;
    private static DescriptorImpl gitSCMDescriptor = null;

    @BeforeClass
    public static void deleteMatchingTags() throws Exception {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            return;
        }
        /* Remove tags from working repository that start with TAG_PREFIX and don't contain TAG_SUFFIX */
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars())
                .in(new File("."))
                .using(chooseGitImplementation()) // Use random implementation, both should work
                .getClient();
        for (GitObject tag : gitClient.getTags()) {
            if (tag.getName().startsWith(TAG_PREFIX) && !tag.getName().contains(TAG_SUFFIX)) {
                gitClient.deleteTag(tag.getName());
            }
        }
    }

    @BeforeClass
    public static void createThreeGitTagActions() throws Exception {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            return;
        }
        sampleRepo.init();
        sampleRepo.write("file", INITIAL_COMMIT_MESSAGE);
        sampleRepo.git("commit", "--all", "--message=" + INITIAL_COMMIT_MESSAGE);
        sampleRepoHead = sampleRepo.head();
        List<UserRemoteConfig> remotes = new ArrayList<>();
        String refSpec = "+refs/heads/master:refs/remotes/origin/master";
        remotes.add(new UserRemoteConfig(sampleRepo.fileUrl(), "origin", refSpec, ""));
        GitSCM scm = new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("origin/master")),
                null,
                chooseGitImplementation(), // Both git implementations should work, choose randomly
                Collections.emptyList());
        scm.getExtensions().add(new LocalBranch("master"));
        p = r.createFreeStyleProject();
        p.setScm(scm);

        /* Add git tag action to builds for this test */
        gitSCMDescriptor = scm.getDescriptor();
        gitSCMDescriptor.setAddGitTagAction(true);

        /* Run with no tag action defined */
        noTagAction = createTagAction(null);

        /* Run with first tag action defined */
        tagOneAction = createTagAction("v1");

        /* Wait for tag creation threads to complete, then assert conditions */
        waitForTagCreation(tagOneAction, "v1");

        /* Run with second tag action defined */
        tagTwoAction = createTagAction("v2");

        /* Wait for tag creation threads to complete, then assert conditions */
        waitForTagCreation(tagTwoAction, "v2");

        assertThat(getMatchingTagNames(), hasItems(getTagValue("v1"), getTagValue("v2")));

        /* Create tag action with special message that tells tag action to create a null list of branches */
        /* JENKINS-64279 reports a null pointer exception in this case */
        GitTagAction tagNullBranchesAction = createTagAction(NO_BRANCHES);
        assertThat(tagNullBranchesAction, is(not(nullValue())));
    }

    @AfterClass
    public static void disableAddGitTagAction() {
        /* Do not add git tag action to builds for other tests */
        if (gitSCMDescriptor != null) {
            gitSCMDescriptor.setAddGitTagAction(false);
        }
    }

    private static String getTagName(String message) {
        return TAG_PREFIX + message + TAG_SUFFIX;
    }

    private static String getTagValue(String message) {
        return getTagName(message) + "-value";
    }

    private static String getTagComment(String message) {
        return getTagName(message) + "-comment";
    }

    private static int messageCounter = 1;

    /**
     * Return a GitTagAction which uses 'message' in the tag name, tag value, and tag comment.
     * If 'message' is null, the GitTagAction is returned but tag creation is not scheduled.
     *
     * @param message value to use in tag name, value, and comment when scheduling tag creation.  If null, tag is not created.
     * @return tag action which uses 'message' in the tag name, value, and comment
     * @throws Exception on error
     */
    private static GitTagAction createTagAction(String message) throws Exception {
        /* Run with a tag action defined */
        String commitMessage = message == null ? ADDED_COMMIT_MESSAGE_BASE + "-" + messageCounter++ : message;
        sampleRepo.write("file", message);
        sampleRepo.git("commit", "--all", "--message=" + commitMessage);
        List<Branch> masterBranchList = new ArrayList<>();
        ObjectId tagObjectId = ObjectId.fromString(sampleRepo.head());
        masterBranchList.add(new Branch("master", tagObjectId));
        Revision tagRevision = new Revision(tagObjectId, masterBranchList);

        /* Run the freestyle project and compute its workspace FilePath */
        Run<?, ?> tagRun = r.buildAndAssertSuccess(p);
        FilePath workspace = r.jenkins.getWorkspaceFor(p);

        /* Create a GitClient for the workspace */
        if (workspaceGitClient == null) {
            /* Assumes workspace does not move after first run */
            workspaceGitClient = Git.with(TaskListener.NULL, new EnvVars())
                    .in(workspace)
                    .using(chooseGitImplementation()) // Use random implementation, both should work
                    .getClient();
        }
        /* Fail if the workspace moved */
        assertThat(workspace, is(workspaceGitClient.getWorkTree()));

        /* Fail if initial commit and subsequent commit not detected in workspace */
        StringWriter stringWriter = new StringWriter();
        workspaceGitClient.changelog(sampleRepoHead + "^", "HEAD", stringWriter);
        assertThat(stringWriter.toString(), containsString(INITIAL_COMMIT_MESSAGE));
        assertThat(stringWriter.toString(), containsString(commitMessage));

        /* Fail if master branch is not defined in the workspace */
        assertThat(workspaceGitClient.getRemoteUrl("origin"), is(sampleRepo.fileUrl().replace("file:/", "file:///")));
        Set<Branch> branches = workspaceGitClient.getBranches();
        if (branches.isEmpty()) {
            /* Should not be required since the LocalBranch extension was enabled */
            workspaceGitClient.branch("master");
            branches = workspaceGitClient.getBranches();
            assertThat(branches, is(not(empty())));
        }
        boolean foundMasterBranch = false;
        String lastBranchName = null;
        for (Branch branch : branches) {
            lastBranchName = branch.getName();
            assertThat(lastBranchName, endsWith("master"));
            if (lastBranchName.equals("master")) {
                foundMasterBranch = true;
            }
        }
        assertTrue("master branch not found, last branch name was " + lastBranchName, foundMasterBranch);

        /* Create the GitTagAction */
        GitTagAction tagAction;
        if (NO_BRANCHES.equals(message)) {
            tagRevision.setBranches(null);
        }
        tagAction = new GitTagAction(tagRun, workspace, tagRevision);

        /* Schedule tag creation if message is not null */
        if (message != null) {
            String tagName = getTagName(message);
            String tagValue = getTagValue(message);
            String tagComment = getTagComment(message);
            Map<String, String> tagMap = new HashMap<>();
            tagMap.put(tagName, tagValue);
            tagAction.scheduleTagCreation(tagMap, tagComment);
        }
        return tagAction;
    }

    private static Set<String> getMatchingTagNames() throws Exception {
        Set<GitObject> tags = workspaceGitClient.getTags();
        Set<String> matchingTagNames = new HashSet<>();
        for (GitObject tag : tags) {
            if (tag.getName().startsWith(TAG_PREFIX)) {
                matchingTagNames.add(tag.getName());
            }
        }
        return matchingTagNames;
    }

    private static void waitForTagCreation(GitTagAction tagAction, String message) throws Exception {
        long backoffDelay = 499L;
        while (tagAction.getLastTagName() == null && tagAction.getLastTagException() == null && backoffDelay < 8000L) {
            backoffDelay = backoffDelay * 2;
            Thread.sleep(backoffDelay); // Allow some time for tag creation
        }
        assertThat(tagAction.getLastTagException(), is(nullValue()));
        assertThat(tagAction.getLastTagName(), is(getTagValue(message)));
    }

    @Test
    public void testDoPost() throws Exception {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }

        JenkinsRule.WebClient browser = r.createWebClient();

        HtmlPage tagPage3 = browser.getPage(p, "/3/tagBuild");
        HtmlForm form3 = tagPage3.getFormByName("tag");
        form3.getInputByName("name0").setValueAttribute("tag-build-3");
        HtmlPage submitted3 = r.submit(form3);
    }

    @Test
    public void testGetDescriptor() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        Descriptor<GitTagAction> descriptor = noTagAction.getDescriptor();
        assertThat(descriptor.getDisplayName(), is("Tag"));
    }

    @Test
    public void testIsNotTagged() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        assertFalse(noTagAction.isTagged());
    }

    @Test
    public void testGetDisplayNameNoTagAction() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        assertThat(noTagAction.getDisplayName(), is("No Tags"));
    }

    @Test
    public void testGetIconFileName() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        assertThat(noTagAction.getIconFileName(), is("save.gif"));
    }

    @Test
    public void testGetTagsNoTagAction() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        Collection<List<String>> valueList = noTagAction.getTags().values();
        for (List<String> value : valueList) {
            assertThat(value, is(empty()));
        }
    }

    @Test
    public void testGetTagsOneTagAction() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        Collection<List<String>> valueList = tagOneAction.getTags().values();
        for (List<String> value : valueList) {
            assertThat(value, is(empty()));
        }
    }

    @Test
    public void testGetTagsTwoTagAction() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        Collection<List<String>> valueList = tagTwoAction.getTags().values();
        for (List<String> value : valueList) {
            assertThat(value, is(empty()));
        }
    }

    @Test
    public void testGetTagInfo() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        assertThat(noTagAction.getTagInfo(), is(empty()));
    }

    @Test
    public void testGetTooltipNoTagAction() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        assertThat(noTagAction.getTooltip(), is(nullValue()));
    }

    @Test
    public void testGetPermission() {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        assertThat(noTagAction.getPermission(), is(GitSCM.TAG));
        assertThat(tagOneAction.getPermission(), is(GitSCM.TAG));
    }

    private static String chooseGitImplementation() {
        return random.nextBoolean() ? "git" : "jgit";
    }

    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote classloader issue
     */
    private static boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
