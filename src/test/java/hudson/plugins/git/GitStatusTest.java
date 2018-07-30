package hudson.plugins.git;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.triggers.SCMTrigger;
import java.io.File;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.*;

import org.eclipse.jgit.transport.URIish;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.WithoutJenkins;

import javax.servlet.http.HttpServletRequest;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class GitStatusTest extends AbstractGitProject {

    private GitStatus gitStatus;
    private HttpServletRequest requestWithNoParameter;
    private HttpServletRequest requestWithParameter;
    private String repoURL;
    private String branch;
    private String sha1;

    @Before
    public void setUp() throws Exception {
        GitStatus.setAllowNotifyCommitParameters(false);
        GitStatus.setSafeParametersForTest(null);
        this.gitStatus = new GitStatus();
        this.requestWithNoParameter = mock(HttpServletRequest.class);
        this.requestWithParameter = mock(HttpServletRequest.class);
        this.repoURL = new File(".").getAbsolutePath();
        this.branch = "**";
        this.sha1 = "7bb68ef21dc90bd4f7b08eca876203b2e049198d";
    }

    @After
    public void resetAllowNotifyCommitParameters() throws Exception {
        GitStatus.setAllowNotifyCommitParameters(false);
        GitStatus.setSafeParametersForTest(null);
    }

    @WithoutJenkins
    @Test
    public void testGetDisplayName() {
        assertEquals("Git", this.gitStatus.getDisplayName());
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

    @WithoutJenkins
    @Test
    public void testToString() {
        assertEquals("URL: ", this.gitStatus.toString());
    }

    @WithoutJenkins
    @Test
    public void testAllowNotifyCommitParametersDisabled() {
        assertEquals("SECURITY-275: ignore arbitrary notifyCommit parameters", false, GitStatus.ALLOW_NOTIFY_COMMIT_PARAMETERS);
    }

    @WithoutJenkins
    @Test
    public void testSafeParametersEmpty() {
        assertEquals("SECURITY-275: Safe notifyCommit parameters", "", GitStatus.SAFE_PARAMETERS);
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

        assertEquals("URL: a Branches: ", this.gitStatus.toString());
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

        assertEquals("URL: nonexistent Branches: ", this.gitStatus.toString());
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

        assertEquals("URL: a Branches: master", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithTwoBranches() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger aFeatureTrigger = setupProjectWithTrigger("a", "feature/def", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);
        SCMTrigger bFeatureTrigger = setupProjectWithTrigger("b", "feature/def", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master,topic,feature/def", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        // trigger containing slash is not called in current code, should be
        // JENKINS-29603 may be related
        Mockito.verify(aFeatureTrigger, Mockito.never()).run();

        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();
        Mockito.verify(bFeatureTrigger, Mockito.never()).run();

        assertEquals("URL: a Branches: master,topic,feature/def", this.gitStatus.toString());
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

        assertEquals("URL: a Branches: nonexistent", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithSlashesInBranchNames() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);

        SCMTrigger aSlashesTrigger = setupProjectWithTrigger("a", "name/with/slashes", false);

        this.gitStatus.doNotifyCommit(requestWithParameter, "a", "name/with/slashes", null);
        Mockito.verify(aSlashesTrigger, Mockito.never()).run(); // Should be run
        Mockito.verify(bMasterTrigger, Mockito.never()).run();

        assertEquals("URL: a Branches: name/with/slashes", this.gitStatus.toString());
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

        assertEquals("URL: a Branches: master", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithIgnoredRepository() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", true);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", null, "");
        Mockito.verify(aMasterTrigger, Mockito.never()).run();

        assertEquals("URL: a SHA1: ", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithNoScmTrigger() throws Exception {
        setupProject("a", "master", null);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", null, "");
        // no expectation here, however we shouldn't have a build triggered, and no exception

        assertEquals("URL: a SHA1: ", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithTwoBranchesAndAdditionalParameterAllowed() throws Exception {
        doNotifyCommitWithTwoBranchesAndAdditionalParameter(true, null);
    }

    @Test
    public void testDoNotifyCommitWithTwoBranchesAndAdditionalParameter() throws Exception {
        doNotifyCommitWithTwoBranchesAndAdditionalParameter(false, null);
    }

    @Test
    public void testDoNotifyCommitWithTwoBranchesAndAdditionalSafeParameter() throws Exception {
        doNotifyCommitWithTwoBranchesAndAdditionalParameter(false, "paramKey1");
    }

    @Test
    public void testDoNotifyCommitWithTwoBranchesAndAdditionalUnsafeParameter() throws Exception {
        doNotifyCommitWithTwoBranchesAndAdditionalParameter(false, "does,not,include,param");
    }

    private void doNotifyCommitWithTwoBranchesAndAdditionalParameter(final boolean allowed, String safeParameters) throws Exception {
        if (allowed) {
            GitStatus.setAllowNotifyCommitParameters(true);
        }

        boolean allowedParamKey1 = allowed;
        if (safeParameters != null) {
            GitStatus.setSafeParametersForTest(safeParameters);
            if (safeParameters.contains("paramKey1")) {
                allowedParamKey1 = true;
            }
        }

        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("paramKey1", new String[] {"paramValue1"});
        when(requestWithParameter.getParameterMap()).thenReturn(parameterMap);

        this.gitStatus.doNotifyCommit(requestWithParameter, "a", "master,topic", null);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();

        String expected = "URL: a Branches: master,topic"
                + (allowedParamKey1 ? " Parameters: paramKey1='paramValue1'" : "")
                + (allowedParamKey1 ? " More parameters: paramKey1='paramValue1'" : "");
        assertEquals(expected, this.gitStatus.toString());
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
        List<URIish> uris = new ArrayList<>();
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
        Map<String, String[]> parameterMap = new HashMap<>();
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
    public void testDoNotifyCommit() throws Exception { /* No parameters */
        setupNotifyProject();
        this.gitStatus.doNotifyCommit(requestWithNoParameter, repoURL, branch, sha1);
        assertEquals("URL: " + repoURL
                + " SHA1: " + sha1
                + " Branches: " + branch, this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithExtraParameterAllowed() throws Exception {
        doNotifyCommitWithExtraParameterAllowed(true, null);
    }

    @Test
    public void testDoNotifyCommitWithExtraParameter() throws Exception {
        doNotifyCommitWithExtraParameterAllowed(false, null);
    }

    @Test
    public void testDoNotifyCommitWithExtraSafeParameter() throws Exception {
        doNotifyCommitWithExtraParameterAllowed(false, "something,extra,is,here");
    }

    @Test
    public void testDoNotifyCommitWithExtraUnsafeParameter() throws Exception {
        doNotifyCommitWithExtraParameterAllowed(false, "something,is,not,here");
    }

    private void doNotifyCommitWithExtraParameterAllowed(final boolean allowed, String safeParameters) throws Exception {
        if (allowed) {
            GitStatus.setAllowNotifyCommitParameters(true);
        }

        boolean allowedExtra = allowed;
        if (safeParameters != null) {
            GitStatus.setSafeParametersForTest(safeParameters);
            if (safeParameters.contains("extra")) {
                allowedExtra = true;
            }
        }
        setupNotifyProject();
        String extraValue = "An-extra-value";
        when(requestWithParameter.getParameterMap()).thenReturn(setupParameterMap(extraValue));
        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1);

        String expected = "URL: " + repoURL
                + " SHA1: " + sha1
                + " Branches: " + branch
                + (allowedExtra ? " Parameters: extra='" + extraValue + "'" : "")
                + (allowedExtra ? " More parameters: extra='" + extraValue + "'" : "");
        assertEquals(expected, this.gitStatus.toString());
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
    public void testDoNotifyCommitWithDefaultParameterAllowed() throws Exception {
        doNotifyCommitWithDefaultParameter(true, null);
    }

    @Test
    public void testDoNotifyCommitWithDefaultParameter() throws Exception {
        doNotifyCommitWithDefaultParameter(false, null);
    }

    @Test
    public void testDoNotifyCommitWithDefaultSafeParameter() throws Exception {
        doNotifyCommitWithDefaultParameter(false, "A,B,C,extra");
    }

    @Test
    public void testDoNotifyCommitWithDefaultUnsafeParameterC() throws Exception {
        doNotifyCommitWithDefaultParameter(false, "A,B,extra");
    }

    @Test
    public void testDoNotifyCommitWithDefaultUnsafeParameterExtra() throws Exception {
        doNotifyCommitWithDefaultParameter(false, "A,B,C");
    }

    private void doNotifyCommitWithDefaultParameter(final boolean allowed, String safeParameters) throws Exception {
        if (allowed) {
            GitStatus.setAllowNotifyCommitParameters(true);
        }

        boolean allowedExtra = allowed;
        if (safeParameters != null) {
            GitStatus.setSafeParametersForTest(safeParameters);
            if (safeParameters.contains("extra")) {
                allowedExtra = true;
            }
        }

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

        String expected = "URL: " + repoURL
                + " SHA1: " + sha1
                + " Branches: " + branch
                + (allowedExtra ? " Parameters: extra='" + extraValue + "'" : "")
                + " More parameters: "
                + (allowedExtra ? "extra='" + extraValue + "'," : "")
                + "A='aaa',C='ccc',B='$A$C'";
        assertEquals(expected, this.gitStatus.toString());
    }

    /**
     * inline ${@link hudson.Functions#isWindows()} to prevent a transient
     * remote classloader issue
     */
    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }

    @Test
    @Issue("JENKINS-46929")
    public void testDoNotifyCommitTriggeredHeadersLimited() throws Exception {
        SCMTrigger[] projectTriggers = new SCMTrigger[50];
        for (int i = 0; i < projectTriggers.length; i++) {
            projectTriggers[i] = setupProjectWithTrigger("a", "master", false);
        }

        HttpResponse rsp = this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null);

        // Up to 10 "Triggered" headers + 1 extra warning are returned.
        StaplerRequest sReq = mock(StaplerRequest.class);
        StaplerResponse sRsp = mock(StaplerResponse.class);
        Mockito.when(sRsp.getWriter()).thenReturn(mock(PrintWriter.class));
        rsp.generateResponse(sReq, sRsp, null);
        Mockito.verify(sRsp, Mockito.times(11)).addHeader(Mockito.eq("Triggered"), Mockito.anyString());

        // All triggers run.
        for (SCMTrigger projectTrigger : projectTriggers) {
            Mockito.verify(projectTrigger).run();
        }

        assertEquals("URL: a Branches: master", this.gitStatus.toString());
    }
}
