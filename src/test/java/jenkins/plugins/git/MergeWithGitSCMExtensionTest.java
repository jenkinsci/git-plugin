package jenkins.plugins.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.*;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.plugins.git.util.BuildData;
import junit.framework.TestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.eclipse.jgit.lib.Constants;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class MergeWithGitSCMExtensionTest extends GitSCMExtensionTest {

    private FreeStyleProject project;

    private TestGitRepo repo;
    private String baseName;
    private String baseHash;
    private String MASTER_FILE = "commitFileBase";

    public void before() throws Exception {
        repo = new TestGitRepo("repo", tmp.newFolder(), listener);
        // make an initial commit to master and get hash
        this.baseHash=repo.commit(MASTER_FILE, repo.johnDoe, "Initial Commit");
        // set the base name as HEAD
        this.baseName=Constants.MASTER;
        project = setupBasicProject(repo);
        // create integration branch
        repo.git.branch("integration");

    }
    @Test
    public void testBasicMergeWithSCMExtension() throws Exception {
        FreeStyleBuild baseBuild = build(project, Result.SUCCESS);
    }

    @Test
    public void testFailedMergeWithSCMExtension() throws Exception {
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

        // delete integration branch successfully and commit successfully
        repo.git.deleteBranch("integration");
        repo.git.checkoutBranch("integration", "master");
        this.baseName = Constants.HEAD;
        this.baseHash = repo.git.revParse(baseName).name();
        repo.commit(MASTER_FILE, "new content on integration branch", repo.johnDoe, repo.johnDoe, "Commit success!");
        repo.git.checkout().ref("master").execute();

        // as baseName and baseHash don't change in master branch, this commit should  merge !
        assertFalse("SCM polling should not detect any more changes after build", project.poll(listener).hasChanges());
        String conflictSha1= repo.commit(MASTER_FILE, "new content ", repo.johnDoe, repo.johnDoe, "Commit success!");
        assertTrue("SCM polling should detect changes", project.poll(listener).hasChanges());


        FreeStyleBuild secondBuild = build(project, Result.SUCCESS);
        assertEquals(secondBuild.getNumber(), gitSCM.getBuildData(secondBuild).lastBuild.getBuildNumber());
        // buildData should mark this as built
        assertEquals(conflictSha1, gitSCM.getBuildData(secondBuild).lastBuild.getMarked().getSha1String());
        assertEquals(conflictSha1, gitSCM.getBuildData(secondBuild).lastBuild.getRevision().getSha1String());

        // Check to see that build data is not corrupted (JENKINS-44037)
        assertEquals(firstBuild.getNumber(), gitSCM.getBuildData(firstBuild).lastBuild.getBuildNumber());
        assertEquals(firstMarked, gitSCM.getBuildData(firstBuild).lastBuild.getMarked());
        assertEquals(firstRevision, gitSCM.getBuildData(firstBuild).lastBuild.getRevision());
    }

    @Override
    protected GitSCMExtension getExtension() {
        return new MergeWithGitSCMExtension(baseName,baseHash);
    }

}
