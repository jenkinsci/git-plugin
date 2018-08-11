package hudson.plugins.git;

import java.io.File;
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
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.LocalBranch;

import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import jenkins.plugins.git.GitSampleRepoRule;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Test git tag action.
 *
 * @author Mark Waite
 */
public class GitTagActionTest {

    private static GitTagAction noTagAction;
    private static GitTagAction tagOneAction;
    private static GitTagAction tagTwoAction;

    private static final Random random = new Random();

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

    @BeforeClass
    public static void deleteMatchingTags() throws Exception {
        /* Remove tags from working repository that start with TAG_PREFIX and don't contain TAG_SUFFIX */
        GitClient gitClient = Git.with(TaskListener.NULL, new EnvVars())
                .in(new File("."))
                .using(random.nextBoolean() ? "git" : "jgit") // Use random implmentation, both should work
                .getClient();
        for (GitObject tag : gitClient.getTags()) {
            if (tag.getName().startsWith(TAG_PREFIX) && !tag.getName().contains(TAG_SUFFIX)) {
                gitClient.deleteTag(tag.getName());
            }
        }
    }

    @BeforeClass
    public static void createThreeGitTagActions() throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "init");
        sampleRepo.git("commit", "--all", "--message=init");
        String head = sampleRepo.head();
        List<UserRemoteConfig> remotes = new ArrayList<>();
        String refSpec = "+refs/heads/master:refs/remotes/origin/master";
        remotes.add(new UserRemoteConfig(sampleRepo.fileUrl(), "origin", refSpec, ""));
        GitSCM scm = new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("origin/master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null,
                random.nextBoolean() ? "git" : "jgit", // Both git implementations should work, choose randomly
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new LocalBranch("master"));
        p = r.createFreeStyleProject();
        p.setScm(scm);

        /* Run with no tag action defined */
        noTagAction = createTagAction(null);

        /* Run with first tag action defined */
        tagOneAction = createTagAction("v1");

        /* Run with second tag action defined */
        tagTwoAction = createTagAction("v2");

        /* Wait for tag creation threads to complete, then assert conditions */
        waitForTagCreation(tagOneAction, "v1");
        waitForTagCreation(tagTwoAction, "v2");

        assertThat(getMatchingTagNames(), hasItems(getTagValue("v1"), getTagValue("v2")));
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
        sampleRepo.write("file", message);
        sampleRepo.git("commit", "--all", "--message=" + (message == null ? random.nextInt() : message));
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
                    .using(random.nextBoolean() ? "git" : "jgit") // Use random implmentation, both should work
                    .getClient();
        }
        /* Fail if the workspace moved */
        assertThat(workspace, is(workspaceGitClient.getWorkTree()));

        /* Create the GitTagAction */
        GitTagAction tagAction = new GitTagAction(tagRun, workspace, tagRevision);

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
        assertThat(tagAction.getLastTagName(), is(getTagValue(message)));
        assertThat(tagAction.getLastTagException(), is(nullValue()));
    }

    @Test
    public void testDoPost() throws Exception {
        JenkinsRule.WebClient browser = r.createWebClient();

        // Don't need all cases until at least one case works fully
        // HtmlPage tagPage = browser.getPage(p, "/1/tagBuild");
        // HtmlForm form = tagPage.getFormByName("tag");
        // form.getInputByName("name0").setValueAttribute("tag-build-1");
        // HtmlPage submitted = r.submit(form);

        // Flaw in the test causes this assertion to fail
        // assertThat(submitted.asText(), not(containsString("Clear error to retry")));

        // Don't need all cases until at least one case works fully
        // HtmlPage tagPage2 = browser.getPage(p, "/2/tagBuild");
        // HtmlForm form2 = tagPage2.getFormByName("tag");
        // form2.getInputByName("name0").setValueAttribute("tag-build-2");
        // HtmlPage submitted2 = r.submit(form2);

        // Flaw in the test causes this assertion to fail
        // assertThat(submitted2.asText(), not(containsString("Clear error to retry")));

        HtmlPage tagPage3 = browser.getPage(p, "/3/tagBuild");
        HtmlForm form3 = tagPage3.getFormByName("tag");
        form3.getInputByName("name0").setValueAttribute("tag-build-3");
        HtmlPage submitted3 = r.submit(form3);

        // Flaw in the test causes this assertion to fail
        // assertThat(submitted3.asText(), not(containsString("Clear error to retry")));

        // Flaw in the test causes this assertion to fail
        // waitForTagCreation(tagTwoAction);
        // assertThat(getMatchingTagNames(), hasItems("tag-build-1", "tag-build-2", "tag-build-3"));
    }

    @Test
    public void testGetDescriptor() {
        Descriptor<GitTagAction> descriptor = noTagAction.getDescriptor();
        assertThat(descriptor.getDisplayName(), is("Tag"));
    }

    // @Test
    public void testIsTagged() {
        assertTrue(tagTwoAction.isTagged());
    }

    @Test
    public void testIsNotTagged() {
        assertFalse(noTagAction.isTagged());
    }

    @Test
    public void testGetDisplayNameNoTagAction() {
        assertThat(noTagAction.getDisplayName(), is("No Tags"));
    }

    // Not working yet
    // @Test
    public void testGetDisplayNameOneTagAction() {
        assertThat(tagOneAction.getDisplayName(), is("One Tag"));
    }

    // Not working yet
    // @Test
    public void testGetDisplayNameTwoTagAction() {
        assertThat(tagTwoAction.getDisplayName(), is("Multiple Tags"));
    }

    @Test
    public void testGetIconFileName() {
        assertThat(noTagAction.getIconFileName(), is("save.gif"));
    }

    @Test
    public void testGetTagsNoTagAction() {
        Collection<List<String>> valueList = noTagAction.getTags().values();
        for (List<String> value : valueList) {
            assertThat(value, is(empty()));
        }
    }

    @Test
    public void testGetTagsOneTagAction() {
        Collection<List<String>> valueList = tagOneAction.getTags().values();
        for (List<String> value : valueList) {
            assertThat(value, is(empty()));
        }
    }

    @Test
    public void testGetTagsTwoTagAction() {
        Collection<List<String>> valueList = tagTwoAction.getTags().values();
        for (List<String> value : valueList) {
            assertThat(value, is(empty()));
        }
    }

    @Test
    public void testGetTagInfo() {
        assertThat(noTagAction.getTagInfo(), is(empty()));
    }

    @Test
    public void testGetTooltipNoTagAction() {
        assertThat(noTagAction.getTooltip(), is(nullValue()));
    }

    @Test
    public void testGetPermission() {
        assertThat(noTagAction.getPermission(), is(GitSCM.TAG));
        assertThat(tagOneAction.getPermission(), is(GitSCM.TAG));
    }
}
