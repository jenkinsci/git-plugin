package hudson.plugins.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.extensions.impl.AuthorInChangelog;
import hudson.plugins.git.extensions.impl.ShowAuthorAndCommitterInChangelog;
import hudson.scm.ChangeLogSet;
import net.sf.json.JSONObject;
import org.eclipse.jgit.lib.PersonIdent;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the git author/committer identity methods
 * added in issue #3831.
 */
class GitChangeSetIdentityTest extends AbstractGitTestCase {

    private static final PersonIdent AUTHOR = new PersonIdent("Real Author", "author@example.com");
    private static final PersonIdent COMMITTER = new PersonIdent("Bot Committer", "bot@ci.example.com");

    @Test
    void testGitIdentityFieldsInChangeSet() throws Exception {
        // Given a commit where author and committer differ
        FreeStyleProject project = setupSimpleProject("master");
        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");

        testRepo.commit("feature.txt", "content", AUTHOR, COMMITTER, "Feature by author, committed by bot");
        FreeStyleBuild build = build(project, Result.SUCCESS, "feature.txt");

        // When reading the change set
        ChangeLogSet<? extends ChangeLogSet.Entry> changeLog = build.getChangeSet();
        assertEquals(1, changeLog.getItems().length);

        // Then both author and committer fields are populated independently
        GitChangeSet cs = (GitChangeSet) changeLog.getItems()[0];
        assertEquals("Real Author", cs.getGitAuthorName());
        assertEquals("author@example.com", cs.getGitAuthorEmail());
        assertEquals("Bot Committer", cs.getGitCommitterName());
        assertEquals("bot@ci.example.com", cs.getGitCommitterEmail());
        assertNotNull(cs.getGitAuthorDate());
        assertNotNull(cs.getGitCommitterDate());
    }

    @Test
    void testGitIdentityFieldsIndependentOfAuthorOrCommitterFlag() throws Exception {
        // Given a project with no AuthorInChangelog extension (default)
        FreeStyleProject project = setupSimpleProject("master");
        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");

        testRepo.commit("feature.txt", "content", AUTHOR, COMMITTER, "Mixed identity commit");
        FreeStyleBuild build = build(project, Result.SUCCESS, "feature.txt");
        GitChangeSet cs = (GitChangeSet) build.getChangeSet().getItems()[0];

        // When reading via the flag-dependent method, it returns the committer
        assertEquals("Bot Committer", cs.getAuthorName());

        // When reading via the new getters, both identities are always available
        assertEquals("Real Author", cs.getGitAuthorName());
        assertEquals("Bot Committer", cs.getGitCommitterName());
    }

    @Test
    void testGitIdentityFieldsWithAuthorInChangelog() throws Exception {
        // Given a project with AuthorInChangelog enabled
        FreeStyleProject project = setupSimpleProject("master");
        GitSCM scm = (GitSCM) project.getScm();
        scm.getExtensions().add(new AuthorInChangelog());

        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");

        testRepo.commit("feature.txt", "content", AUTHOR, COMMITTER, "Another mixed commit");
        FreeStyleBuild build = build(project, Result.SUCCESS, "feature.txt");
        GitChangeSet cs = (GitChangeSet) build.getChangeSet().getItems()[0];

        // When reading via the flag-dependent method, it now returns the author
        assertEquals("Real Author", cs.getAuthorName());

        // When reading via the new getters, both identities remain unchanged
        assertEquals("Real Author", cs.getGitAuthorName());
        assertEquals("Bot Committer", cs.getGitCommitterName());
        assertEquals("author@example.com", cs.getGitAuthorEmail());
        assertEquals("bot@ci.example.com", cs.getGitCommitterEmail());
    }

    @Test
    void testRestApiExposesGitIdentityFields() throws Exception {
        // Given a build with a commit where author and committer differ
        FreeStyleProject project = setupSimpleProject("master");
        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");

        testRepo.commit("api-test.txt", "content", AUTHOR, COMMITTER, "REST API test commit");
        FreeStyleBuild build = build(project, Result.SUCCESS, "api-test.txt");

        // When fetching the build's REST API response
        JenkinsRule.WebClient wc = r.createWebClient();
        Page page = wc.goTo(build.getUrl() + "api/json?depth=1", "application/json");
        JSONObject item = JSONObject.fromObject(page.getWebResponse().getContentAsString())
                .getJSONObject("changeSet").getJSONArray("items").getJSONObject(0);

        // Then both identities appear as separate fields
        assertEquals("Real Author", item.getString("gitAuthorName"));
        assertEquals("author@example.com", item.getString("gitAuthorEmail"));
        assertEquals("Bot Committer", item.getString("gitCommitterName"));
        assertEquals("bot@ci.example.com", item.getString("gitCommitterEmail"));
    }

    @Test
    void testShowAuthorAndCommitterExtensionFlag() throws Exception {
        // Given a project without ShowAuthorAndCommitterInChangelog
        FreeStyleProject project = setupSimpleProject("master");
        GitSCM scm = (GitSCM) project.getScm();

        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");
        testRepo.commit("ext-test.txt", "content", AUTHOR, COMMITTER, "Extension test");
        FreeStyleBuild build = build(project, Result.SUCCESS, "ext-test.txt");

        // Then isShowAuthorAndCommitter returns false
        assertFalse(((GitChangeSetList) build.getChangeSet()).isShowAuthorAndCommitter());

        // Given the extension is added
        scm.getExtensions().add(new ShowAuthorAndCommitterInChangelog());
        testRepo.commit("ext-test2.txt", "content2", AUTHOR, COMMITTER, "Extension test 2");
        FreeStyleBuild build2 = build(project, Result.SUCCESS, "ext-test2.txt");

        // Then isShowAuthorAndCommitter returns true
        assertTrue(((GitChangeSetList) build2.getChangeSet()).isShowAuthorAndCommitter());
    }

    @Test
    void testGitIdentityFieldsSameAuthorAndCommitter() throws Exception {
        // Given a commit where author and committer are the same person
        FreeStyleProject project = setupSimpleProject("master");
        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");

        testRepo.commit("same.txt", "content", johnDoe, johnDoe, "Same person commit");
        FreeStyleBuild build = build(project, Result.SUCCESS, "same.txt");
        GitChangeSet cs = (GitChangeSet) build.getChangeSet().getItems()[0];

        // Then both getters return the same values
        assertEquals(johnDoe.getName(), cs.getGitAuthorName());
        assertEquals(johnDoe.getName(), cs.getGitCommitterName());
        assertEquals(johnDoe.getEmailAddress(), cs.getGitAuthorEmail());
        assertEquals(johnDoe.getEmailAddress(), cs.getGitCommitterEmail());
    }

    @Test
    void testChangelogShowsBothIdentitiesWhenExtensionEnabled() throws Exception {
        // Given ShowAuthorAndCommitterInChangelog is enabled and author differs from committer
        FreeStyleProject project = setupSimpleProject("master");
        GitSCM scm = (GitSCM) project.getScm();
        scm.getExtensions().add(new ShowAuthorAndCommitterInChangelog());

        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");
        testRepo.commit("feature.txt", "content", AUTHOR, COMMITTER, "Feature by author");
        FreeStyleBuild build = build(project, Result.SUCCESS, "feature.txt");

        // When viewing the changelog
        HtmlPage page = r.createWebClient().getPage(build, "changes");
        String pageContent = page.getBody().getTextContent();

        // Then both identities are shown with explicit labels
        assertThat(pageContent, containsString("Real Author"));
        assertThat(pageContent, containsString("Bot Committer"));
        assertThat(pageContent, containsString("authored by"));
        assertThat(pageContent, containsString("committed by"));
    }

    @Test
    void testChangelogShowsSingleIdentityWithoutExtension() throws Exception {
        // Given no ShowAuthorAndCommitterInChangelog extension
        FreeStyleProject project = setupSimpleProject("master");

        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");
        testRepo.commit("feature.txt", "content", AUTHOR, COMMITTER, "Feature by author");
        FreeStyleBuild build = build(project, Result.SUCCESS, "feature.txt");

        // When viewing the changelog
        HtmlPage page = r.createWebClient().getPage(build, "changes");
        String pageContent = page.getBody().getTextContent();

        // Then no dual-identity labels are shown
        assertThat(pageContent, not(containsString("authored by")));
        assertThat(pageContent, not(containsString("committed by")));
    }

    @Test
    void testChangelogDoesNotLabelWhenSameIdentity() throws Exception {
        // Given ShowAuthorAndCommitterInChangelog is enabled but author and committer are the same
        FreeStyleProject project = setupSimpleProject("master");
        GitSCM scm = (GitSCM) project.getScm();
        scm.getExtensions().add(new ShowAuthorAndCommitterInChangelog());

        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");
        testRepo.commit("same.txt", "content", AUTHOR, AUTHOR, "Same person");
        FreeStyleBuild build = build(project, Result.SUCCESS, "same.txt");

        // When viewing the changelog
        HtmlPage page = r.createWebClient().getPage(build, "changes");
        String pageContent = page.getBody().getTextContent();

        // Then it falls back to single-identity rendering
        assertThat(pageContent, not(containsString("authored by")));
        assertThat(pageContent, not(containsString("committed by")));
    }

    @Test
    void testShowBothWithAuthorInChangelogExtension() throws Exception {
        // Given both AuthorInChangelog and ShowAuthorAndCommitterInChangelog are enabled
        FreeStyleProject project = setupSimpleProject("master");
        GitSCM scm = (GitSCM) project.getScm();
        scm.getExtensions().add(new AuthorInChangelog());
        scm.getExtensions().add(new ShowAuthorAndCommitterInChangelog());

        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");
        testRepo.commit("both.txt", "content", AUTHOR, COMMITTER, "Both extensions");
        FreeStyleBuild build = build(project, Result.SUCCESS, "both.txt");

        // Then AuthorInChangelog still controls getAuthorName, and both raw getters work
        GitChangeSet cs = (GitChangeSet) build.getChangeSet().getItems()[0];
        assertEquals("Real Author", cs.getAuthorName());
        assertEquals("Real Author", cs.getGitAuthorName());
        assertEquals("Bot Committer", cs.getGitCommitterName());

        // And the changelog shows both identities
        HtmlPage page = r.createWebClient().getPage(build, "changes");
        String pageContent = page.getBody().getTextContent();
        assertThat(pageContent, containsString("Real Author"));
        assertThat(pageContent, containsString("Bot Committer"));
    }

    @Test
    void testRestApiExposesDateFields() throws Exception {
        // Given a build with a commit where author and committer differ
        FreeStyleProject project = setupSimpleProject("master");
        commit("initial.txt", johnDoe, "Initial commit");
        build(project, Result.SUCCESS, "initial.txt");

        testRepo.commit("dates.txt", "content", AUTHOR, COMMITTER, "Date test commit");
        FreeStyleBuild build = build(project, Result.SUCCESS, "dates.txt");

        // When fetching the build's REST API response
        Page page = r.createWebClient().goTo(build.getUrl() + "api/json?depth=1", "application/json");
        JSONObject item = JSONObject.fromObject(page.getWebResponse().getContentAsString())
                .getJSONObject("changeSet").getJSONArray("items").getJSONObject(0);

        // Then both date fields are present and non-empty
        assertTrue(item.has("gitAuthorDate"));
        assertTrue(item.has("gitCommitterDate"));
        assertFalse(item.getString("gitAuthorDate").isEmpty());
        assertFalse(item.getString("gitCommitterDate").isEmpty());
    }
}
