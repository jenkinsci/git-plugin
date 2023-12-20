package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import hudson.plugins.git.util.BuildData;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author dalvizu
 */
public class PreBuildMergeTest extends GitSCMExtensionTest
{
    private FreeStyleProject project;

    private TestGitRepo repo;

    private String MASTER_FILE = "commitFileBase";
    private String INTEGRATION_FILE = "branchFile";

    private String initCommit;

    public void before() throws Exception {
        repo = new TestGitRepo("repo", tmp.newFolder(), listener);
        project = setupBasicProject(repo);
        // make an initial commit to master
        initCommit = repo.commit(MASTER_FILE, repo.johnDoe, "Initial Commit");
        // create integration branch
        repo.git.branch("integration");
    }

    @Test
    public void testBasicPreMerge() throws Exception {
        // already existing test
        FreeStyleBuild firstBuild = build(project, Result.SUCCESS);
    }

    @Test
    public void testDisplayMergedRevision() throws Exception {
        // add some commits to source and target branch
        repo.git.checkoutBranch("integration", initCommit);
        String integrationCommit = repo.commit(INTEGRATION_FILE, repo.johnDoe, "Integration Commit");
        repo.git.checkoutBranch("master", initCommit);
        String masterCommit = repo.commit("Master2", repo.johnDoe, "Master2 Commit");

        FreeStyleBuild firstBuild = build(project, Result.SUCCESS);

        // git repository placed in build's workspace
        TestGitRepo repoInWorkspace = getRepoInWorkspace(firstBuild);
        
        List<ObjectId> commitsInWorkspace = getCommitsOnHead(repoInWorkspace);

        ObjectId mergeCommit = commitsInWorkspace.remove(0);

        // verify state of workspace fit repo
        // is it possible to verify merge commit (checking log message?)
        assertEquals(Arrays.asList(integrationCommit, masterCommit, initCommit),
                commitsInWorkspace.stream().map(AnyObjectId::name).collect(Collectors.toList()));

        // verify revision displayed
        assertEquals(GitSCM.class, project.getScm().getClass());
        GitSCM gitSCM = (GitSCM)project.getScm();
        BuildData buildData = gitSCM.getBuildData(firstBuild);
        Revision buildRevision = buildData.getLastBuiltDisplayRevision();

        assertEquals(mergeCommit, buildRevision.getSha1());
        assertEquals(1, buildRevision.getBranches().size());
        assertTrue(buildRevision.containsBranchName("origin/integration"));
    }

    @Test
    public void testDisplaySourceRevision() throws Exception {
        // change UserMergeOption
        GitSCM gitSCM = (GitSCM)project.getScm();
        gitSCM.getExtensions().get(PreBuildMerge.class).getOptions().setDisplayRevision(UserMergeOptions.DisplayRevision.SOURCE_BRANCH);

        // add some commits to source and target branch
        repo.git.checkoutBranch("integration", initCommit);
        String integrationCommit = repo.commit(INTEGRATION_FILE, repo.johnDoe, "Integration Commit");
        repo.git.checkoutBranch("master", initCommit);
        String masterCommit = repo.commit("Master2", repo.johnDoe, "Master2 Commit");

        FreeStyleBuild firstBuild = build(project, Result.SUCCESS);

        // git repository placed in build's workspace
        TestGitRepo repoInWorkspace = getRepoInWorkspace(firstBuild);
        List<ObjectId> commitsInWorkspace = getCommitsOnHead(repoInWorkspace);

        ObjectId mergeCommit = commitsInWorkspace.remove(0);

        // verify state of workspace fit repo
        // is it possible to verify merge commit (checking log message?)
        assertEquals(Arrays.asList(integrationCommit, masterCommit, initCommit),
                commitsInWorkspace.stream().map(AnyObjectId::name).collect(Collectors.toList()));
        assertEquals(GitSCM.class, project.getScm().getClass());

        // verify revision displayed
        BuildData buildData = gitSCM.getBuildData(firstBuild);
        Revision buildRevision = buildData.getLastBuiltDisplayRevision();

        assertEquals(masterCommit, buildRevision.getSha1().name());
        assertEquals(1, buildRevision.getBranches().size());
        assertTrue(buildRevision.containsBranchName("origin/master"));
    }

    private List<ObjectId> getCommitsOnHead(TestGitRepo repoInWorkspace) throws InterruptedException {
        return repoInWorkspace.git.revList("HEAD");
    }

    private TestGitRepo getRepoInWorkspace(FreeStyleBuild firstBuild) throws IOException, InterruptedException {
        return new TestGitRepo("workspace", new File(firstBuild.getWorkspace().toString()), listener);
    }

    @Test
    public void testFailedMerge() throws Exception {
        FreeStyleBuild firstBuild = build(project, Result.SUCCESS);
        assertEquals(GitSCM.class, project.getScm().getClass());
        GitSCM gitSCM = (GitSCM)project.getScm();
        BuildData buildData = gitSCM.getBuildData(firstBuild);
        assertNotNull("Build data not found", buildData);
        assertEquals(firstBuild.getNumber(), buildData.lastBuild.getBuildNumber());
        Revision firstMarked = buildData.lastBuild.getMarked();
        Revision firstRevision = buildData.lastBuild.getRevision();
        assertNotNull(firstMarked);
        assertNotNull(firstRevision);

        // pretend we merged and published it successfully
        repo.git.deleteBranch("integration");
        repo.git.checkoutBranch("integration", "master");
        repo.commit(MASTER_FILE, "new content on integration branch", repo.johnDoe, repo.johnDoe, "Commit which should fail!");
        repo.git.checkout().ref("master").execute();

        // make a new commit in master branch, this commit should not merge cleanly!
        assertFalse("SCM polling should not detect any more changes after build", project.poll(listener).hasChanges());
        String conflictSha1 = repo.commit(MASTER_FILE, "new content - expect a merge conflict!", repo.johnDoe, repo.johnDoe, "Commit which should fail!");
        assertTrue("SCM polling should detect changes", project.poll(listener).hasChanges());

        FreeStyleBuild secondBuild = build(project, Result.FAILURE);
        assertEquals(secondBuild.getNumber(), gitSCM.getBuildData(secondBuild).lastBuild.getBuildNumber());
        // buildData should mark this as built
        assertEquals(conflictSha1, gitSCM.getBuildData(secondBuild).lastBuild.getMarked().getSha1String());
        assertEquals(conflictSha1, gitSCM.getBuildData(secondBuild).lastBuild.getRevision().getSha1String());

        // Check to see that build data is not corrupted (JENKINS-44037)
        assertEquals(firstBuild.getNumber(), gitSCM.getBuildData(firstBuild).lastBuild.getBuildNumber());
        assertEquals(firstMarked, gitSCM.getBuildData(firstBuild).lastBuild.getMarked());
        assertEquals(firstRevision, gitSCM.getBuildData(firstBuild).lastBuild.getRevision());
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(PreBuildMerge.class)
                .usingGetClass()
                .verify();
    }

    @Override
    protected GitSCMExtension getExtension() {
        return new PreBuildMerge(new UserMergeOptions("origin", "integration", "default",
                MergeCommand.GitPluginFastForwardMode.FF));
    }

}
