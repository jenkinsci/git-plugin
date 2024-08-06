package hudson.plugins.git.extensions.impl;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;

import java.io.IOException;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.UnsupportedCommand;

import org.junit.Before;
import org.junit.Test;

import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;

public class SubmoduleOptionTest {

    private SubmoduleOption submoduleOption;

    private static final boolean DISABLE_SUBMODULES_FALSE = false;
    private static final boolean RECURSIVE_SUBMODULES_FALSE = false;
    private static final boolean TRACKING_SUBMODULES_FALSE = false;
    private static final boolean USE_PARENT_CREDENTIALS_FALSE = false;
    private static final String SUBMODULES_REFERENCE_REPOSITORY = null;
    private static final Integer SUBMODULES_TIMEOUT = null;

    private SubmoduleOption newSubmoduleOption() {
        return new SubmoduleOption(DISABLE_SUBMODULES_FALSE,
                RECURSIVE_SUBMODULES_FALSE,
                TRACKING_SUBMODULES_FALSE,
                SUBMODULES_REFERENCE_REPOSITORY,
                SUBMODULES_TIMEOUT,
                USE_PARENT_CREDENTIALS_FALSE);
    }

    @Before
    public void setUp() throws Exception {
        submoduleOption = newSubmoduleOption();
    }

    @Issue("JENKINS-31934")
    @Test
    public void testSubmoduleUpdateThrowsIOException() throws Exception {
        // In order to verify that the submodule option correctly converts
        // GitExceptions into IOExceptions, setup a SubmoduleOption, and run
        // it's onCheckoutCompleted extension point with a mocked git client
        // that always throws an exception.
        BuildData buildData = Mockito.mock(BuildData.class);
        Build lastBuild = Mockito.mock(Build.class);
        GitSCM scm = Mockito.mock(GitSCM.class);
        Run<?, ?> build = Mockito.mock(Run.class);
        GitClient client = Mockito.mock(GitClient.class);
        TaskListener listener = Mockito.mock(TaskListener.class);
        buildData.lastBuild = lastBuild;
        Mockito.when(scm.getBuildData(build)).thenReturn(buildData);
        Mockito.when(client.hasGitModules()).thenReturn(true);
        Mockito.when(client.submoduleUpdate()).thenThrow(new GitException("a git exception"));

        Exception e = assertThrows(IOException.class, () -> submoduleOption.onCheckoutCompleted(scm, build, client, listener));
        assertThat(e.getMessage(), is("Could not perform submodule update"));
    }

    @Test
    public void testOnCheckoutCompleted() throws Exception {
        /* See testSubmoduleUpdateThrowsIOException */
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(SubmoduleOption.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }

    @Test
    public void testIsDisableSubmodules() {
        assertThat(submoduleOption.isDisableSubmodules(), is(false));
    }

    @Test
    public void testIsDisableSubmodulesTrue() {
        submoduleOption = new SubmoduleOption(true,
                RECURSIVE_SUBMODULES_FALSE,
                TRACKING_SUBMODULES_FALSE,
                SUBMODULES_REFERENCE_REPOSITORY,
                SUBMODULES_TIMEOUT,
                USE_PARENT_CREDENTIALS_FALSE);
        assertThat(submoduleOption.isDisableSubmodules(), is(true));
    }

    @Test
    public void testIsRecursiveSubmodules() {
        assertThat(submoduleOption.isRecursiveSubmodules(), is(false));
    }

    @Test
    public void testIsRecursiveSubmodulesTrue() {
        submoduleOption = new SubmoduleOption(DISABLE_SUBMODULES_FALSE,
                true,
                TRACKING_SUBMODULES_FALSE,
                SUBMODULES_REFERENCE_REPOSITORY,
                SUBMODULES_TIMEOUT,
                USE_PARENT_CREDENTIALS_FALSE);
        assertThat(submoduleOption.isRecursiveSubmodules(), is(true));
    }

    @Test
    public void testIsTrackingSubmodules() {
        assertThat(submoduleOption.isTrackingSubmodules(), is(false));
    }

    @Test
    public void testIsTrackingSubmodulesTrue() {
        submoduleOption = new SubmoduleOption(DISABLE_SUBMODULES_FALSE,
                RECURSIVE_SUBMODULES_FALSE,
                true,
                SUBMODULES_REFERENCE_REPOSITORY,
                SUBMODULES_TIMEOUT,
                USE_PARENT_CREDENTIALS_FALSE);
        assertThat(submoduleOption.isTrackingSubmodules(), is(true));
    }

    @Test
    public void testIsParentCredentials() {
        assertThat(submoduleOption.isParentCredentials(), is(false));
    }

    @Test
    public void testIsParentCredentialsTrue() {
        submoduleOption = new SubmoduleOption(DISABLE_SUBMODULES_FALSE,
                RECURSIVE_SUBMODULES_FALSE,
                TRACKING_SUBMODULES_FALSE,
                SUBMODULES_REFERENCE_REPOSITORY,
                SUBMODULES_TIMEOUT,
                true);
        assertThat(submoduleOption.isParentCredentials(), is(true));
    }

    @Test
    public void testGetReference() {
        assertThat(submoduleOption.getReference(), is(nullValue()));
    }

    @Test
    public void testGetReferenceNotNull() {
        final String referenceRepoDirName = "/repo.git";
        submoduleOption = new SubmoduleOption(DISABLE_SUBMODULES_FALSE,
                RECURSIVE_SUBMODULES_FALSE,
                TRACKING_SUBMODULES_FALSE,
                referenceRepoDirName,
                SUBMODULES_TIMEOUT,
                true);
        assertThat(submoduleOption.getReference(), is(referenceRepoDirName));
    }

    @Test
    public void testGetTimeout() {
        assertThat(submoduleOption.getTimeout(), is(nullValue()));
    }

    @Test
    public void testGetTimeoutNotNull() {
        Integer timeout = 3;
        submoduleOption = new SubmoduleOption(DISABLE_SUBMODULES_FALSE,
                RECURSIVE_SUBMODULES_FALSE,
                TRACKING_SUBMODULES_FALSE,
                SUBMODULES_REFERENCE_REPOSITORY,
                timeout,
                true);
        assertThat(submoduleOption.getTimeout(), is(timeout));
    }

    @Test
    public void testSetShallow() {
        submoduleOption.setShallow(true);
        assertThat(submoduleOption.getShallow(), is(true));
    }

    @Test
    public void testGetShallow() {
        assertThat(submoduleOption.getShallow(), is(false));
    }

    private Integer randomSmallNonNegativeIntegerOrNull() {
        java.util.Random random = new java.util.Random();
        Integer value = random.nextInt(131);
        if (value == 0) {
            value = null;
        }
        return value;
    }

    @Test
    public void testSetDepth() {
        Integer depthValue = randomSmallNonNegativeIntegerOrNull();
        submoduleOption.setDepth(depthValue);
        assertThat(submoduleOption.getDepth(), is(depthValue));
    }

    @Test
    public void testGetDepth() {
        assertThat(submoduleOption.getDepth(), is(nullValue()));
    }

    @Test
    public void testGetThreads() {
        assertThat(submoduleOption.getThreads(), is(nullValue()));
    }

    @Test
    public void testSetThreads() {
        Integer threads = randomSmallNonNegativeIntegerOrNull();
        submoduleOption.setThreads(threads);
        assertThat(submoduleOption.getThreads(), is(threads));
    }

    @Test
    public void testToString() {
        assertThat(submoduleOption.toString(), is("SubmoduleOption{"
                + "disableSubmodules=false"
                + ", recursiveSubmodules=false"
                + ", trackingSubmodules=false"
                + ", reference='null'"
                + ", parentCredentials=false"
                + ", timeout=null"
                + ", shallow=false"
                + ", depth=null"
                + ", threads=null"
                + '}'));
    }

    @Test
    public void testToStringDataBoundConstructor() {
        submoduleOption = new SubmoduleOption();
        assertThat(submoduleOption.toString(), is("SubmoduleOption{"
                + "disableSubmodules=false"
                + ", recursiveSubmodules=false"
                + ", trackingSubmodules=false"
                + ", reference='null'"
                + ", parentCredentials=false"
                + ", timeout=null"
                + ", shallow=false"
                + ", depth=null"
                + ", threads=null"
                + '}'));
    }

    @Test
    @Issue("JENKINS-64382")
    public void testDetermineSupportForJGit() throws Exception {
        /* JGit was incorrectly used when submodule option was added with no items checked. */
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/git-plugin");
        scm.getExtensions().add(submoduleOption);
        UnsupportedCommand cmd = new UnsupportedCommand();
        submoduleOption.determineSupportForJGit(scm, cmd);
        assertThat(cmd.determineSupportForJGit(), is(false));
    }

    @Test
    @Issue("JENKINS-64382")
    public void testDetermineSupportForJGitRecursiveSubmodules() throws Exception {
        /* JGit was incorrectly used when submodule option was added with only recursive submodule checked. */
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/git-plugin");
        submoduleOption = new SubmoduleOption(DISABLE_SUBMODULES_FALSE,
                true,
                TRACKING_SUBMODULES_FALSE,
                SUBMODULES_REFERENCE_REPOSITORY,
                SUBMODULES_TIMEOUT,
                USE_PARENT_CREDENTIALS_FALSE);
        scm.getExtensions().add(submoduleOption);
        UnsupportedCommand cmd = new UnsupportedCommand();
        submoduleOption.determineSupportForJGit(scm, cmd);
        assertThat(cmd.determineSupportForJGit(), is(false));
    }

    @Test
    public void testDetermineSupportForJGitThreads() throws Exception {
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/git-plugin");
        Integer threads = randomSmallNonNegativeIntegerOrNull();
        submoduleOption.setThreads(threads);
        scm.getExtensions().add(submoduleOption);
        UnsupportedCommand cmd = new UnsupportedCommand();
        submoduleOption.determineSupportForJGit(scm, cmd);
        assertThat(cmd.determineSupportForJGit(), is(false));
    }
}
