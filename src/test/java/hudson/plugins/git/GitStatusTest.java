package hudson.plugins.git;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.View;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.triggers.SCMTrigger;
import hudson.util.RunList;
import java.io.File;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.*;
import org.eclipse.jgit.transport.URIish;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.HttpResponses;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import jakarta.servlet.http.HttpServletRequest;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

public class GitStatusTest extends AbstractGitProject {

    private GitStatus gitStatus;
    private HttpServletRequest requestWithNoParameter;
    private HttpServletRequest requestWithParameter;
    private String repoURL;
    private String branch;
    private String sha1;
    private String notifyCommitApiToken;

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
        if (r.jenkins != null) {
            this.notifyCommitApiToken = ApiTokenPropertyConfiguration.get().generateApiToken("test").getString("value");
        }
    }

    @After
    public void resetAllowNotifyCommitParameters() throws Exception {
        GitStatus.setAllowNotifyCommitParameters(false);
        GitStatus.setSafeParametersForTest(null);
    }

    @After
    public void waitForAllJobsToComplete() throws Exception {
        // Put JenkinsRule into shutdown state, trying to reduce cleanup exceptions
        r.jenkins.doQuietDown();
        // JenkinsRule cleanup throws exceptions during tearDown.
        // Reduce exceptions by a random delay from 0.5 to 0.9 seconds.
        // Adding roughly 0.7 seconds to these JenkinsRule tests is a small price
        // for fewer exceptions and for better Windows job cleanup.
        java.util.Random random = new java.util.Random();
        Thread.sleep(500L + random.nextInt(400));
        /* Windows job cleanup fails to delete build logs in some of these tests.
         * Wait for the jobs to complete before exiting the test so that the
         * build logs will not be active when the cleanup process tries to
         * delete them.
         */
        View allView = r.jenkins.getView("All");
        if (allView == null) {
            fail("All view was not found when it should always be available");
            return;
        }
        RunList<Run> runList = allView.getBuilds();
        if (runList == null) {
            Logger.getLogger(GitStatusTest.class.getName()).log(Level.INFO, "No waiting, no entries in the runList");
            return;
        }
        runList.forEach((Run run) -> {
            try {
                Logger.getLogger(GitStatusTest.class.getName()).log(Level.INFO, "Waiting for {0}", run);
                r.waitForCompletion(run);
            } catch (InterruptedException ex) {
                Logger.getLogger(GitStatusTest.class.getName()).log(Level.SEVERE, "Interrupted waiting for GitStatusTest job", ex);
            }
        });
    }

    @Test
    public void testDoNotifyCommitWithNoBranches() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", false);
        SCMTrigger aTopicTrigger = setupProjectWithTrigger("a", "topic", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "", null, notifyCommitApiToken);
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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "nonexistent", "", null, notifyCommitApiToken);
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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null, notifyCommitApiToken);
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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master,topic,feature/def", null, notifyCommitApiToken);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        Mockito.verify(aFeatureTrigger).run();

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

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "nonexistent", null, notifyCommitApiToken);
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

        this.gitStatus.doNotifyCommit(requestWithParameter, "a", "name/with/slashes", null, notifyCommitApiToken);
        Mockito.verify(aSlashesTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();

        assertEquals("URL: a Branches: name/with/slashes", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithParametrizedBranch() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "$BRANCH_TO_BUILD", false);
        SCMTrigger bMasterTrigger = setupProjectWithTrigger("b", "master", false);
        SCMTrigger bTopicTrigger = setupProjectWithTrigger("b", "topic", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null, notifyCommitApiToken);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();

        assertEquals("URL: a Branches: master", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithIgnoredRepository() throws Exception {
        SCMTrigger aMasterTrigger = setupProjectWithTrigger("a", "master", true);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", null, "", notifyCommitApiToken);
        Mockito.verify(aMasterTrigger, Mockito.never()).run();

        assertEquals("URL: a SHA1: ", this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitWithNoScmTrigger() throws Exception {
        setupProject("a", "master", null);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", null, "", notifyCommitApiToken);
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

        this.gitStatus.doNotifyCommit(requestWithParameter, "a", "master,topic", null, notifyCommitApiToken);
        Mockito.verify(aMasterTrigger).run();
        Mockito.verify(aTopicTrigger).run();
        Mockito.verify(bMasterTrigger, Mockito.never()).run();
        Mockito.verify(bTopicTrigger, Mockito.never()).run();

        String expected = "URL: a Branches: master,topic"
                + (allowedParamKey1 ? " Parameters: paramKey1='paramValue1'" : "")
                + (allowedParamKey1 ? " More parameters: paramKey1='paramValue1'" : "");
        assertEquals(expected, this.gitStatus.toString());
    }

    @Test
    public void testDoNotifyCommitBranchWithRegex() throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", ":[^/]*/awesome-feature", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "feature/awesome-feature", null, notifyCommitApiToken);

        Mockito.verify(trigger).run();
    }

    @Test
    public void testDoNotifyCommitBranchWithWildcard() throws Exception {
        SCMTrigger trigger = setupProjectWithTrigger("remote", "origin/feature/*", false);
        this.gitStatus.doNotifyCommit(requestWithNoParameter, "remote", "feature/awesome-feature", null, notifyCommitApiToken);

        Mockito.verify(trigger).run();
    }

    private void assertAdditionalParameters(Collection<? extends Action> actions) {
        for (Action action: actions) {
            if (action instanceof ParametersAction parametersAction) {
                final List<ParameterValue> parameters = parametersAction.getParameters();
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
        FreeStyleProject project = r.createFreeStyleProject();
        GitSCM git = new GitSCM(
                Collections.singletonList(new UserRemoteConfig(url, null, null, null)),
                Collections.singletonList(new BranchSpec(branchString)),
                null, null,
                Collections.emptyList());
        project.setScm(git);
        if (trigger != null) project.addTrigger(trigger);
    }

    private FreeStyleProject setupNotifyProject() throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        project.setQuietPeriod(0);
        GitSCM git = new GitSCM(
                Collections.singletonList(new UserRemoteConfig(repoURL, null, null, null)),
                Collections.singletonList(new BranchSpec(branch)),
                null, null,
                Collections.emptyList());
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
        this.gitStatus.doNotifyCommit(requestWithNoParameter, repoURL, branch, sha1, notifyCommitApiToken);
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
        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1, notifyCommitApiToken);

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
        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1, notifyCommitApiToken);
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
        if (!runUnreliableTests()) {
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
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

        FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();

        r.waitForMessage("aaa aaaccc ccc", build);

        String extraValue = "An-extra-value";
        when(requestWithParameter.getParameterMap()).thenReturn(setupParameterMap(extraValue));
        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1, notifyCommitApiToken);

        String expected = "URL: " + repoURL
                + " SHA1: " + sha1
                + " Branches: " + branch
                + (allowedExtra ? " Parameters: extra='" + extraValue + "'" : "")
                + " More parameters: "
                + (allowedExtra ? "extra='" + extraValue + "'," : "")
                + "A='aaa',C='ccc',B='$A$C'";
        assertEquals(expected, this.gitStatus.toString());
    }

    /** Returns true if unreliable tests should be run */
    private boolean runUnreliableTests() {
        if (!isWindows()) {
            return true; // Always run tests on non-Windows platforms
        }
        String jobUrl = System.getenv("JOB_URL");
        if (jobUrl == null) {
            return true; // Always run tests when not inside a CI environment
        }
        return !jobUrl.contains("ci.jenkins.io"); // Skip some tests on ci.jenkins.io, windows cleanup is unreliable on those machines
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

        HttpResponse rsp = this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null, notifyCommitApiToken);

        // Up to 10 "Triggered" headers + 1 extra warning are returned.
        StaplerRequest2 sReq = mock(StaplerRequest2.class);
        StaplerResponse2 sRsp = mock(StaplerResponse2.class);
        Mockito.when(sRsp.getWriter()).thenReturn(mock(PrintWriter.class));
        rsp.generateResponse(sReq, sRsp, null);
        Mockito.verify(sRsp, Mockito.times(11)).addHeader(Mockito.eq("Triggered"), Mockito.anyString());

        // All triggers run.
        for (SCMTrigger projectTrigger : projectTriggers) {
            Mockito.verify(projectTrigger).run();
        }

        assertEquals("URL: a Branches: master", this.gitStatus.toString());
    }

    @Test
    @Issue("SECURITY-2499")
    public void testDoNotifyCommitWithWrongSha1Content() throws Exception {
        setupProjectWithTrigger("a", "master", false);

        String content = "<img src=onerror=alert(1)>";

        HttpResponse rsp = this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", content, notifyCommitApiToken);

        HttpResponses.HttpResponseException responseException = ((HttpResponses.HttpResponseException) rsp);
        assertEquals(IllegalArgumentException.class, responseException.getCause().getClass());
        assertEquals("Illegal SHA1", responseException.getCause().getMessage());

    }

    @Test
    @Issue("SECURITY-284")
    public void testDoNotifyCommitWithValidSha1AndValidApiToken() throws Exception {
        // when sha1 is provided build is scheduled right away instead of repo polling, so we do not check for trigger
        FreeStyleProject project = setupNotifyProject();

        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1, notifyCommitApiToken);

        r.waitUntilNoActivity();
        FreeStyleBuild lastBuild = project.getLastBuild();

        assertNotNull(lastBuild);
        assertEquals(lastBuild.getNumber(), 1);
    }

    @Test
    @Issue("SECURITY-284")
    public void testDoNotifyCommitWithUnauthenticatedPollingAllowed() throws Exception {
        GitStatus.NOTIFY_COMMIT_ACCESS_CONTROL = "disabled-for-polling";
        SCMTrigger trigger = setupProjectWithTrigger("a", "master", false);

        this.gitStatus.doNotifyCommit(requestWithNoParameter, "a", "master", null, null);

        Mockito.verify(trigger).run();
    }

    @Test
    @Issue("SECURITY-284")
    public void testDoNotifyCommitWithAllowModeSha1() throws Exception {
        GitStatus.NOTIFY_COMMIT_ACCESS_CONTROL = "disabled";
        // when sha1 is provided build is scheduled right away instead of repo polling, so we do not check for trigger
        FreeStyleProject project = setupNotifyProject();

        this.gitStatus.doNotifyCommit(requestWithParameter, repoURL, branch, sha1, null);

        r.waitUntilNoActivity();
        FreeStyleBuild lastBuild = project.getLastBuild();

        assertNotNull(lastBuild);
        assertEquals(lastBuild.getNumber(), 1);
    }
}
