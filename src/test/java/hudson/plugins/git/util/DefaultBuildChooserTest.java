package hudson.plugins.git.util;

import hudson.plugins.git.AbstractGitRepository;
import hudson.plugins.git.Branch;
import java.util.Collection;
import java.util.HashSet;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import org.junit.jupiter.api.Test;

import org.eclipse.jgit.lib.ObjectId;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jvnet.hudson.test.Issue;

/**
 * @author Arnout Engelen
 */
class DefaultBuildChooserTest extends AbstractGitRepository {

    @Test
    void testChooseGitRevisionToBuildByShaHash() throws Exception {
        testGitClient.commit("Commit 1");
        String shaHashCommit1 = testGitClient.getBranches().iterator().next().getSHA1String();
        testGitClient.commit("Commit 2");
        String shaHashCommit2 = testGitClient.getBranches().iterator().next().getSHA1String();
        assertThat(shaHashCommit1, is(not(shaHashCommit2)));

        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, shaHashCommit1, testGitClient, null, null, null);

        assertThat(candidateRevisions, hasSize(1));
        assertThat(candidateRevisions.iterator().next().getSha1String(), is(shaHashCommit1));

        candidateRevisions = buildChooser.getCandidateRevisions(false, "aaa" + shaHashCommit1.substring(3), testGitClient, null, null, null);
        assertThat(candidateRevisions, is(empty()));
    }

    /* RegExp patterns prefixed with : should pass through to DefaultBuildChooser.getAdvancedCandidateRevisions */
    @Test
    void testIsAdvancedSpec() throws Exception {
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        assertFalse(buildChooser.isAdvancedSpec("origin/master"));
        assertTrue(buildChooser.isAdvancedSpec("origin/master-*"));
        assertTrue(buildChooser.isAdvancedSpec("origin**"));
        // regexp use case
        assertTrue(buildChooser.isAdvancedSpec(":origin/master"));
        assertTrue(buildChooser.isAdvancedSpec(":origin/master-\\d{*}"));
    }

    /* always failed before fix */
    @Issue("JENKINS-37263")
    @Test
    void testPreferRemoteBranchInCandidateRevisionsWithWrongOrderInHashSet() throws Exception {
        String branchName = "feature/42";
        String localRef = "refs/heads/" + branchName;
        String remoteRef = "refs/remotes/origin/" + branchName;
        createRefsWithPredefinedOrderInHashSet(localRef, remoteRef);
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions =
            buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

        assertThat(candidateRevisions, hasSize(2));
        Revision firstCandidateRevision = candidateRevisions.iterator().next();
        Branch firstCandidateBranch = firstCandidateRevision.getBranches().iterator().next();
        assertThat(firstCandidateBranch.getName(), is(remoteRef));
    }

    /* was successful also before fix */
    @Issue("JENKINS-37263")
    @Test
    void testPreferRemoteBranchInCandidateRevisionsWithCorrectOrderInHashSet() throws Exception {
        String branchName = "feature/42";
        String localRef = "refs/heads/" + branchName;
        String remoteRef = "refs/remotes/origin/" + branchName;
        createRefsWithPredefinedOrderInHashSet(remoteRef, localRef);
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions =
            buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

        assertThat(candidateRevisions, hasSize(2));
        Revision firstCandidateRevision = candidateRevisions.iterator().next();
        Branch firstCandidateBranch = firstCandidateRevision.getBranches().iterator().next();
        assertThat(firstCandidateBranch.getName(), is(remoteRef));
    }

    /* was successful also before fix */
    @Issue("JENKINS-37263")
    @Test
    void testSingleCandidateRevisionWithLocalAndRemoteRefsOnSameCommit() throws Exception {
        String branchName = "feature/42";
        String localRef = "refs/heads/" + branchName;
        String remoteRef = "refs/remotes/origin/" + branchName;
        testGitClient.ref(localRef);
        testGitClient.ref(remoteRef);
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions =
            buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

        assertThat(candidateRevisions, hasSize(1));
    }

    /* was successful also before fix */
    @Issue("JENKINS-37263")
    @Test
    void testSingleCandidateRevisionWithLocalAndRemoteRefsOnSameCommitWithOriginPrefix() throws Exception {
        String baseBranchName = "feature/42";
        String branchName = "origin/" + baseBranchName;
        String localRef = "refs/heads/" + baseBranchName;
        String remoteRef = "refs/remotes/" + branchName;
        createRefsWithPredefinedOrderInHashSet(localRef, remoteRef);
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions
                = buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

        assertThat(candidateRevisions, hasSize(1));
    }

    /* was successful also before fix */
    @Issue("JENKINS-37263")
    @Test
    void testSingleCandidateRevisionWithLocalAndRemoteRefsOnSameCommitWithRemotesOriginPrefix() throws Exception {
        String baseBranchName = "feature/42";
        String branchName = "remotes/origin/" + baseBranchName;
        String localRef = "refs/heads/" + baseBranchName;
        String remoteRef = "refs/" + branchName;
        createRefsWithPredefinedOrderInHashSet(localRef, remoteRef);
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions =
            buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

        assertThat(candidateRevisions, hasSize(1));
    }

    /* was successful also before fix */
    @Issue("JENKINS-37263")
    @Test
    void testSingleCandidateRevisionWithLocalAndRemoteRefsOnSameCommitWithRefsHeadsPrefix() throws Exception {
        String baseBranchName = "feature/42";
        String branchName = "refs/heads/" + baseBranchName;
        String localRef = "refs/heads/" + baseBranchName;
        String remoteRef = "refs/remotes/origin/" + baseBranchName;
        createRefsWithPredefinedOrderInHashSet(localRef, remoteRef);
        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions =
            buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

        assertThat(candidateRevisions, hasSize(1));
    }

    private void createRefsWithPredefinedOrderInHashSet(String ref1, String ref2) throws Exception {
        ObjectId commit1 = testGitClient.revParse("HEAD");
        testGitClient.ref(ref1);
        testGitClient.commit("Commit");
        ObjectId commit2 = testGitClient.revParse("HEAD");
        HashSet<Revision> set = new HashSet<>();
        set.add(new Revision(commit1));
        set.add(new Revision(commit2));
        if (set.iterator().next().getSha1().equals(commit1)) {
            // order in HashSet: commit1, commit2
            // ref1 -> commit1, ref2 -> commit2
            testGitClient.ref(ref2);
        } else {
            // order in HashSet: commit2, commit1
            // ref1 -> commit2, ref2 -> commit1
            testGitClient.ref(ref1);
            testGitClient.checkout().ref(commit1.getName()).execute();
            testGitClient.ref(ref2);
        }
    }
}
