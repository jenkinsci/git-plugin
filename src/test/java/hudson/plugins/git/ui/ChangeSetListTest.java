package hudson.plugins.git.ui;

import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitChangeSetList;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.SCM;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * Unit test for {@link GitChangeSetList} generated HTML code. Ideally, this UT
 * should run on newer HtmlUnit, than one we have in Jenkins test harness (at
 * least version 2.9, which supports <code>document.querySelectorAll()</code>.
 * @author Pavel Baranchikov
 * @see http://htmlunit.sourceforge.net/changes-report.html#a2.9
 * @see https://groups.google.com/d/msgid/jenkinsci-dev/664bb4c6-f6d7-4178-9c11-
 *      d759974aa49f%40googlegroups.com?utm_medium=email&utm_source=footer
 * @see JENKINS-25995
 */
public class ChangeSetListTest extends AbstractGitTestCase {

    private com.gargoylesoftware.htmlunit.WebClient webClient;
    private FreeStyleProject project;
    private int fileCounter;
    private static final String HIDE_ID = "git.plugin.GitChangeSetList.digest.hide";
    private static final String SHOW_ID = "git.plugin.GitChangeSetList.digest.show";
    private static final String CHAHGE_SET_LIST = "git.plugin.chageSetList";
    private static final String MIME = "text/html";
    private static final String TAG_ITEM = "li";
    private static final String CHANGES_SHOULD_NOT_BE_NULL = "Changes list should not exist";
    private static final String CHANGES_SHOULD_BE_NULL = "Changes list should exist";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // Use directly HtmlUnit's webclient to switch easily to newer Html
        // (manually) to perform JavaScript tests
        webClient = new com.gargoylesoftware.htmlunit.WebClient();
        // We are to ignore Javascript errors, as HtmlUnit is obsolete
        webClient.setThrowExceptionOnScriptError(false);
        fileCounter = 0;
    }

    /**
     * Test for various behaviour on changes list. Javascript dinamic showing
     * and hiding elements is not testable because of obsolete HtmlUnit. So,
     * this test still try to open pages, and see for HTTP status code is not
     * 5xx
     * @throws Exception on exception occurs
     */
    @Test
    public void testGitProjectChanges() throws Exception {
        project = setupProject("master", false, null, "exclude.*", null, null);
        commitIncluded();
        build(project, Result.SUCCESS);
        testWithHtml(new PageTester() {
            public void testPage(HtmlObjects input) throws Exception {
                input.assertButtonsExist(false);
                Assert.assertNull(CHANGES_SHOULD_BE_NULL, input.changesList);
            }
        });
        checkRecentChanges(false);

        commitIncluded();
        build(project, Result.SUCCESS);
        testWithHtml(new PageTester() {
            public void testPage(HtmlObjects input) throws Exception {
                input.assertButtonsExist(false);
                Assert.assertNotNull(CHANGES_SHOULD_NOT_BE_NULL, input.changesList);
            }
        });
        checkRecentChanges(false);

        commitExcluded();
        build(project, Result.SUCCESS);
        testWithHtml(new PageTester() {
            public void testPage(HtmlObjects input) throws Exception {
                input.assertButtonsExist(true);
                Assert.assertNotNull(CHANGES_SHOULD_NOT_BE_NULL, input.changesList);
                // Ignoring because of obsolete HtmlUnit
                // testAnchors(input, 1, 1);
            }
        });
        checkRecentChanges(true);

        commitIncluded();
        commitExcluded();
        commitIncluded();
        build(project, Result.SUCCESS);
        testWithHtml(new PageTester() {
            public void testPage(HtmlObjects input) throws Exception {
                input.assertButtonsExist(true);
                Assert.assertNotNull(CHANGES_SHOULD_BE_NULL, input.changesList);
                // Ignoring because of obsolete HtmlUnit
                // testAnchors(input, 3, 1);
            }
        });
        checkRecentChanges(true);
    }

    @SuppressWarnings("unused")
    private static void testAnchors(HtmlObjects htmlObj, int changes, int excluded)
            throws IOException {
        htmlObj.assertShowVisible();
        htmlObj.expectChanges(changes);
        htmlObj.expectVisibleChanges(changes - excluded);
        htmlObj.showElement.click();
        htmlObj.expectChanges(changes);
        htmlObj.expectVisibleChanges(changes);
        htmlObj.assertHideVisible();
        htmlObj.hideElement.click();
        htmlObj.assertShowVisible();
        htmlObj.expectChanges(changes);
        htmlObj.expectVisibleChanges(changes - excluded);
    }

    private static HtmlObjects getHtmlObjects(HtmlPage page) throws Exception {
        final HtmlAnchor hideElement = (HtmlAnchor) page.getElementById(HIDE_ID);
        final HtmlAnchor showElement = (HtmlAnchor) page.getElementById(SHOW_ID);
        final HtmlElement changeElement = (HtmlElement) page.getElementById(CHAHGE_SET_LIST);
        return new HtmlObjects(hideElement, showElement, changeElement);
    }

    private String getBuildUrl(AbstractProject<?, ?> project, AbstractBuild<?, ?> build) {
        return String.format("job/%s/%d/", project.getName(), build.getNumber());
    }

    private String getChangesUrl(AbstractProject<?, ?> project, AbstractBuild<?, ?> build) {
        return String.format("job/%s/%d/changes", project.getName(), build.getNumber());
    }

    private String getRecentChangesUrl(AbstractProject<?, ?> project) {
        return String.format("job/%s/changes", project.getName());
    }

    private HtmlPage goTo(String relative, String expectedContentType) throws IOException,
            SAXException {
        final Page p = webClient.getPage(getURL() + relative);
        assertEquals(expectedContentType, p.getWebResponse().getContentType());
        if (p instanceof HtmlPage) {
            return (HtmlPage) p;
        } else {
            throw new AssertionError("Expected text/html but instead the content type was "
                    + p.getWebResponse().getContentType());
        }
    }

    /**
     * Method performs same tests on different Html pages: summary digest (build
     * page) and changes page.
     * @param function test to perform on pages
     * @throws Exception on exceptions occur
     */
    private void testWithHtml(PageTester function) throws Exception {
        final HtmlPage digest = goTo(getBuildUrl(project, project.getLastBuild()), MIME);
        final HtmlObjects digestObjects = getHtmlObjects(digest);
        function.testPage(digestObjects);
        final HtmlPage changes = goTo(getChangesUrl(project, project.getLastBuild()), MIME);
        final HtmlObjects changesObjects = getHtmlObjects(changes);
        function.testPage(changesObjects);
    }

    private void commit(String commitFile) throws Exception {
        commit(commitFile, johnDoe, "File " + commitFile + " committed");
    }

    private void commitExcluded() throws Exception {
        commit("excluded" + getNextFileName());
    }

    private void commitIncluded() throws Exception {
        commit(getNextFileName());
    }

    private String getNextFileName() {
        return "file" + fileCounter++;
    }

    private void checkRecentChanges(boolean buttonsExist) throws Exception {
        final HtmlPage changesPage = goTo(getRecentChangesUrl(project), MIME);
        final HtmlObjects changesObj = getHtmlObjects(changesPage);
        changesObj.assertButtonsExist(buttonsExist);
    }

    /**
     * Simple test to ensure, that nothing fails on projects with no SCM.
     * @throws Exception on exceptions occur
     */
    @Test
    public void testNoScmProject() throws Exception {
        final FreeStyleProject project = createFreeStyleProject();
        build(project, Result.SUCCESS);

        final HtmlObjects objects1 = getHtmlObjects(goTo(
                getBuildUrl(project, project.getLastBuild()), MIME));
        objects1.assertButtonsExist(false);
        Assert.assertNull(objects1.changesList);
        goTo(getRecentChangesUrl(project), MIME);

        build(project, Result.SUCCESS);
        final HtmlObjects objects2 = getHtmlObjects(goTo(
                getBuildUrl(project, project.getLastBuild()), MIME));
        objects2.assertButtonsExist(false);
        Assert.assertNull(objects2.changesList);
        goTo(getRecentChangesUrl(project), MIME);
    }

    /**
     * Simple test to ensure, that nothing fails on projects with multiple SCMs,
     * including Git.
     * @throws Exception on exceptions occur
     */
    @Test
    public void testMultipleScmProject() throws Exception {
        final TestGitRepo repo0 = new TestGitRepo("repo0", this, listener);
        final TestGitRepo repo1 = new TestGitRepo("repo1", this, listener);
        final FreeStyleProject project = setupMultiScmProject("project1", repo0, repo1);
        repo0.commit("repo0-init", repo0.johnDoe, "repo0 initial commit");
        repo1.commit("repo1-init", repo0.johnDoe, "repo1 initial commit");
        build(project, Result.SUCCESS);
        goTo(getBuildUrl(project, project.getLastBuild()), MIME);
        goTo(getChangesUrl(project, project.getLastBuild()), MIME);
        goTo(getRecentChangesUrl(project), MIME);

        repo1.commit("repo1-1", repo1.johnDoe, "repo1 commit 1");
        repo0.commit("repo0-1", repo0.johnDoe, "repo0 commit 1");
        build(project, Result.SUCCESS);
        goTo(getBuildUrl(project, project.getLastBuild()), MIME);
        goTo(getChangesUrl(project, project.getLastBuild()), MIME);
        goTo(getRecentChangesUrl(project), MIME);
    }

    private FreeStyleProject setupMultiScmProject(String name, TestGitRepo repo0, TestGitRepo repo1)
            throws IOException {
        final FreeStyleProject project = createFreeStyleProject(name);

        final List<BranchSpec> branch = Collections.singletonList(new BranchSpec("master"));

        final SCM repo0Scm = new GitSCM(repo0.remoteConfigs(), branch, false,
                Collections.<SubmoduleConfig> emptyList(), null, null,
                Collections.<GitSCMExtension> emptyList());

        final SCM repo1Scm = new GitSCM(repo1.remoteConfigs(), branch, false,
                Collections.<SubmoduleConfig> emptyList(), null, null,
                Collections.<GitSCMExtension> emptyList());

        final List<SCM> testScms = new ArrayList<SCM>();
        testScms.add(repo0Scm);
        testScms.add(repo1Scm);

        final MultiSCM scm = new MultiSCM(testScms);

        project.setScm(scm);
        project.getBuildersList().add(new CaptureEnvironmentBuilder());
        return project;
    }

    /**
     * Class to hold hide and show anchors and changes list.
     */
    private static class HtmlObjects {
        /**
         * Hide excluded changes anchor.
         */
        final HtmlElement hideElement;
        /**
         * Show excluded changes anchor.
         */
        final HtmlElement showElement;
        /**
         * Changes list.
         */
        final HtmlElement changesList;

        public HtmlObjects(HtmlElement hideElement, HtmlElement showElement, HtmlElement changeList) {
            this.hideElement = hideElement;
            this.showElement = showElement;
            this.changesList = changeList;
        }

        public void assertShowVisible() {
            Assert.assertTrue("Show buttonshould be visible", showElement.isDisplayed());
            Assert.assertFalse("Hide button should not be visible", hideElement.isDisplayed());
        }

        public void assertHideVisible() {
            Assert.assertTrue("Hide button should be visible", hideElement.isDisplayed());
            Assert.assertFalse("Show button should not be visible", showElement.isDisplayed());
        }

        public void expectChanges(int changesCount) {
            Assert.assertTrue("Changes count should be " + changesCount, changesList
                    .getElementsByTagName(TAG_ITEM).size() == changesCount);
        }

        public void expectVisibleChanges(int changesCount) {
            final Collection<HtmlElement> items = changesList.getElementsByTagName(TAG_ITEM);
            int visibleChanges = 0;
            for (HtmlElement item : items) {
                if (item.isDisplayed()) {
                    visibleChanges++;
                }
            }
            Assert.assertTrue("Visible changes count should be " + changesCount,
                    changesCount == visibleChanges);
        }

        public void assertButtonsExist(boolean exist) {
            Assert.assertTrue((hideElement != null) == exist);
            Assert.assertTrue((showElement != null) == exist);
        }

    }

    /**
     * Interface to tests, that should be performed on different pages.
     */
    private interface PageTester {
        /**
         * Method performs tests on the specified page.
         * @param htmlObjects anchors and list object
         * @throws Exception on exceptions occur
         */
        void testPage(HtmlObjects htmlObjects) throws Exception;
    }

}
