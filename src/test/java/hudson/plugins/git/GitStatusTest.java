package hudson.plugins.git;

import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.triggers.SCMTrigger;

import java.net.URISyntaxException;
import java.util.*;
import org.eclipse.jgit.transport.URIish;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.WithoutJenkins;

import javax.servlet.http.HttpServletRequest;

@RunWith(Theories.class)
public class GitStatusTest extends AbstractGitProject {

    private GitStatus gitStatus;
    private HttpServletRequest requestWithNoParameter;
    private HttpServletRequest requestWithParameter;

    @Before
    public void setUp() throws Exception {
        this.gitStatus = new GitStatus();
        this.requestWithNoParameter = mock(HttpServletRequest.class);
        this.requestWithParameter = mock(HttpServletRequest.class);
    }

    @WithoutJenkins
    @Test
    public void testGetDisplayName() {
        assertEquals("Git", this.gitStatus.getDisplayName());
    }

    @WithoutJenkins
    @Test
    public void testGetSearchUrl() {
        assertEquals("git", this.gitStatus.getSearchUrl());
    }

    @WithoutJenkins
    @Test
    public void testGetIconFileName() {
        assertNull(this.gitStatus.getIconFileName());
    }

    @WithoutJenkins
    @Test
    public void testGetUrlName() {
        assertEquals("git", this.gitStatus.getUrlName());
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testDoNotifyCommitWithParametrizedBranch() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "$BRANCH_TO_BUILD", false);
        SCMTrigger bMasterTrigger = setupProject("b", "master", false);
        SCMTrigger bTopicTrigger = setupProject("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    @Test
    public void testDoNotifyCommitWithIgnoredRepository() throws Exception {
        SCMTrigger aMasterTrigger = setupProject("a", "master", true);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", null, "");
        Mockito.verify(aMasterTrigger, Mockito.never()).run();
    }

    @Test
    public void testDoNotifyCommitWithNoScmTrigger() throws Exception {
        setupProject("a", "master", null);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", null, "");
        // no expectation here, however we shouldn't have a build triggered, and no exception
    }

    @Test
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

    @DataPoints("branchSpecPrefixes")
    public static String[] branchSpecPrefixes = new String[] {
            "",
            "refs/remotes/",
            "refs/heads/",
            "origin/",
            "remotes/origin/"
    };

    @Theory
    public void testDoNotifyBranchWithSlash(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProject("remote", branchSpecPrefix + "feature/awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "feature/awesome-feature", null);

        Mockito.verify(trigger).run();
    }

    @Theory
    public void testDoNotifyBranchWithoutSlash(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProject("remote", branchSpecPrefix + "awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "awesome-feature", null);

        Mockito.verify(trigger).run();
    }

    @Theory
    public void testDoNotifyBranchByBranchRef(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProject("remote", branchSpecPrefix + "awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "refs/heads/awesome-feature", null);

        Mockito.verify(trigger).run();
    }

    @Test
    public void testDoNotifyBranchWithRegex() throws Exception {
        SCMTrigger trigger = setupProject("remote", ":[^/]*/awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "feature/awesome-feature", null);

        Mockito.verify(trigger).run();
    }

    @Test
    public void testDoNotifyCommitBranchWithWildcard() throws Exception {
        SCMTrigger trigger = setupProject("remote", "origin/feature/*", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "feature/awesome-feature", null);

        Mockito.verify(trigger).run();
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
        FreeStyleProject project = jenkins.createFreeStyleProject();
        GitSCM git = new GitSCM(
                Collections.singletonList(new UserRemoteConfig(url, null, null, null)),
                Collections.singletonList(new BranchSpec(branchString)),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(git);
        if (trigger != null) project.addTrigger(trigger);
    }

    @WithoutJenkins
    @Test
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
                assertTrue(lhs + " and " + rhs + " didn't match", GitStatus.looselyMatches(lhs, rhs));
            }
        }
    }
}
