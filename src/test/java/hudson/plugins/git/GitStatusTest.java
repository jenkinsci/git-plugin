/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.git;

import hudson.Functions;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.triggers.SCMTrigger;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.transport.URIish;
import org.jvnet.hudson.test.HudsonTestCase;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class GitStatusTest extends HudsonTestCase {
    private GitStatus gitStatus;

    public GitStatusTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.gitStatus = new GitStatus();
    }

    @Override
    protected void tearDown() throws Exception {
        try { //Avoid test failures due to failed cleanup tasks
            super.tearDown();
        } catch (Exception e) {
            if (e instanceof IOException && Functions.isWindows()) {
                return;
            }
            e.printStackTrace();
        }
    }

    public void testGetDisplayName() {
        assertEquals("Git", this.gitStatus.getDisplayName());
    }

    public void testGetSearchUrl() {
        assertEquals("git", this.gitStatus.getSearchUrl());
    }

    public void testGetIconFileName() {
        assertNull(this.gitStatus.getIconFileName());
    }

    public void testGetUrlName() {
        assertEquals("git", this.gitStatus.getUrlName());
    }

    public void testDoNotifyCommitWithNoBranches() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", false);
        SCMTrigger aTopicTrigger = setupProject("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProject("b", "master", false);
        SCMTrigger bTopicTrigger = setupProject("b", "topic", false);

        this.gitStatus.doNotifyCommit("a", "", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    public void testDoNotifyCommitWithNoMatchingUrl() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", false);
        SCMTrigger aTopicTrigger = setupProject("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProject("b", "master", false);
        SCMTrigger bTopicTrigger = setupProject("b", "topic", false);

        this.gitStatus.doNotifyCommit("nonexistent", "", null);
        Mockito.verify(aMasterTrigger, Mockito.never()).run();
        Mockito.verify(aTopicTrigger, Mockito.never()).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    public void testDoNotifyCommitWithOneBranch() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", false);
        SCMTrigger aTopicTrigger = setupProject("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProject("b", "master", false);
        SCMTrigger bTopicTrigger = setupProject("b", "topic", false);

        this.gitStatus.doNotifyCommit("a", "master", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger, Mockito.never()).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    public void testDoNotifyCommitWithTwoBranches() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", false);
        SCMTrigger aTopicTrigger = setupProject("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProject("b", "master", false);
        SCMTrigger bTopicTrigger = setupProject("b", "topic", false);

        this.gitStatus.doNotifyCommit("a", "master,topic", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    public void testDoNotifyCommitWithNoMatchingBranches() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", false);
        SCMTrigger aTopicTrigger = setupProject("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProject("b", "master", false);
        SCMTrigger bTopicTrigger = setupProject("b", "topic", false);

        this.gitStatus.doNotifyCommit("a", "nonexistent", null);
        Mockito.verify(aMasterTrigger, Mockito.never()).run();
        Mockito.verify(aTopicTrigger, Mockito.never()).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    public void testDoNotifyCommitWithParametrizedBranch() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "$BRANCH_TO_BUILD", false);
        SCMTrigger bMasterTrigger = setupProject("b", "master", false);
        SCMTrigger bTopicTrigger = setupProject("b", "topic", false);

        this.gitStatus.doNotifyCommit("a", "master", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    public void testDoNotifyCommitWithIgnoredRepository() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", true);

        this.gitStatus.doNotifyCommit("a", null, "");
        Mockito.verify(aMasterTrigger, Mockito.never()).run();
    }


    public void testDoNotifyCommitWithNoScmTrigger() throws Exception {
        setupProject("a", "master", null);
        this.gitStatus.doNotifyCommit("a", null, "");
        // no expectation here, however we shouldn't have a build triggered, and no exception
    }

    private SCMTrigger setupProject(String url, String branchString, boolean ignoreNotifyCommit) throws Exception {
        SCMTrigger trigger = Mockito.mock(SCMTrigger.class);
        Mockito.doReturn(ignoreNotifyCommit).when(trigger).isIgnorePostCommitHooks();
        setupProject(url, branchString, trigger);
        return trigger;
    }

    private void setupProject(String url, String branchString, SCMTrigger trigger) throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        GitSCM git = new GitSCM(
                Collections.singletonList(new UserRemoteConfig(url, null, null, null)),
                Collections.singletonList(new BranchSpec(branchString)),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(git);
        if (trigger != null) project.addTrigger(trigger);
    }


    public void testLooseMatch() throws URISyntaxException {
        String[] list = new String[]{
            "https://github.com/jenkinsci/git-plugin.git",
            "git://github.com/jenkinsci/git-plugin.git",
            "ssh://git@github.com/jenkinsci/git-plugin.git",
            "https://someone@github.com/jenkinsci/git-plugin.git",
            "git@github.com:jenkinsci/git-plugin.git"
        };
        List<URIish> uris = new ArrayList<URIish>();
        for (String s : list) {
            uris.add(new URIish(s));
        }

        for (URIish lhs : uris) {
            for (URIish rhs : uris) {
                assertTrue(lhs+" and "+rhs+" didn't match",new GitStatus().looselyMatches(lhs,rhs));
            }
        }
    }
}
