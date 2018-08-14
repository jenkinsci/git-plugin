package hudson.plugins.git;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.plugins.git.GitSCM.BuildChooserContextImpl;
import hudson.plugins.git.GitSCM.DescriptorImpl;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.*;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.util.BuildChooserContext.ContextCallable;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.plugins.git.util.GitUtils;
import hudson.plugins.parameterizedtrigger.BuildTrigger;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCMRevisionState;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.tools.ToolProperty;
import hudson.triggers.SCMTrigger;
import hudson.util.StreamTaskListener;

import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import org.eclipse.jgit.transport.RemoteConfig;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jenkins.model.Jenkins;
import jenkins.plugins.git.CliGitCommand;
import jenkins.plugins.git.GitSampleRepoRule;

/**
 * Tests for {@link GitSCM}.
 * @author ishaaq
 */
public class GitSCMTest extends AbstractGitTestCase {
    @Rule
    public GitSampleRepoRule secondRepo = new GitSampleRepoRule();

    private CredentialsStore store = null;

    @BeforeClass
    public static void setGitDefaults() throws Exception {
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
    }

    @Before
    public void enableSystemCredentialsProvider() throws Exception {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.<Credentials>emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.getInstance())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;

            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
    }

    private StandardCredentials getInvalidCredential() {
        String username = "bad-user";
        String password = "bad-password";
        CredentialsScope scope = CredentialsScope.GLOBAL;
        String id = "username-" + username + "-password-" + password;
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, username, password);
    }

    @Test
    public void trackCredentials() throws Exception {
        StandardCredentials credential = getInvalidCredential();
        store.addCredentials(Domain.global(), credential);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should not be set before job definition", fingerprint, nullValue());

        JenkinsRule.WebClient wc = rule.createWebClient();
        HtmlPage page = wc.goTo("credentials/store/system/domain/_/credentials/" + credential.getId());
        assertThat("Have usage tracking reported", page.getElementById("usage"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-missing"), notNullValue());
        assertThat("No fingerprint created until first use", page.getElementById("usage-present"), nullValue());

        FreeStyleProject project = setupProject("master", credential);

        fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should not be set before first build", fingerprint, nullValue());

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should be set after first build", fingerprint, notNullValue());
        assertThat(fingerprint.getJobs(), hasItem(is(project.getFullName())));
        Fingerprint.RangeSet rangeSet = fingerprint.getRangeSet(project);
        assertThat(rangeSet, notNullValue());
        assertThat(rangeSet.includes(project.getLastBuild().getNumber()), is(true));

        page = wc.goTo("credentials/store/system/domain/_/credentials/" + credential.getId());
        assertThat(page.getElementById("usage-missing"), nullValue());
        assertThat(page.getElementById("usage-present"), notNullValue());
        assertThat(page.getAnchorByText(project.getFullDisplayName()), notNullValue());
    }

    /**
     * Basic test - create a GitSCM based project, check it out and build for the first time.
     * Next test that polling works correctly, make another commit, check that polling finds it,
     * then build it and finally test the build culprits as well as the contents of the workspace.
     * @throws Exception on error
     */
    @Test
    public void testBasic() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testBasicRemotePoll() throws Exception {
//        FreeStyleProject project = setupProject("master", true, false);
        FreeStyleProject project = setupProject("master", false, null, null, null, true, null);
        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        // ... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testBranchSpecWithRemotesMaster() throws Exception {
        FreeStyleProject projectMasterBranch = setupProject("remotes/origin/master", false, null, null, null, true, null);
        // create initial commit and build
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(projectMasterBranch, Result.SUCCESS, commitFile1);
    }

    /**
     * This test and testSpecificRefspecsWithoutCloneOption confirm behaviors of
     * refspecs on initial clone. Without the CloneOption to honor refspec, all
     * references are cloned, even if they will be later ignored due to the
     * refspec.  With the CloneOption to ignore refspec, the initial clone also
     * honors the refspec and only retrieves references per the refspec.
     * @throws Exception on error
     */
    @Test
    @Issue("JENKINS-31393")
    public void testSpecificRefspecs() throws Exception {
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "+refs/heads/foo:refs/remotes/foo", null));

        /* Set CloneOption to honor refspec on initial clone */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        CloneOption cloneOptionMaster = new CloneOption(false, null, null);
        cloneOptionMaster.setHonorRefspec(true);
        ((GitSCM)projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);

        /* Set CloneOption to honor refspec on initial clone */
        FreeStyleProject projectWithFoo = setupProject(repos, Collections.singletonList(new BranchSpec("foo")), null, false, null);
        CloneOption cloneOptionFoo = new CloneOption(false, null, null);
        cloneOptionFoo.setHonorRefspec(true);
        ((GitSCM)projectWithMaster.getScm()).getExtensions().add(cloneOptionFoo);

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");
        // create branch and make initial commit
        git.branch("foo");
        git.checkout().branch("foo");
        commit(commitFile1, johnDoe, "Commit in foo");

        build(projectWithMaster, Result.FAILURE);
        build(projectWithFoo, Result.SUCCESS, commitFile1);
    }

    /**
     * This test and testSpecificRefspecs confirm behaviors of
     * refspecs on initial clone. Without the CloneOption to honor refspec, all
     * references are cloned, even if they will be later ignored due to the
     * refspec.  With the CloneOption to ignore refspec, the initial clone also
     * honors the refspec and only retrieves references per the refspec.
     * @throws Exception on error
     */
    @Test
    @Issue("JENKINS-36507")
    public void testSpecificRefspecsWithoutCloneOption() throws Exception {
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "+refs/heads/foo:refs/remotes/foo", null));
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        FreeStyleProject projectWithFoo = setupProject(repos, Collections.singletonList(new BranchSpec("foo")), null, false, null);

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");
        // create branch and make initial commit
        git.branch("foo");
        git.checkout().branch("foo");
        commit(commitFile1, johnDoe, "Commit in foo");

        build(projectWithMaster, Result.SUCCESS); /* If clone refspec had been honored, this would fail */
        build(projectWithFoo, Result.SUCCESS, commitFile1);
    }

    @Test
    public void testBranchSpecWithRemotesHierarchical() throws Exception {
      FreeStyleProject projectMasterBranch = setupProject("master", false, null, null, null, true, null);
      FreeStyleProject projectHierarchicalBranch = setupProject("remotes/origin/rel-1/xy", false, null, null, null, true, null);
      // create initial commit
      final String commitFile1 = "commitFile1";
      commit(commitFile1, johnDoe, "Commit number 1");
      // create hierarchical branch, delete master branch, and build
      git.branch("rel-1/xy");
      git.checkout("rel-1/xy");
      git.deleteBranch("master");
      build(projectMasterBranch, Result.FAILURE);
      build(projectHierarchicalBranch, Result.SUCCESS, commitFile1);
    }

    @Test
    public void testBranchSpecUsingTagWithSlash() throws Exception {
        FreeStyleProject projectMasterBranch = setupProject("path/tag", false, null, null, null, true, null);
        // create initial commit and build
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1 will be tagged with path/tag");
        testRepo.git.tag("path/tag", "tag with a slash in the tag name");
        build(projectMasterBranch, Result.SUCCESS, commitFile1);
      }

    @Test
    public void testBasicIncludedRegion() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, null, ".*3");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should not have been included", project.poll(listener).hasChanges());

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.poll(listener).hasChanges());

        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());
        
        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testIncludedRegionWithDeeperCommits() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, null, ".*3");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should not have been included", project.poll(listener).hasChanges());
        

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        
        final String commitFile4 = "commitFile4";
        commit(commitFile4, janeDoe, "Commit number 4");
        assertTrue("scm polling did not detect commit3 change", project.poll(listener).hasChanges());

        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());
        
        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testBasicExcludedRegion() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, ".*2", null, null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should have been excluded", project.poll(listener).hasChanges());

        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());

        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testCleanBeforeCheckout() throws Exception {
    	FreeStyleProject p = setupProject("master", false, null, null, "Jane Doe", null);
        ((GitSCM)p.getScm()).getExtensions().add(new CleanBeforeCheckout());
        final String commitFile1 = "commitFile1";
        final String commitFile2 = "commitFile2";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        final FreeStyleBuild firstBuild = build(p, Result.SUCCESS, commitFile1);
        final String branch1 = "Branch1";
        final String branch2 = "Branch2";
        List<BranchSpec> branches = new ArrayList<>();
        branches.add(new BranchSpec("master"));
        branches.add(new BranchSpec(branch1));
        branches.add(new BranchSpec(branch2));
        git.branch(branch1);
        git.checkout(branch1);
        p.poll(listener).hasChanges();
        assertTrue(firstBuild.getLog().contains("Cleaning"));
        assertTrue(firstBuild.getLog().indexOf("Cleaning") > firstBuild.getLog().indexOf("Cloning")); //clean should be after clone
        assertTrue(firstBuild.getLog().indexOf("Cleaning") < firstBuild.getLog().indexOf("Checking out")); //clean before checkout
        assertTrue(firstBuild.getWorkspace().child(commitFile1).exists());
        git.checkout(branch1);
        final FreeStyleBuild secondBuild = build(p, Result.SUCCESS, commitFile2);
        p.poll(listener).hasChanges();
        assertTrue(secondBuild.getLog().contains("Cleaning"));
        assertTrue(secondBuild.getLog().indexOf("Cleaning") < secondBuild.getLog().indexOf("Fetching upstream changes")); 
        assertTrue(secondBuild.getWorkspace().child(commitFile2).exists());

        
    }

    @Issue("JENKINS-8342")
    @Test
    public void testExcludedRegionMultiCommit() throws Exception {
        // Got 2 projects, each one should only build if changes in its own file
        FreeStyleProject clientProject = setupProject("master", false, null, ".*serverFile", null, null);
        FreeStyleProject serverProject = setupProject("master", false, null, ".*clientFile", null, null);
        String initialCommitFile = "initialFile";
        commit(initialCommitFile, johnDoe, "initial commit");
        build(clientProject, Result.SUCCESS, initialCommitFile);
        build(serverProject, Result.SUCCESS, initialCommitFile);

        assertFalse("scm polling should not detect any more changes after initial build", clientProject.poll(listener).hasChanges());
        assertFalse("scm polling should not detect any more changes after initial build", serverProject.poll(listener).hasChanges());

        // Got commits on serverFile, so only server project should build.
        commit("myserverFile", johnDoe, "commit first server file");

        assertFalse("scm polling should not detect any changes in client project", clientProject.poll(listener).hasChanges());
        assertTrue("scm polling did not detect changes in server project", serverProject.poll(listener).hasChanges());

        // Got commits on both client and serverFile, so both projects should build.
        commit("myNewserverFile", johnDoe, "commit new server file");
        commit("myclientFile", johnDoe, "commit first clientfile");

        assertTrue("scm polling did not detect changes in client project", clientProject.poll(listener).hasChanges());
        assertTrue("scm polling did not detect changes in server project", serverProject.poll(listener).hasChanges());
    }

    /*
     * With multiple branches specified in the project and having commits from a user
     * excluded should not build the excluded revisions when another branch changes.
     */
    /*
    @Issue("JENKINS-8342")
    @Test
    public void testMultipleBranchWithExcludedUser() throws Exception {
        final String branch1 = "Branch1";
        final String branch2 = "Branch2";

        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("master"));
        branches.add(new BranchSpec(branch1));
        branches.add(new BranchSpec(branch2));
        final FreeStyleProject project = setupProject(branches, false, null, null, janeDoe.getName(), null, false, null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        // create branches here so we can get back to them later...
        git.branch(branch1);
        git.branch(branch2);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling should detect changes in 'master' branch", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile1, commitFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());

        // Add excluded commit
        final String commitFile4 = "commitFile4";
        commit(commitFile4, janeDoe, "Commit number 4");
        assertFalse("scm polling detected change in 'master', which should have been excluded", project.poll(listener).hasChanges());

        // now jump back...
        git.checkout(branch1);
        final String branch1File1 = "branch1File1";
        commit(branch1File1, janeDoe, "Branch1 commit number 1");
        assertFalse("scm polling detected change in 'Branch1', which should have been excluded", project.poll(listener).hasChanges());

        // and the other branch...
        git.checkout(branch2);

        final String branch2File1 = "branch2File1";
        commit(branch2File1, janeDoe, "Branch2 commit number 1");
        assertFalse("scm polling detected change in 'Branch2', which should have been excluded", project.poll(listener).hasChanges());

        final String branch2File2 = "branch2File2";
        commit(branch2File2, johnDoe, "Branch2 commit number 2");
        assertTrue("scm polling should detect changes in 'Branch2' branch", project.poll(listener).hasChanges());

        //... and build it...
        build(project, Result.SUCCESS, branch2File1, branch2File2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        // now jump back again...
        git.checkout(branch1);

        // Commit excluded after non-excluded commit, should trigger build.
        final String branch1File2 = "branch1File2";
        commit(branch1File2, johnDoe, "Branch1 commit number 2");
        final String branch1File3 = "branch1File3";
        commit(branch1File3, janeDoe, "Branch1 commit number 3");
        assertTrue("scm polling should detect changes in 'Branch1' branch", project.poll(listener).hasChanges());

        build(project, Result.SUCCESS, branch1File1, branch1File2, branch1File3);
    } */

    @Test
    public void testBasicExcludedUser() throws Exception {
        FreeStyleProject project = setupProject("master", false, null, null, "Jane Doe", null);

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertFalse("scm polling detected commit2 change, which should have been excluded", project.poll(listener).hasChanges());
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling did not detect commit3 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2, commitFile3);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have two culprit", 2, culprits.size());

        PersonIdent[] expected = {johnDoe, janeDoe};
        assertCulprits("jane doe and john doe should be the culprits", culprits, expected);

        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertTrue(build2.getWorkspace().child(commitFile3).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

    }

    @Test
    public void testBasicInSubdir() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        ((GitSCM)project.getScm()).getExtensions().add(new RelativeTargetDirectory("subdir"));

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, "subdir", Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, "subdir", Result.SUCCESS,
                                            commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertEquals("The workspace should have a 'subdir' subdirectory, but does not.", true,
                     build2.getWorkspace().child("subdir").exists());
        assertEquals("The 'subdir' subdirectory should contain commitFile2, but does not.", true,
                build2.getWorkspace().child("subdir").child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testBasicWithSlave() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Issue("HUDSON-7547")
    @Test
    public void testBasicWithSlaveNoExecutorsOnMaster() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        rule.jenkins.setNumExecutors(0);

        project.setAssignedLabel(rule.createSlave().getSelfLabel());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", janeDoe.getName(), culprits.iterator().next().getFullName());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testAuthorOrCommitterFalse() throws Exception {
        // Test with authorOrCommitter set to false and make sure we get the committer.
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());

        final FreeStyleBuild secondBuild = build(project, Result.SUCCESS, commitFile2);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final Set<User> secondCulprits = secondBuild.getCulprits();

        assertEquals("The build should have only one culprit", 1, secondCulprits.size());
        assertEquals("Did not get the committer as the change author with authorOrCommitter==false",
                     janeDoe.getName(), secondCulprits.iterator().next().getFullName());
    }

    @Test
    public void testAuthorOrCommitterTrue() throws Exception {
        // Next, test with authorOrCommitter set to true and make sure we get the author.
        FreeStyleProject project = setupSimpleProject("master");
        ((GitSCM)project.getScm()).getExtensions().add(new AuthorInChangelog());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, janeDoe, "Commit number 1");
        final FreeStyleBuild firstBuild = build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());

        final FreeStyleBuild secondBuild = build(project, Result.SUCCESS, commitFile2);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final Set<User> secondCulprits = secondBuild.getCulprits();

        assertEquals("The build should have only one culprit", 1, secondCulprits.size());
        assertEquals("Did not get the author as the change author with authorOrCommitter==true",
                johnDoe.getName(), secondCulprits.iterator().next().getFullName());
    }

    @Test
    public void testNewCommitToUntrackedBranchDoesNotTriggerBuild() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        //now create and checkout a new branch:
        git.checkout(Constants.HEAD, "untracked");
        //.. and commit to it:
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertFalse("scm polling should not detect commit2 change because it is not in the branch we are tracking.", project.poll(listener).hasChanges());
    }

    private String checkoutString(FreeStyleProject project, String envVar) {
        return "checkout -f " + getEnvVars(project).get(envVar);
    }

    @Test
    public void testEnvVarsAvailable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        assertEquals("origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
        rule.assertLogContains(getEnvVars(project).get(GitSCM.GIT_BRANCH), build1);

        rule.assertLogContains(checkoutString(project, GitSCM.GIT_COMMIT), build1);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);

        rule.assertLogNotContains(checkoutString(project, GitSCM.GIT_PREVIOUS_COMMIT), build2);
        rule.assertLogContains(checkoutString(project, GitSCM.GIT_PREVIOUS_COMMIT), build1);

        rule.assertLogNotContains(checkoutString(project, GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT), build2);
        rule.assertLogContains(checkoutString(project, GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT), build1);
    }

    @Issue("HUDSON-7411")
    @Test
    public void testNodeEnvVarsAvailable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        Node s = rule.createSlave();
        setVariables(s, new Entry("TESTKEY", "slaveValue"));
        project.setAssignedLabel(s.getSelfLabel());
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        assertEquals("slaveValue", getEnvVars(project).get("TESTKEY"));
    }

    /*
     * A previous version of GitSCM would only build against branches, not tags. This test checks that that
     * regression has been fixed.
     */
    @Test
    public void testGitSCMCanBuildAgainstTags() throws Exception {
        final String mytag = "mytag";
        FreeStyleProject project = setupSimpleProject(mytag);
        build(project, Result.FAILURE); // fail, because there's nothing to be checked out here

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");

        // Try again. The first build will leave the repository in a bad state because we
        // cloned something without even a HEAD - which will mean it will want to re-clone once there is some
        // actual data.
        build(project, Result.FAILURE); // fail, because there's nothing to be checked out here

        //now create and checkout a new branch:
        final String tmpBranch = "tmp";
        git.branch(tmpBranch);
        git.checkout(tmpBranch);
        // commit to it
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertFalse("scm polling should not detect any more changes since mytag is untouched right now", project.poll(listener).hasChanges());
        build(project, Result.FAILURE);  // fail, because there's nothing to be checked out here

        // tag it, then delete the tmp branch
        git.tag(mytag, "mytag initial");
        git.checkout("master");
        git.deleteBranch(tmpBranch);

        // at this point we're back on master, there are no other branches, tag "mytag" exists but is
        // not part of "master"
        assertTrue("scm polling should detect commit2 change in 'mytag'", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());

        // now, create tmp branch again against mytag:
        git.checkout(mytag);
        git.branch(tmpBranch);
        // another commit:
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertFalse("scm polling should not detect any more changes since mytag is untouched right now", project.poll(listener).hasChanges());

        // now we're going to force mytag to point to the new commit, if everything goes well, gitSCM should pick the change up:
        git.tag(mytag, "mytag moved");
        git.checkout("master");
        git.deleteBranch(tmpBranch);

        // at this point we're back on master, there are no other branches, "mytag" has been updated to a new commit:
        assertTrue("scm polling should detect commit3 change in 'mytag'", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile3);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());
    }

    /*
     * Not specifying a branch string in the project implies that we should be polling for changes in
     * all branches.
     */
    @Test
    public void testMultipleBranchBuild() throws Exception {
        // empty string will result in a project that tracks against changes in all branches:
        final FreeStyleProject project = setupSimpleProject("");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        // create a branch here so we can get back to this point  later...
        final String fork = "fork";
        git.branch(fork);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        final String commitFile3 = "commitFile3";
        commit(commitFile3, johnDoe, "Commit number 3");
        assertTrue("scm polling should detect changes in 'master' branch", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile1, commitFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());

        // now jump back...
        git.checkout(fork);

        // add some commits to the fork branch...
        final String forkFile1 = "forkFile1";
        commit(forkFile1, johnDoe, "Fork commit number 1");
        final String forkFile2 = "forkFile2";
        commit(forkFile2, johnDoe, "Fork commit number 2");
        assertTrue("scm polling should detect changes in 'fork' branch", project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, forkFile1, forkFile2);
        assertFalse("scm polling should not detect any more changes after last build", project.poll(listener).hasChanges());
    }

    @Test
    public void testMultipleBranchesWithTags() throws Exception {
        List<BranchSpec> branchSpecs = Arrays.asList(
                new BranchSpec("refs/tags/v*"),
                new BranchSpec("refs/remotes/origin/non-existent"));
        FreeStyleProject project = setupProject(branchSpecs, false, null, null, janeDoe.getName(), null, false, null);

        // create initial commit and then run the build against it:
        // Here the changelog is by default empty (because changelog for first commit is always empty
        commit("commitFileBase", johnDoe, "Initial Commit");

        // there are no branches to be build
        FreeStyleBuild freeStyleBuild = build(project, Result.FAILURE);

        final String v1 = "v1";

        git.tag(v1, "version 1");
        assertTrue("v1 tag exists", git.tagExists(v1));

        freeStyleBuild = build(project, Result.SUCCESS);
        assertTrue("change set is empty", freeStyleBuild.getChangeSet().isEmptySet());

        commit("file1", johnDoe, "change to file1");
        git.tag("none", "latest");

        freeStyleBuild = build(project, Result.SUCCESS);

        ObjectId tag = git.revParse(Constants.R_TAGS + v1);
        GitSCM scm = (GitSCM)project.getScm();
        BuildData buildData = scm.getBuildData(freeStyleBuild);

        assertEquals("last build matches the v1 tag revision", tag, buildData.lastBuild.getSHA1());
    }

    @Issue("JENKINS-19037")
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Test
    public void testBlankRepositoryName() throws Exception {
        new GitSCM(null);
    }

    @Issue("JENKINS-10060")
    @Test
    public void testSubmoduleFixup() throws Exception {
        File repo = secondRepo.getRoot();
        FilePath moduleWs = new FilePath(repo);
        org.jenkinsci.plugins.gitclient.GitClient moduleRepo = Git.with(listener, new EnvVars()).in(repo).getClient();

        {// first we create a Git repository with submodule
            moduleRepo.init();
            moduleWs.child("a").touch(0);
            moduleRepo.add("a");
            moduleRepo.commit("creating a module");

            git.addSubmodule(repo.getAbsolutePath(), "module1");
            git.commit("creating a super project");
        }

        // configure two uproject 'u' -> 'd' that's chained together.
        FreeStyleProject u = createFreeStyleProject();
        FreeStyleProject d = createFreeStyleProject();

        u.setScm(new GitSCM(workDir.getPath()));
        u.getPublishersList().add(new BuildTrigger(new hudson.plugins.parameterizedtrigger.BuildTriggerConfig(d.getName(), ResultCondition.SUCCESS,
                new GitRevisionBuildParameters())));

        d.setScm(new GitSCM(workDir.getPath()));
        rule.jenkins.rebuildDependencyGraph();


        FreeStyleBuild ub = rule.assertBuildStatusSuccess(u.scheduleBuild2(0));
        System.out.println(ub.getLog());
        for  (int i=0; (d.getLastBuild()==null || d.getLastBuild().isBuilding()) && i<100; i++) // wait only up to 10 sec to avoid infinite loop
            Thread.sleep(100);

        FreeStyleBuild db = d.getLastBuild();
        assertNotNull("downstream build didn't happen",db);
        rule.assertBuildStatusSuccess(db);
    }

    @Test
    public void testBuildChooserContext() throws Exception {
        final FreeStyleProject p = createFreeStyleProject();
        final FreeStyleBuild b = rule.assertBuildStatusSuccess(p.scheduleBuild2(0));

        BuildChooserContextImpl c = new BuildChooserContextImpl(p, b, null);
        c.actOnBuild(new ContextCallable<Run<?,?>, Object>() {
            public Object invoke(Run param, VirtualChannel channel) throws IOException, InterruptedException {
                assertSame(param,b);
                return null;
            }
        });
        c.actOnProject(new ContextCallable<Job<?,?>, Object>() {
            public Object invoke(Job param, VirtualChannel channel) throws IOException, InterruptedException {
                assertSame(param,p);
                return null;
            }
        });
        DumbSlave s = rule.createOnlineSlave();
        assertEquals(p.toString(), s.getChannel().call(new BuildChooserContextTestCallable(c)));
    }

    private static class BuildChooserContextTestCallable extends MasterToSlaveCallable<String,IOException> {
        private final BuildChooserContext c;

        public BuildChooserContextTestCallable(BuildChooserContext c) {
            this.c = c;
        }

        public String call() throws IOException {
            try {
                return c.actOnProject(new ContextCallable<Job<?,?>, String>() {
                    public String invoke(Job<?,?> param, VirtualChannel channel) throws IOException, InterruptedException {
                        assertTrue(channel instanceof Channel);
                        assertTrue(Hudson.getInstance()!=null);
                        return param.toString();
                    }
                });
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

    }

    // eg: "jane doe and john doe should be the culprits", culprits, [johnDoe, janeDoe])
    static public void assertCulprits(String assertMsg, Set<User> actual, PersonIdent[] expected)
    {
        Collection<String> fullNames = Collections2.transform(actual, new Function<User,String>() {
            public String apply(User u)
            {
                return u.getFullName();
            }
        });

        for(PersonIdent p : expected)
        {
            assertTrue(assertMsg, fullNames.contains(p.getName()));
        }
    }

    @Test
    public void testEmailCommitter() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // setup global config
        GitSCM scm = (GitSCM) project.getScm();
        final DescriptorImpl descriptor = (DescriptorImpl) scm.getDescriptor();
        assertFalse("Wrong initial value for create account based on e-mail", scm.isCreateAccountBasedOnEmail());
        descriptor.setCreateAccountBasedOnEmail(true);
        assertTrue("Create account based on e-mail not set", scm.isCreateAccountBasedOnEmail());

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";

        final PersonIdent jeffDoe = new PersonIdent("Jeff Doe", "jeff@doe.com");
        commit(commitFile2, jeffDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...

        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        final Set<User> culprits = build2.getCulprits();

        assertEquals("The build should have only one culprit", 1, culprits.size());
        User culprit = culprits.iterator().next();
        assertEquals("", jeffDoe.getEmailAddress(), culprit.getId());
        assertEquals("", jeffDoe.getName(), culprit.getFullName());

        rule.assertBuildStatusSuccess(build);
    }

    // Disabled - consistently fails, needs more analysis
    // @Test
    public void testFetchFromMultipleRepositories() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("second", secondRepo.getRoot(), listener);
        List<UserRemoteConfig> remotes = new ArrayList<>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        project.setScm(new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList()));

        // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        build(project, Result.SUCCESS, commitFile1);

        /* Diagnostic help - for later use */
        SCMRevisionState baseline = project.poll(listener).baseline;
        Change change = project.poll(listener).change;
        SCMRevisionState remote = project.poll(listener).remote;
        String assertionMessage = MessageFormat.format("polling incorrectly detected change after build. Baseline: {0}, Change: {1}, Remote: {2}", baseline, change, remote);
        assertFalse(assertionMessage, project.poll(listener).hasChanges());

        final String commitFile2 = "commitFile2";
        secondTestRepo.commit(commitFile2, janeDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        //... and build it...
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    private void branchSpecWithMultipleRepositories(String branchName) throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("second", secondRepo.getRoot(), listener);
        List<UserRemoteConfig> remotes = new ArrayList<>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");

        project.setScm(new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec(branchName)),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList()));

        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);
        rule.assertBuildStatusSuccess(build);
    }

    @Issue("JENKINS-26268")
    public void testBranchSpecAsSHA1WithMultipleRepositories() throws Exception {
        branchSpecWithMultipleRepositories(testRepo.git.revParse("HEAD").getName());
    }

    @Issue("JENKINS-26268")
    public void testBranchSpecAsRemotesOriginMasterWithMultipleRepositories() throws Exception {
        branchSpecWithMultipleRepositories("remotes/origin/master");
    }

    @Issue("JENKINS-25639")
    @Test
    public void testCommitDetectedOnlyOnceInMultipleRepositories() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        TestGitRepo secondTestRepo = new TestGitRepo("secondRepo", secondRepo.getRoot(), listener);
        List<UserRemoteConfig> remotes = new ArrayList<>();
        remotes.addAll(testRepo.remoteConfigs());
        remotes.addAll(secondTestRepo.remoteConfigs());

        GitSCM gitSCM = new GitSCM(
                remotes,
                Collections.singletonList(new BranchSpec("origin/master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(gitSCM);

        commit("commitFile1", johnDoe, "Commit number 1");
        FreeStyleBuild build = build(project, Result.SUCCESS, "commitFile1");

        commit("commitFile2", johnDoe, "Commit number 2");
        git = Git.with(listener, new EnvVars()).in(build.getWorkspace()).getClient();
        for (RemoteConfig remoteConfig : gitSCM.getRepositories()) {
            git.fetch_().from(remoteConfig.getURIs().get(0), remoteConfig.getFetchRefSpecs());
        }
        BuildChooser buildChooser = gitSCM.getBuildChooser();
        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, "origin/master", git, listener, project.getLastBuild().getAction(BuildData.class), null);
        assertEquals(1, candidateRevisions.size());
        gitSCM.setBuildChooser(buildChooser); // Should be a no-op
        Collection<Revision> candidateRevisions2 = buildChooser.getCandidateRevisions(false, "origin/master", git, listener, project.getLastBuild().getAction(BuildData.class), null);
        assertThat(candidateRevisions2, is(candidateRevisions));
    }

    private final Random random = new Random();
    private boolean useChangelogToBranch = random.nextBoolean();

    private void addChangelogToBranchExtension(GitSCM scm) {
        if (useChangelogToBranch) {
            /* Changelog should be no different with this enabled or disabled */
            ChangelogToBranchOptions changelogOptions = new ChangelogToBranchOptions("origin", "master");
            scm.getExtensions().add(new ChangelogToBranch(changelogOptions));
        }
        useChangelogToBranch = !useChangelogToBranch;
    }

    @Test
    public void testMerge() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "default", MergeCommand.GitPluginFastForwardMode.FF)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Issue("JENKINS-20392")
    @Test
    public void testMergeChangelog() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "default", MergeCommand.GitPluginFastForwardMode.FF)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        // Here the changelog is by default empty (because changelog for first commit is always empty
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        // Create second commit and run build
        // Here the changelog should contain exactly this one new commit
        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        String commitMessage = "Commit number 2";
        commit(commitFile2, johnDoe, commitMessage);
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);

        ChangeLogSet<? extends ChangeLogSet.Entry> changeLog = build2.getChangeSet();
        assertEquals("Changelog should contain one item", 1, changeLog.getItems().length);

        GitChangeSet singleChange = (GitChangeSet) changeLog.getItems()[0];
        assertEquals("Changelog should contain commit number 2", commitMessage, singleChange.getComment().trim());
    }

    @Test
    public void testMergeWithSlave() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        final FreeStyleBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testMergeFailed() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", "", MergeCommand.GitPluginFastForwardMode.FF)));
        addChangelogToBranchExtension(scm);

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        commit(commitFile1, "other content", johnDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        final FreeStyleBuild build2 = build(project, Result.FAILURE);
        rule.assertBuildStatus(Result.FAILURE, build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }
    
    @Issue("JENKINS-25191")
    @Test
    public void testMultipleMergeFailed() throws Exception {
    	FreeStyleProject project = setupSimpleProject("master");
    	
    	GitSCM scm = new GitSCM(
    			createRemoteRepositories(),
    			Collections.singletonList(new BranchSpec("master")),
    			false, Collections.<SubmoduleConfig>emptyList(),
    			null, null,
    			Collections.<GitSCMExtension>emptyList());
    	project.setScm(scm);
	scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration1", "", MergeCommand.GitPluginFastForwardMode.FF)));
	scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration2", "", MergeCommand.GitPluginFastForwardMode.FF)));
        addChangelogToBranchExtension(scm);
    	
    	commit("dummyFile", johnDoe, "Initial Commit");
    	testRepo.git.branch("integration1");
    	testRepo.git.branch("integration2");
    	build(project, Result.SUCCESS);
    	
    	final String commitFile = "commitFile";
    	testRepo.git.checkoutBranch("integration1","master");
    	commit(commitFile,"abc", johnDoe, "merge conflict with integration2");
    	
    	testRepo.git.checkoutBranch("integration2","master");
    	commit(commitFile,"cde", johnDoe, "merge conflict with integration1");
    	
    	final FreeStyleBuild build = build(project, Result.FAILURE);
    	
    	assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testMergeFailedWithSlave() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");

        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        commit(commitFile1, "other content", johnDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        final FreeStyleBuild build2 = build(project, Result.FAILURE);
        rule.assertBuildStatus(Result.FAILURE, build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }


    @Test
    public void testMergeWithMatrixBuild() throws Exception {
        
        //Create a matrix project and a couple of axes
        MatrixProject project = rule.jenkins.createProject(MatrixProject.class, "xyz");
        project.setAxes(new AxisList(new Axis("VAR","a","b")));
        
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("*")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new PreBuildMerge(new UserMergeOptions("origin", "integration", null, null)));
        addChangelogToBranchExtension(scm);
        project.setScm(scm);

        // create initial commit and then run the build against it:
        commit("commitFileBase", johnDoe, "Initial Commit");
        testRepo.git.branch("integration");
        build(project, Result.SUCCESS, "commitFileBase");
        
        
        testRepo.git.checkout(null, "topic1");
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final MatrixBuild build1 = build(project, Result.SUCCESS, commitFile1);
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
        // do what the GitPublisher would do
        testRepo.git.deleteBranch("integration");
        testRepo.git.checkout("topic1", "integration");

        testRepo.git.checkout("master", "topic2");
        final String commitFile2 = "commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");
        assertTrue("scm polling did not detect commit2 change", project.poll(listener).hasChanges());
        final MatrixBuild build2 = build(project, Result.SUCCESS, commitFile2);
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        rule.assertBuildStatusSuccess(build2);
        assertFalse("scm polling should not detect any more changes after build", project.poll(listener).hasChanges());
    }

    @Test
    public void testEnvironmentVariableExpansion() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.setScm(new GitSCM("${CAT}"+testRepo.gitDir.getPath()));

        // create initial commit and then run the build against it:
        commit("a.txt", johnDoe, "Initial Commit");

        build(project, Result.SUCCESS, "a.txt");

        PollingResult r = project.poll(StreamTaskListener.fromStdout());
        assertFalse(r.hasChanges());

        commit("b.txt", johnDoe, "Another commit");

        r = project.poll(StreamTaskListener.fromStdout());
        assertTrue(r.hasChanges());

        build(project, Result.SUCCESS, "b.txt");
    }

    @TestExtension("testEnvironmentVariableExpansion")
    public static class SupplySomeEnvVars extends EnvironmentContributor {
        @Override
        public void buildEnvironmentFor(Run r, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
            envs.put("CAT","");
        }
    }

    private List<UserRemoteConfig> createRepoList(String url) {
        List<UserRemoteConfig> repoList = new ArrayList<>();
        repoList.add(new UserRemoteConfig(url, null, null, null));
        return repoList;
    }

    /*
     * Makes sure that git browser URL is preserved across config round trip.
     */
    @Issue("JENKINS-22604")
    @Test
    public void testConfigRoundtripURLPreserved() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        final String url = "https://github.com/jenkinsci/jenkins";
        GitRepositoryBrowser browser = new GithubWeb(url);
        GitSCM scm = new GitSCM(createRepoList(url),
                                Collections.singletonList(new BranchSpec("")),
                                false, Collections.<SubmoduleConfig>emptyList(),
                                browser, null, null);
        p.setScm(scm);
        rule.configRoundtrip(p);
        rule.assertEqualDataBoundBeans(scm,p.getScm());
        assertEquals("Wrong key", "git " + url, scm.getKey());
    }

    /*
     * Makes sure that git extensions are preserved across config round trip.
     */
    @Issue("JENKINS-33695")
    @Test
    public void testConfigRoundtripExtensionsPreserved() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        final String url = "git://github.com/jenkinsci/git-plugin.git";
        GitRepositoryBrowser browser = new GithubWeb(url);
        GitSCM scm = new GitSCM(createRepoList(url),
                Collections.singletonList(new BranchSpec("*/master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                browser, null, null);
        p.setScm(scm);

        /* Assert that no extensions are loaded initially */
        assertEquals(Collections.emptyList(), scm.getExtensions().toList());

        /* Add LocalBranch extension */
        LocalBranch localBranchExtension = new LocalBranch("**");
        scm.getExtensions().add(localBranchExtension);
        assertTrue(scm.getExtensions().toList().contains(localBranchExtension));

        /* Save the configuration */
        rule.configRoundtrip(p);
        List<GitSCMExtension> extensions = scm.getExtensions().toList();;
        assertTrue(extensions.contains(localBranchExtension));
        assertEquals("Wrong extension count before reload", 1, extensions.size());

        /* Reload configuration from disc */
        p.doReload();
        GitSCM reloadedGit = (GitSCM) p.getScm();
        List<GitSCMExtension> reloadedExtensions = reloadedGit.getExtensions().toList();
        assertEquals("Wrong extension count after reload", 1, reloadedExtensions.size());
        LocalBranch reloadedLocalBranch = (LocalBranch) reloadedExtensions.get(0);
        assertEquals(localBranchExtension.getLocalBranch(), reloadedLocalBranch.getLocalBranch());
    }

    /*
     * Makes sure that the configuration form works.
     */
    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/jenkins");
        p.setScm(scm);
        rule.configRoundtrip(p);
        rule.assertEqualDataBoundBeans(scm,p.getScm());
    }

    /*
     * Sample configuration that should result in no extensions at all
     */
    @Test
    public void testDataCompatibility1() throws Exception {
        FreeStyleProject p = (FreeStyleProject) rule.jenkins.createProjectFromXML("foo", getClass().getResourceAsStream("GitSCMTest/old1.xml"));
        GitSCM oldGit = (GitSCM) p.getScm();
        assertEquals(Collections.emptyList(), oldGit.getExtensions().toList());
        assertEquals(0, oldGit.getSubmoduleCfg().size());
        assertEquals("git git://github.com/jenkinsci/model-ant-project.git", oldGit.getKey());
        assertThat(oldGit.getEffectiveBrowser(), instanceOf(GithubWeb.class));
        GithubWeb browser = (GithubWeb) oldGit.getEffectiveBrowser();
        assertEquals(browser.getRepoUrl(), "https://github.com/jenkinsci/model-ant-project.git/");
    }

    @Test
    public void testPleaseDontContinueAnyway() throws Exception {
        // create an empty repository with some commits
        testRepo.commit("a","foo",johnDoe, "added");

        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new GitSCM(testRepo.gitDir.getAbsolutePath()));

        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // this should fail as it fails to fetch
        p.setScm(new GitSCM("http://localhost:4321/no/such/repository.git"));
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
    }

    @Issue("JENKINS-19108")
    @Test
    public void testCheckoutToSpecificBranch() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        GitSCM oldGit = new GitSCM("https://github.com/jenkinsci/model-ant-project.git/");
        setupJGit(oldGit);
        oldGit.getExtensions().add(new LocalBranch("master"));
        p.setScm(oldGit);

        FreeStyleBuild b = rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        GitClient gc = Git.with(StreamTaskListener.fromStdout(),null).in(b.getWorkspace()).getClient();
        gc.withRepository(new RepositoryCallback<Void>() {
            public Void invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
                Ref head = repo.findRef("HEAD");
                assertTrue("Detached HEAD",head.isSymbolic());
                Ref t = head.getTarget();
                assertEquals(t.getName(),"refs/heads/master");

                return null;
            }
        });
    }
    
    /**
     * Verifies that if project specifies LocalBranch with value of "**" 
     * that the checkout to a local branch using remote branch name sans 'origin'.
     * This feature is necessary to support Maven release builds that push updated
     * pom.xml to remote branch as 
     * <pre>
     * git push origin localbranch:localbranch
     * </pre>
     * @throws Exception on error
     */
    @Test
    public void testCheckoutToDefaultLocalBranch_StarStar() throws Exception {
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new LocalBranch("**"));
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("GIT_BRANCH", "origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
       assertEquals("GIT_LOCAL_BRANCH", "master", getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH));
    }

    /**
     * Verifies that if project specifies LocalBranch with null value (empty string) 
     * that the checkout to a local branch using remote branch name sans 'origin'.
     * This feature is necessary to support Maven release builds that push updated
     * pom.xml to remote branch as 
     * <pre>
     * git push origin localbranch:localbranch
     * </pre>
     * @throws Exception on error
     */
    @Test
    public void testCheckoutToDefaultLocalBranch_NULL() throws Exception {
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new LocalBranch(""));
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("GIT_BRANCH", "origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
       assertEquals("GIT_LOCAL_BRANCH", "master", getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH));
    }

    /*
     * Verifies that GIT_LOCAL_BRANCH is not set if LocalBranch extension
     * is not configured.
     */
    @Test
    public void testCheckoutSansLocalBranchExtension() throws Exception {
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("GIT_BRANCH", "origin/master", getEnvVars(project).get(GitSCM.GIT_BRANCH));
       assertEquals("GIT_LOCAL_BRANCH", null, getEnvVars(project).get(GitSCM.GIT_LOCAL_BRANCH));
    }
    
    /*
     * Verifies that GIT_CHECKOUT_DIR is set to "checkoutDir" if RelativeTargetDirectory extension
     * is configured.
     */
    @Test
    public void testCheckoutRelativeTargetDirectoryExtension() throws Exception {
       FreeStyleProject project = setupProject("master", false, "checkoutDir");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       GitSCM git = (GitSCM)project.getScm();
       git.getExtensions().add(new RelativeTargetDirectory("checkoutDir"));
       FreeStyleBuild build1 = build(project, "checkoutDir", Result.SUCCESS, commitFile1);

       assertEquals("GIT_CHECKOUT_DIR", "checkoutDir", getEnvVars(project).get(GitSCM.GIT_CHECKOUT_DIR));
    }

    /*
     * Verifies that GIT_CHECKOUT_DIR is not set if RelativeTargetDirectory extension
     * is not configured.
     */
    @Test
    public void testCheckoutSansRelativeTargetDirectoryExtension() throws Exception {
       FreeStyleProject project = setupSimpleProject("master");

       final String commitFile1 = "commitFile1";
       commit(commitFile1, johnDoe, "Commit number 1");
       FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

       assertEquals("GIT_CHECKOUT_DIR", null, getEnvVars(project).get(GitSCM.GIT_CHECKOUT_DIR));
    }
    @Test
    public void testCheckoutFailureIsRetryable() throws Exception {
        FreeStyleProject project = setupSimpleProject("master");

        // run build first to create workspace
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final FreeStyleBuild build1 = build(project, Result.SUCCESS, commitFile1);

        final String commitFile2 = "commitFile2";
        commit(commitFile2, janeDoe, "Commit number 2");

        // create lock file to simulate lock collision
        File lock = new File(build1.getWorkspace().toString(), ".git/index.lock");
        try {
            FileUtils.touch(lock);
            final FreeStyleBuild build2 = build(project, Result.FAILURE);
            rule.assertLogContains("java.io.IOException: Could not checkout", build2);
        } finally {
            lock.delete();
        }
    }

    @Test
    public void testInitSparseCheckout() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
        FreeStyleProject project = setupProject("master", Lists.newArrayList(new SparseCheckoutPath("toto")));

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("toto").exists());
        assertTrue(build1.getWorkspace().child(commitFile1).exists());
        assertFalse(build1.getWorkspace().child("titi").exists());
        assertFalse(build1.getWorkspace().child(commitFile2).exists());
    }

    @Test
    public void testInitSparseCheckoutBis() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
        FreeStyleProject project = setupProject("master", Lists.newArrayList(new SparseCheckoutPath("titi")));

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("titi").exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        assertFalse(build1.getWorkspace().child("toto").exists());
        assertFalse(build1.getWorkspace().child(commitFile1).exists());
    }

    @Test
    public void testSparseCheckoutAfterNormalCheckout() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
        FreeStyleProject project = setupSimpleProject("master");

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("titi").exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        assertTrue(build1.getWorkspace().child("toto").exists());
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

        ((GitSCM) project.getScm()).getExtensions().add(new SparseCheckoutPaths(Lists.newArrayList(new SparseCheckoutPath("titi"))));

        final FreeStyleBuild build2 = build(project, Result.SUCCESS);
        assertTrue(build2.getWorkspace().child("titi").exists());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertFalse(build2.getWorkspace().child("toto").exists());
        assertFalse(build2.getWorkspace().child(commitFile1).exists());
    }

    @Test
    public void testNormalCheckoutAfterSparseCheckout() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
        FreeStyleProject project = setupProject("master", Lists.newArrayList(new SparseCheckoutPath("titi")));

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build2 = build(project, Result.SUCCESS);
        assertTrue(build2.getWorkspace().child("titi").exists());
        assertTrue(build2.getWorkspace().child(commitFile2).exists());
        assertFalse(build2.getWorkspace().child("toto").exists());
        assertFalse(build2.getWorkspace().child(commitFile1).exists());

        ((GitSCM) project.getScm()).getExtensions().remove(SparseCheckoutPaths.class);

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("titi").exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        assertTrue(build1.getWorkspace().child("toto").exists());
        assertTrue(build1.getWorkspace().child(commitFile1).exists());

    }

    @Test
    public void testInitSparseCheckoutOverSlave() throws Exception {
        if (!sampleRepo.gitVersionAtLeast(1, 7, 10)) {
            /* Older git versions have unexpected behaviors with sparse checkout */
            return;
        }
        FreeStyleProject project = setupProject("master", Lists.newArrayList(new SparseCheckoutPath("titi")));
        project.setAssignedLabel(rule.createSlave().getSelfLabel());

        // run build first to create workspace
        final String commitFile1 = "toto/commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        final String commitFile2 = "titi/commitFile2";
        commit(commitFile2, johnDoe, "Commit number 2");

        final FreeStyleBuild build1 = build(project, Result.SUCCESS);
        assertTrue(build1.getWorkspace().child("titi").exists());
        assertTrue(build1.getWorkspace().child(commitFile2).exists());
        assertFalse(build1.getWorkspace().child("toto").exists());
        assertFalse(build1.getWorkspace().child(commitFile1).exists());
    }

    @Issue("JENKINS-22009")
    @Test
    public void testPolling_environmentValueInBranchSpec() throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_BRANCH", "master")));

        // commit something in order to create an initial base version in git
        commit("toto/commitFile1", johnDoe, "Commit number 1");

        // build the project
        build(project, Result.SUCCESS);

        assertFalse("No changes to git since last build, thus no new build is expected", project.poll(listener).hasChanges());
    }

    @Issue("JENKINS-29066")
    public void baseTestPolling_parentHead(List<GitSCMExtension> extensions) throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                extensions);
        project.setScm(scm);

        // commit something in order to create an initial base version in git
        commit("toto/commitFile1", johnDoe, "Commit number 1");
        git.branch("someBranch");
        commit("toto/commitFile2", johnDoe, "Commit number 2");

        assertTrue("polling should detect changes",project.poll(listener).hasChanges());

        // build the project
        build(project, Result.SUCCESS);

        /* Expects 1 build because the build of someBranch incorporates all
         * the changes from the master branch as well as the changes from someBranch.
         */
        assertEquals("Wrong number of builds", 1, project.getBuilds().size());

        assertFalse("polling should not detect changes",project.poll(listener).hasChanges());
    }

    @Issue("JENKINS-29066")
    @Test
    public void testPolling_parentHead() throws Exception {
        baseTestPolling_parentHead(Collections.<GitSCMExtension>emptyList());
    }

    @Issue("JENKINS-29066")
    @Test
    public void testPolling_parentHead_DisableRemotePoll() throws Exception {
        baseTestPolling_parentHead(Collections.<GitSCMExtension>singletonList(new DisableRemotePoll()));
    }

    @Test
    public void testPollingAfterManualBuildWithParametrizedBranchSpec() throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_BRANCH", "trackedbranch")));

        // Initial commit to master
        commit("file1", johnDoe, "Initial Commit");
        
        // Create the branches
        git.branch("trackedbranch");
        git.branch("manualbranch");
        
        final StringParameterValue branchParam = new StringParameterValue("MY_BRANCH", "manualbranch");
        final Action[] actions = {new ParametersAction(branchParam)};
        FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause(), actions).get();
        rule.assertBuildStatus(Result.SUCCESS, build);

        assertFalse("No changes to git since last build", project.poll(listener).hasChanges());

        git.checkout("manualbranch");
        commit("file2", johnDoe, "Commit to manually build branch");
        assertFalse("No changes to tracked branch", project.poll(listener).hasChanges());

        git.checkout("trackedbranch");
        commit("file3", johnDoe, "Commit to tracked branch");
        assertTrue("A change should be detected in tracked branch", project.poll(listener).hasChanges());
        
    }
    
    private final class FakeParametersAction implements EnvironmentContributingAction, Serializable {
        // Test class for testPolling_environmentValueAsEnvironmentContributingAction test case
        final ParametersAction m_forwardingAction;

        public FakeParametersAction(StringParameterValue params) {
            this.m_forwardingAction = new ParametersAction(params);
        }

        public void buildEnvVars(AbstractBuild<?, ?> ab, EnvVars ev) {
            this.m_forwardingAction.buildEnvVars(ab, ev);
        }

        public String getIconFileName() {
            return this.m_forwardingAction.getIconFileName();
        }

        public String getDisplayName() {
            return this.m_forwardingAction.getDisplayName();
        }

        public String getUrlName() {
            return this.m_forwardingAction.getUrlName();
        }

        public List<ParameterValue> getParameters() {
            return this.m_forwardingAction.getParameters();
        }

        private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        }

        private void readObjectNoData() throws ObjectStreamException {
        }
    }

    @Test
	public void testPolling_CanDoRemotePollingIfOneBranchButMultipleRepositories() throws Exception {
		FreeStyleProject project = createFreeStyleProject();
		List<UserRemoteConfig> remoteConfigs = new ArrayList<>();
		remoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "", null));
		remoteConfigs.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "someOtherRepo", "", null));
		GitSCM scm = new GitSCM(remoteConfigs,
				Collections.singletonList(new BranchSpec("origin/master")), false,
				Collections.<SubmoduleConfig> emptyList(), null, null,
				Collections.<GitSCMExtension> emptyList());
		project.setScm(scm);
		commit("commitFile1", johnDoe, "Commit number 1");

		FreeStyleBuild first_build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        rule.assertBuildStatus(Result.SUCCESS, first_build);

		first_build.getWorkspace().deleteContents();
		PollingResult pollingResult = scm.poll(project, null, first_build.getWorkspace(), listener, null);
		assertFalse(pollingResult.hasChanges());
	}

    @Issue("JENKINS-24467")
    @Test
    public void testPolling_environmentValueAsEnvironmentContributingAction() throws Exception {
        // create parameterized project with environment value in branch specification
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                Collections.singletonList(new BranchSpec("${MY_BRANCH}")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        project.setScm(scm);

        // Inital commit and build
        commit("toto/commitFile1", johnDoe, "Commit number 1");
        String brokenPath = "\\broken/path\\of/doom";
        if (!sampleRepo.gitVersionAtLeast(1, 8)) {
            /* Git 1.7.10.4 fails the first build unless the git-upload-pack
             * program is available in its PATH.
             * Later versions of git don't have that problem.
             */
            final String systemPath = System.getenv("PATH");
            brokenPath = systemPath + File.pathSeparator + brokenPath;
        }
        final StringParameterValue real_param = new StringParameterValue("MY_BRANCH", "master");
        final StringParameterValue fake_param = new StringParameterValue("PATH", brokenPath);

        final Action[] actions = {new ParametersAction(real_param), new FakeParametersAction(fake_param)};

        // SECURITY-170 - have to use ParametersDefinitionProperty
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MY_BRANCH", "master")));

        FreeStyleBuild first_build = project.scheduleBuild2(0, new Cause.UserCause(), actions).get();
        rule.assertBuildStatus(Result.SUCCESS, first_build);

        Launcher launcher = workspace.createLauncher(listener);
        final EnvVars environment = GitUtils.getPollEnvironment(project, workspace, launcher, listener);

        assertEquals(environment.get("MY_BRANCH"), "master");
        assertNotSame("Enviroment path should not be broken path", environment.get("PATH"), brokenPath);
    }

    /**
     * Tests that builds have the correctly specified Custom SCM names, associated with each build.
     * @throws Exception on error
     */
    // Flaky test distracting from primary goal
    // @Test
    public void testCustomSCMName() throws Exception {
        final String branchName = "master";
        final FreeStyleProject project = setupProject(branchName, false);
        project.addTrigger(new SCMTrigger(""));
        GitSCM git = (GitSCM) project.getScm();
        setupJGit(git);

        final String commitFile1 = "commitFile1";
        final String scmNameString1 = "";
        commit(commitFile1, johnDoe, "Commit number 1");
        assertTrue("scm polling should not detect any more changes after build",
                project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile1);
        final ObjectId commit1 = testRepo.git.revListAll().get(0);

        // Check unset build SCM Name carries
        final int buildNumber1 = notifyAndCheckScmName(
            project, commit1, scmNameString1, 1, git);

        final String scmNameString2 = "ScmName2";
        git.getExtensions().replace(new ScmName(scmNameString2));

        commit("commitFile2", johnDoe, "Commit number 2");
        assertTrue("scm polling should detect commit 2 (commit1=" + commit1 + ")", project.poll(listener).hasChanges());
        final ObjectId commit2 = testRepo.git.revListAll().get(0);

        // Check second set SCM Name
        final int buildNumber2 = notifyAndCheckScmName(
            project, commit2, scmNameString2, 2, git, commit1);
        checkNumberedBuildScmName(project, buildNumber1, scmNameString1, git);

        final String scmNameString3 = "ScmName3";
        git.getExtensions().replace(new ScmName(scmNameString3));

        commit("commitFile3", johnDoe, "Commit number 3");
        assertTrue("scm polling should detect commit 3, (commit2=" + commit2 + ",commit1=" + commit1 + ")", project.poll(listener).hasChanges());
        final ObjectId commit3 = testRepo.git.revListAll().get(0);

        // Check third set SCM Name
        final int buildNumber3 = notifyAndCheckScmName(
            project, commit3, scmNameString3, 3, git, commit2, commit1);
        checkNumberedBuildScmName(project, buildNumber1, scmNameString1, git);
        checkNumberedBuildScmName(project, buildNumber2, scmNameString2, git);

        commit("commitFile4", johnDoe, "Commit number 4");
        assertTrue("scm polling should detect commit 4 (commit3=" + commit3 + ",commit2=" + commit2 + ",commit1=" + commit1 + ")", project.poll(listener).hasChanges());
        final ObjectId commit4 = testRepo.git.revListAll().get(0);

        // Check third set SCM Name still set
        final int buildNumber4 = notifyAndCheckScmName(
            project, commit4, scmNameString3, 4, git, commit3, commit2, commit1);
        checkNumberedBuildScmName(project, buildNumber1, scmNameString1, git);
        checkNumberedBuildScmName(project, buildNumber2, scmNameString2, git);
        checkNumberedBuildScmName(project, buildNumber3, scmNameString3, git);
    }

    /**
     * Method performs HTTP get on "notifyCommit" URL, passing it commit by SHA1
     * and tests for custom SCM name build data consistency.
     * @param project project to build
     * @param commit commit to build
     * @param expectedScmName Expected SCM name for commit.
     * @param ordinal number of commit to log into errors, if any
     * @param git git SCM
     * @throws Exception on error
     */
    private int notifyAndCheckScmName(FreeStyleProject project, ObjectId commit,
            String expectedScmName, int ordinal, GitSCM git, ObjectId... priorCommits) throws Exception {
        String priorCommitIDs = "";
        for (ObjectId priorCommit : priorCommits) {
            priorCommitIDs = priorCommitIDs + " " + priorCommit;
        }
        assertTrue("scm polling should detect commit " + ordinal, notifyCommit(project, commit));

        final Build build = project.getLastBuild();
        final BuildData buildData = git.getBuildData(build);
        assertEquals("Expected SHA1 != built SHA1 for commit " + ordinal + " priors:" + priorCommitIDs, commit, buildData
                .getLastBuiltRevision().getSha1());
        assertEquals("Expected SHA1 != retrieved SHA1 for commit " + ordinal + " priors:" + priorCommitIDs, commit, buildData.getLastBuild(commit).getSHA1());
        assertTrue("Commit " + ordinal + " not marked as built", buildData.hasBeenBuilt(commit));

        assertEquals("Wrong SCM Name for commit " + ordinal, expectedScmName, buildData.getScmName());

        return build.getNumber();
    }

    private void checkNumberedBuildScmName(FreeStyleProject project, int buildNumber,
            String expectedScmName, GitSCM git) throws Exception {

        final BuildData buildData = git.getBuildData(project.getBuildByNumber(buildNumber));
        assertEquals("Wrong SCM Name", expectedScmName, buildData.getScmName());
    }

    /*
     * Tests that builds have the correctly specified branches, associated with
     * the commit id, passed with "notifyCommit" URL.
     */
    @Issue("JENKINS-24133")
    // Flaky test distracting from primary focus
    // @Test
    public void testSha1NotificationBranches() throws Exception {
        final String branchName = "master";
        final FreeStyleProject project = setupProject(branchName, false);
        project.addTrigger(new SCMTrigger(""));
        final GitSCM git = (GitSCM) project.getScm();
        setupJGit(git);

        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit number 1");
        assertTrue("scm polling should detect commit 1",
                project.poll(listener).hasChanges());
        build(project, Result.SUCCESS, commitFile1);
        final ObjectId commit1 = testRepo.git.revListAll().get(0);
        notifyAndCheckBranch(project, commit1, branchName, 1, git);

        commit("commitFile2", johnDoe, "Commit number 2");
        assertTrue("scm polling should detect commit 2", project.poll(listener).hasChanges());
        final ObjectId commit2 = testRepo.git.revListAll().get(0);
        notifyAndCheckBranch(project, commit2, branchName, 2, git);

        notifyAndCheckBranch(project, commit1, branchName, 1, git);
    }

    /* A null pointer exception was detected because the plugin failed to
     * write a branch name to the build data, so there was a SHA1 recorded 
     * in the build data, but no branch name.
     */
    @Test
    public void testNoNullPointerExceptionWithNullBranch() throws Exception {
        ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

        /* This is the null that causes NPE */
        Branch branch = new Branch(null, sha1);

        List<Branch> branchList = new ArrayList<>();
        branchList.add(branch);

        Revision revision = new Revision(sha1, branchList);

        /* BuildData mock that will use the Revision with null branch name */
        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
        Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

        /* List of build data that will be returned by the mocked BuildData */
        List<BuildData> buildDataList = new ArrayList<>();
        buildDataList.add(buildData);

        /* AbstractBuild mock which returns the buildDataList that contains a null branch name */
        AbstractBuild build = Mockito.mock(AbstractBuild.class);
        Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

        final FreeStyleProject project = setupProject("*/*", false);
        GitSCM scm = (GitSCM) project.getScm();
        scm.buildEnvVars(build, new EnvVars()); // NPE here before fix applied

        /* Verify mocks were called as expected */
        verify(buildData, times(1)).getLastBuiltRevision();
        verify(buildData, times(1)).hasBeenReferenced(anyString());
        verify(build, times(1)).getActions(BuildData.class);
    }

    @Test
    public void testBuildEnvVarsLocalBranchStarStar() throws Exception {
       ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

       /* This is the null that causes NPE */
       Branch branch = new Branch("origin/master", sha1);

       List<Branch> branchList = new ArrayList<>();
       branchList.add(branch);

       Revision revision = new Revision(sha1, branchList);

       /* BuildData mock that will use the Revision with null branch name */
       BuildData buildData = Mockito.mock(BuildData.class);
       Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
       Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

       /* List of build data that will be returned by the mocked BuildData */
       List<BuildData> buildDataList = new ArrayList<>();
       buildDataList.add(buildData);

       /* AbstractBuild mock which returns the buildDataList that contains a null branch name */
       AbstractBuild build = Mockito.mock(AbstractBuild.class);
       Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

       final FreeStyleProject project = setupProject("*/*", false);
       GitSCM scm = (GitSCM) project.getScm();
       scm.getExtensions().add(new LocalBranch("**"));

       EnvVars env = new EnvVars();
       scm.buildEnvVars(build, env); // NPE here before fix applied
       
       assertEquals("GIT_BRANCH", "origin/master", env.get("GIT_BRANCH"));
       assertEquals("GIT_LOCAL_BRANCH", "master", env.get("GIT_LOCAL_BRANCH"));

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    @Test
    public void testBuildEnvVarsLocalBranchNull() throws Exception {
       ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

       /* This is the null that causes NPE */
       Branch branch = new Branch("origin/master", sha1);

       List<Branch> branchList = new ArrayList<>();
       branchList.add(branch);

       Revision revision = new Revision(sha1, branchList);

       /* BuildData mock that will use the Revision with null branch name */
       BuildData buildData = Mockito.mock(BuildData.class);
       Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
       Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

       /* List of build data that will be returned by the mocked BuildData */
       List<BuildData> buildDataList = new ArrayList<>();
       buildDataList.add(buildData);

       /* AbstractBuild mock which returns the buildDataList that contains a null branch name */
       AbstractBuild build = Mockito.mock(AbstractBuild.class);
       Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

       final FreeStyleProject project = setupProject("*/*", false);
       GitSCM scm = (GitSCM) project.getScm();
       scm.getExtensions().add(new LocalBranch(""));

       EnvVars env = new EnvVars();
       scm.buildEnvVars(build, env); // NPE here before fix applied
       
       assertEquals("GIT_BRANCH", "origin/master", env.get("GIT_BRANCH"));
       assertEquals("GIT_LOCAL_BRANCH", "master", env.get("GIT_LOCAL_BRANCH"));

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    @Test
    public void testBuildEnvVarsLocalBranchNotSet() throws Exception {
       ObjectId sha1 = ObjectId.fromString("2cec153f34767f7638378735dc2b907ed251a67d");

       /* This is the null that causes NPE */
       Branch branch = new Branch("origin/master", sha1);

       List<Branch> branchList = new ArrayList<>();
       branchList.add(branch);

       Revision revision = new Revision(sha1, branchList);

       /* BuildData mock that will use the Revision with null branch name */
       BuildData buildData = Mockito.mock(BuildData.class);
       Mockito.when(buildData.getLastBuiltRevision()).thenReturn(revision);
       Mockito.when(buildData.hasBeenReferenced(anyString())).thenReturn(true);

       /* List of build data that will be returned by the mocked BuildData */
       List<BuildData> buildDataList = new ArrayList<>();
       buildDataList.add(buildData);

       /* AbstractBuild mock which returns the buildDataList that contains a null branch name */
       AbstractBuild build = Mockito.mock(AbstractBuild.class);
       Mockito.when(build.getActions(BuildData.class)).thenReturn(buildDataList);

       final FreeStyleProject project = setupProject("*/*", false);
       GitSCM scm = (GitSCM) project.getScm();

       EnvVars env = new EnvVars();
       scm.buildEnvVars(build, env); // NPE here before fix applied
       
       assertEquals("GIT_BRANCH", "origin/master", env.get("GIT_BRANCH"));
       assertEquals("GIT_LOCAL_BRANCH", null, env.get("GIT_LOCAL_BRANCH"));

       /* Verify mocks were called as expected */
       verify(buildData, times(1)).getLastBuiltRevision();
       verify(buildData, times(1)).hasBeenReferenced(anyString());
       verify(build, times(1)).getActions(BuildData.class);
    }

    @Issue("JENKINS-38241")
    @Test
    public void testCommitMessageIsPrintedToLogs() throws Exception {
        sampleRepo.init();
        sampleRepo.write("file", "v1");
        sampleRepo.git("commit", "--all", "--message=test commit");
        FreeStyleProject p = setupSimpleProject("master");
        Run<?,?> run = rule.buildAndAssertSuccess(p);
        TaskListener mockListener = Mockito.mock(TaskListener.class);
        Mockito.when(mockListener.getLogger()).thenReturn(Mockito.spy(StreamTaskListener.fromStdout().getLogger()));

        p.getScm().checkout(run, new Launcher.LocalLauncher(listener),
                new FilePath(run.getRootDir()).child("tmp-" + "master"),
                mockListener, null, SCMRevisionState.NONE);

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockListener.getLogger(), atLeastOnce()).println(logCaptor.capture());
        List<String> values = logCaptor.getAllValues();
        assertThat(values, hasItem("Commit message: \"test commit\""));
    }

    /**
     * Method performs HTTP get on "notifyCommit" URL, passing it commit by SHA1
     * and tests for build data consistency.
     * @param project project to build
     * @param commit commit to build
     * @param expectedBranch branch, that is expected to be built
     * @param ordinal number of commit to log into errors, if any
     * @param git git SCM
     * @throws Exception on error
     */
    private void notifyAndCheckBranch(FreeStyleProject project, ObjectId commit,
            String expectedBranch, int ordinal, GitSCM git) throws Exception {
        assertTrue("scm polling should detect commit " + ordinal, notifyCommit(project, commit));
        final BuildData buildData = git.getBuildData(project.getLastBuild());
        final Collection<Branch> builtBranches = buildData.lastBuild.getRevision().getBranches();
        assertEquals("Commit " + ordinal + " should be built", commit, buildData
                .getLastBuiltRevision().getSha1());

        final String expectedBranchString = "origin/" + expectedBranch;
        assertFalse("Branches should be detected for the build", builtBranches.isEmpty());
        assertEquals(expectedBranch + " branch should be detected", expectedBranchString,
                     builtBranches.iterator().next().getName());
        assertEquals(expectedBranchString, getEnvVars(project).get(GitSCM.GIT_BRANCH));
    }

    /**
     * Method performs commit notification for the last committed SHA1 using
     * notifyCommit URL.
     * @param project project to trigger
     * @return whether the new build has been triggered (<code>true</code>) or
     *         not (<code>false</code>).
     * @throws Exception on error
     */
    private boolean notifyCommit(FreeStyleProject project, ObjectId commitId) throws Exception {
        final int initialBuildNumber = project.getLastBuild().getNumber();
        final String commit1 = ObjectId.toString(commitId);

        final String notificationPath = rule.getURL().toExternalForm()
                + "git/notifyCommit?url=" + testRepo.gitDir.toString() + "&sha1=" + commit1;
        final URL notifyUrl = new URL(notificationPath);
        String notifyContent = null;
        try (final InputStream is = notifyUrl.openStream()) {
            notifyContent = IOUtils.toString(is);
        }
        assertThat(notifyContent, containsString("No Git consumers using SCM API plugin for: " + testRepo.gitDir.toString()));

        if ((project.getLastBuild().getNumber() == initialBuildNumber)
                && (rule.jenkins.getQueue().isEmpty())) {
            return false;
        } else {
            while (!rule.jenkins.getQueue().isEmpty()) {
                Thread.sleep(100);
            }
            final FreeStyleBuild build = project.getLastBuild();
            while (build.isBuilding()) {
                Thread.sleep(100);
            }
            return true;
        }
    }

    private void setupJGit(GitSCM git) {
        git.gitTool="jgit";
        rule.jenkins.getDescriptorByType(GitTool.DescriptorImpl.class).setInstallations(new JGitTool(Collections.<ToolProperty<?>>emptyList()));
    }

    /** We clean the environment, just in case the test is being run from a Jenkins job using this same plugin :). */
    @TestExtension
    public static class CleanEnvironment extends EnvironmentContributor {
        @Override
        public void buildEnvironmentFor(Run run, EnvVars envs, TaskListener listener) {
            envs.remove(GitSCM.GIT_BRANCH);
            envs.remove(GitSCM.GIT_LOCAL_BRANCH);
            envs.remove(GitSCM.GIT_COMMIT);
            envs.remove(GitSCM.GIT_PREVIOUS_COMMIT);
            envs.remove(GitSCM.GIT_PREVIOUS_SUCCESSFUL_COMMIT);
        }
    }

}
