package jenkins.plugins.git;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import hudson.plugins.git.extensions.impl.UserIdentity;
import hudson.plugins.git.util.BuildData;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MergeWithGitSCMExtensionTest extends GitSCMExtensionTest {

    private FreeStyleProject project;

    private TestGitRepo repo;
    private String baseName;
    private String baseHash;
    private String MASTER_FILE = "commitFileBase";

    @Override
    protected void before() throws Exception {
        repo = new TestGitRepo("repo", newFolder(tmp, "junit"), listener);
        // make an initial commit to master and get hash
        this.baseHash = repo.commit(MASTER_FILE, repo.johnDoe, "Initial Commit");
        // set the base name as HEAD
        this.baseName = Constants.MASTER;
        project = setupBasicProject(repo);
        // create integration branch
        repo.git.branch("integration");

    }

    @Test
    void testBasicMergeWithSCMExtension() throws Exception {
        FreeStyleBuild baseBuild = build(project, Result.SUCCESS);
    }

    @Test
    void testFailedMergeWithSCMExtension() throws Exception {
        FreeStyleBuild firstBuild = build(project, Result.SUCCESS);
        assertEquals(GitSCM.class, project.getScm().getClass());
        GitSCM gitSCM = (GitSCM)project.getScm();
        BuildData buildData = gitSCM.getBuildData(firstBuild);
        assertNotNull(buildData, "Build data not found");
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
        assertFalse(project.poll(listener).hasChanges(), "SCM polling should not detect any more changes after build");
        String conflictSha1= repo.commit(MASTER_FILE, "new John Doe content will conflict", repo.johnDoe, repo.johnDoe, "Commit success!");
        assertTrue(project.poll(listener).hasChanges(), "SCM polling should detect changes");


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

    @Test
    void testMergeCommitUsesDefaultIdentity() throws Exception {
        String integrationHash = makeDivergingBranches();
        FreeStyleProject p = createMergeProject(integrationHash);

        FreeStyleBuild build = build(p, Result.SUCCESS);

        PersonIdent author = headCommitAuthor(build.getWorkspace());
        assertEquals("Jenkins", author.getName());
        assertEquals("nobody@nowhere", author.getEmailAddress());
    }

    @Test
    void testMergeCommitUsesGlobalConfigIdentity() throws Exception {
        GitSCM.DescriptorImpl descriptor = (GitSCM.DescriptorImpl) r.jenkins.getDescriptorByType(GitSCM.DescriptorImpl.class);
        descriptor.setGlobalConfigName("CI Bot");
        descriptor.setGlobalConfigEmail("ci@example.com");

        String integrationHash = makeDivergingBranches();
        FreeStyleProject p = createMergeProject(integrationHash);

        FreeStyleBuild build = build(p, Result.SUCCESS);

        PersonIdent author = headCommitAuthor(build.getWorkspace());
        assertEquals("CI Bot", author.getName());
        assertEquals("ci@example.com", author.getEmailAddress());
    }

    @Test
    void testMergeCommitUsesUserIdentityExtension() throws Exception {
        String integrationHash = makeDivergingBranches();
        FreeStyleProject p = createMergeProject(integrationHash);
        ((GitSCM) p.getScm()).getExtensions().add(new UserIdentity("Bot User", "bot@example.com"));

        FreeStyleBuild build = build(p, Result.SUCCESS);

        PersonIdent author = headCommitAuthor(build.getWorkspace());
        assertEquals("Bot User", author.getName());
        assertEquals("bot@example.com", author.getEmailAddress());
    }

    /**
     * Commits a file on integration and a different file on master so the two branches diverge,
     * enabling a true merge commit (not a fast-forward) when they are later merged.
     *
     * @return SHA of the new integration HEAD
     */
    private String makeDivergingBranches() throws Exception {
        repo.git.checkoutBranch("integration", "master");
        repo.commit("integration-only-file", repo.janeDoe, "Integration branch commit");
        String integrationHash = repo.git.revParse(Constants.HEAD).name();
        repo.git.checkout().ref("master").execute();
        repo.commit("master-only-file", repo.johnDoe, "Master diverging commit");
        return integrationHash;
    }

    /**
     * Creates a project that builds master and merges the integration branch at the given hash.
     */
    private FreeStyleProject createMergeProject(String mergeBaseHash) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        GitSCM scm = new GitSCM(
                repo.remoteConfigs(),
                Collections.singletonList(new BranchSpec("master")),
                null, null,
                Collections.emptyList());
        scm.getExtensions().add(new MergeWithGitSCMExtension("integration", mergeBaseHash));
        p.setScm(scm);
        p.getBuildersList().add(new CaptureEnvironmentBuilder());
        return p;
    }

    private PersonIdent headCommitAuthor(FilePath workspace) throws Exception {
        File workspaceDir = new File(workspace.getRemote());
        try (Repository gitRepo = new FileRepositoryBuilder().setWorkTree(workspaceDir).build();
             RevWalk revWalk = new RevWalk(gitRepo)) {
            RevCommit headCommit = revWalk.parseCommit(gitRepo.resolve(Constants.HEAD));
            return headCommit.getAuthorIdent();
        }
    }

    @Override
    protected GitSCMExtension getExtension() {
        return new MergeWithGitSCMExtension(baseName,baseHash);
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
