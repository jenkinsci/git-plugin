package hudson.plugins.git;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.triggers.SCMTrigger;
import java.io.File;
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
    private String repoURL;
    private String branch;
    private String sha1;

    @Before
    public void setUp() throws Exception {
        this.gitStatus = new GitStatus();
        this.requestWithNoParameter = mock(HttpServletRequest.class);
        this.requestWithParameter = mock(HttpServletRequest.class);
        this.repoURL = new File(".").getAbsolutePath();
        this.branch = "**";
        this.sha1 = "7bb68ef21dc90bd4f7b08eca876203b2e049198d";
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
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    @Test
    public void testDoNotifyCommitWithNoMatchingUrl() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "nonexistent", "", null);
        Mockito.verify(aMasterTrigger, Mockito.never()).run();
        Mockito.verify(aTopicTrigger, Mockito.never()).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    @Test
    public void testDoNotifyCommitWithOneBranch() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger, Mockito.never()).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    @Test
    public void testDoNotifyCommitWithTwoBranches() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master,topic", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    @Test
    public void testDoNotifyCommitWithNoMatchingBranches() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "nonexistent", null);
        Mockito.verify(aMasterTrigger, Mockito.never()).run();
        Mockito.verify(aTopicTrigger, Mockito.never()).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    @Test
    public void testDoNotifyCommitWithParametrizedBranch() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "$BRANCH_TO_BUILD", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
    }

    @Test
    public void testDoNotifyCommitWithIgnoredRepository() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", true);

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
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

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
    public void testDoNotifyCommitBranchWithSlash(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", branchSpecPrefix + "feature/awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "feature/awesome-feature", null);

        Mockito.verify(trigger).run();
    }

    @Theory
    public void testDoNotifyCommitBranchWithoutSlash(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", branchSpecPrefix + "awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "awesome-feature", null);

        Mockito.verify(trigger).run();
    }

    @Theory
    public void testDoNotifyCommitBranchByBranchRef(@FromDataPoints("branchSpecPrefixes") String branchSpecPrefix) throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", branchSpecPrefix + "awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "refs/heads/awesome-feature", null);

        Mockito.verify(trigger).run();
    }

    @Test
    public void testDoNotifyCommitBranchWithRegex() throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", ":[^/]*/awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "feature/awesome-feature", null);

        Mockito.verify(trigger).run();
    }

    @Test
    public void testDoNotifyCommitBranchWithWildcard() throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", "origin/feature/*", false);
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

    private SCMTrigger setupProjectWithTrigger(String url, String branchString, boolean ignoreNotifyCommit) throws Exception {
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
    public void testLooselyMatches() throws URISyntaxException {
        String[] equivalentRepoURLs = new String[]{
            "https://github.com/jenkinsci/git-plugin",
            "https://github.com/jenkinsci/git-plugin/",
            "https://github.com/jenkinsci/git-plugin.git",
            "https://github.com/jenkinsci/git-plugin.git/",
            "https://someone@github.com/jenkinsci/git-plugin.git",
            "https://someone:somepassword@github.com/jenkinsci/git-plugin/",
            "git://github.com/jenkinsci/git-plugin",
            "git://github.com/jenkinsci/git-plugin/",
            "git://github.com/jenkinsci/git-plugin.git",
            "git://github.com/jenkinsci/git-plugin.git/",
            "ssh://git@github.com/jenkinsci/git-plugin",
            "ssh://github.com/jenkinsci/git-plugin.git",
            "git@github.com:jenkinsci/git-plugin/",
            "git@github.com:jenkinsci/git-plugin.git",
            "git@github.com:jenkinsci/git-plugin.git/"
        };
        List<URIish> uris = new ArrayList<URIish>();
        for (String testURL : equivalentRepoURLs) {
            uris.add(new URIish(testURL));
        }

        /* Extra slashes on end of URL probably should be considered equivalent,
         * but current implementation does not consider them as loose matches
         */
        URIish badURLTrailingSlashes = new URIish(equivalentRepoURLs[0] + "///");
        /* Different hostname should always fail match check */
        URIish badURLHostname = new URIish(equivalentRepoURLs[0].replace("github.com", "bitbucket.org"));

        for (URIish lhs : uris) {
            assertFalse(lhs + " matches trailing slashes " + badURLTrailingSlashes, GitStatus.looselyMatches(lhs, badURLTrailingSlashes));
            assertFalse(lhs + " matches bad hostname " + badURLHostname, GitStatus.looselyMatches(lhs, badURLHostname));
            for (URIish rhs : uris) {
                assertTrue(lhs + " and " + rhs + " didn't match", GitStatus.looselyMatches(lhs, rhs));
            }
        }
    }

    private FreeStyleProject setupNotifyProject() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.setQuietPeriod(0);
        GitSCM git = new GitSCM(
                Collections.singletonList(new UserRemoteConfig(repoURL, null, null, null)),
                Collections.singletonList(new BranchSpec(branch)),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(git);
        project.addTrigger(new SCMTrigger("")); // Required for GitStatus to see polling request
        return project;
    }

    private Map<String, String[]> setupParameterMap() {
        Map<String, String[]> parameterMap = new HashMap<String, String[]>();
        String[] repoURLs = {repoURL};
        parameterMap.put("url", repoURLs);
        String[] branches = {branch};
        parameterMap.put("branches", branches);
        String[] hashes = {sha1};
        parameterMap.put("sha1", hashes);
        return parameterMap;
    }

    private Map<String, String[]> setupParameterMap(String extraValue) {
        Map<String, String[]> parameterMap = setupParameterMap();
        String[] extra = {extraValue};
        parameterMap.put("extra", extra);
        return parameterMap;
    }

    @Test
    public void testDoNotifyCommitNoParameters() throws Exception {
        setupNotifyProject();
        this.gitStatus.doNotifyCommit(requestWithNoParameter, repoURL, branch, sha1);
        assertEquals("URL: " + repoURL
                + " SHA1: " + sha1
                + " Branches: " + branch, this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithExtraParameter() throws Exception {
        setupNotifyProject();
        String extraValue = "An-extra-value";
        when(requestWithParameter.getParameterMap()).thenReturn(setupParameterMap(extraValue));
        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1);
        assertEquals("URL: " + repoURL
                + " SHA1: " + sha1
                + " Branches: " + branch
                + " Parameters: extra='" + extraValue + "'"
                + " More parameters: extra='" + extraValue + "'", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithNullValueExtraParameter() throws Exception {
        setupNotifyProject();
        when(requestWithParameter.getParameterMap()).thenReturn(setupParameterMap(null));
        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1);
        assertEquals("URL: " + repoURL
                + " SHA1: " + sha1
                + " Branches: " + branch, this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithDefaultParameter() throws Exception {
        // Use official repo for this single test
        this.repoURL = "https://github.com/jenkinsci/git-plugin.git";
        FreeStyleProject project = setupNotifyProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("A", "aaa"),
                new StringParameterDefinition("C", "ccc"),
                new StringParameterDefinition("B", "$A$C")
        ));
        final CommandInterpreter script = isWindows()
                ? new BatchFile("echo %A% %B% %C%")
                : new Shell("echo $A $B $C");
        project.getBuildersList().add(script);

        FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();

        jenkins.assertLogContains("aaa aaaccc ccc", build);

        String extraValue = "An-extra-value";
        when(requestWithParameter.getParameterMap()).thenReturn(setupParameterMap(extraValue));
        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1);
        assertEquals("URL: " + repoURL
                + " SHA1: " + sha1
                + " Branches: " + branch
                + " Parameters: extra='" + extraValue + "'"
                + " More parameters: extra='" + extraValue + "',A='aaa',C='ccc',B='$A$C'", this.gitStatus.toString());
    }

    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote classloader issue
     */
    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
