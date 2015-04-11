/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.git;

import hudson.Functions;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.triggers.SCMTrigger;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import org.eclipse.jgit.transport.URIish;
import org.jvnet.hudson.test.HudsonTestCase;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

public class GitStatusTest extends HudsonTestCase {
    private GitStatus gitStatus;
    private HttpServletRequest requestWithNoParameter;
    private HttpServletRequest requestWithParameter;

    public GitStatusTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.gitStatus = new GitStatus();
        this.requestWithNoParameter = mock(HttpServletRequest.class);
        this.requestWithParameter = mock(HttpServletRequest.class);
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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "", null);
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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "nonexistent", "", null);
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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null);
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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master,topic", null);
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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "nonexistent", null);
        Mockito.verify(aMasterTrigger, Mockito.never()).run();
        Mockito.verify(aTopicTrigger, Mockito.never()).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    public void testDoNotifyCommitWithIgnoredRepository() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", true);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", null, "");
        Mockito.verify(aMasterTrigger, Mockito.never()).run();
    }


    public void testDoNotifyCommitWithNoScmTrigger() throws Exception {
        setupProject("a", "master", null);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", null, "");
        // no expectation here, however we shouldn't have a build triggered, and no exception
    }

    public void testDoNotifyCommitWithTwoBranchesAndAdditionalParameter() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", false);
        SCMTrigger aTopicTrigger = setupProject("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProject("b", "master", false);
        SCMTrigger bTopicTrigger = setupProject("b", "topic", false);

        Map<String, String[]> parameterMap = new HashMap<String, String[]>();
        parameterMap.put("paramKey1", new String[] {"paramValue1"});
        parameterMap.put("paramKey2", new String[] {"paramValue2"});
        when(requestWithParameter.getParameterMap()).thenReturn(parameterMap);

        this.gitStatus.doNotifyCommit(requestWithParameter, "a", "master,topic", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();

        assertAdditionalParameters(aMasterTrigger.getProjectActions());
        assertAdditionalParameters(aTopicTrigger.getProjectActions());

    }

    private void assertAdditionalParameters(Collection<? extends Action> actions) {
        for (Action action: actions) {
            if (action instanceof ParametersAction) {
                final List<ParameterValue> parameters = ((ParametersAction) action).getParameters();
                assertEquals(2, parameters.size());
                for (ParameterValue value : parameters) {
                    assertTrue((value.getName().equals("paramKey1") && value.getValue().equals("paramValue1"))
                            || (value.getName().equals("paramKey2") && value.getValue().equals("paramValue2")));
                }
            }
        }
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
