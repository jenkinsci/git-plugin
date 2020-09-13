package hudson.plugins.git.util;

import hudson.plugins.git.AbstractGitRepository;
import hudson.plugins.git.Branch;
import java.util.Collection;
import java.util.HashSet;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import org.eclipse.jgit.lib.ObjectId;
import static org.junit.Assert.*;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Arnout Engelen
 */
public class DefaultBuildChooserTest extends AbstractGitRepository {
    @Test
    public void testChooseGitRevisionToBuildByShaHash() throws Exception {
        testGitClient.commit("Commit 1");
        String shaHashCommit1 = testGitClient.getBranches().iterator().next().getSHA1String();
        testGitClient.commit("Commit 2");
        String shaHashCommit2 = testGitClient.getBranches().iterator().next().getSHA1String();
        assertNotSame(shaHashCommit1, shaHashCommit2);

        DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

        Collection<Revision> candidateRevisions = buildChooser.getCandidateRevisions(false, shaHashCommit1, testGitClient, null, null, null);

        assertEquals(1, candidateRevisions.size());
        assertEquals(shaHashCommit1, candidateRevisions.iterator().next().getSha1String());

        candidateRevisions = buildChooser.getCandidateRevisions(false, "aaa" + shaHashCommit1.substring(3), testGitClient, null, null, null);
        assertTrue(candidateRevisions.isEmpty());
    }

    /* RegExp patterns prefixed with : should pass through to DefaultBuildChooser.getAdvancedCandidateRevisions */
    @Test
    public void testIsAdvancedSpec() throws Exception {
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
	public void testPrefereRemoteBranchInCandidateRevisionsWithWrongOrderInHashSet() throws Exception {
		String branchName = "feature/42";
		String localRef = "refs/heads/" + branchName;
		String remoteRef = "refs/remotes/origin/" + branchName;
		createRefsWithPredefinedOrderInHashSet(localRef, remoteRef);
		DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

		Collection<Revision> candidateRevisions =
				buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

		assertEquals(2, candidateRevisions.size());
		Revision firstCandidateRevision = candidateRevisions.iterator().next();
		Branch firstCandidateBranch = firstCandidateRevision.getBranches().iterator().next();
		assertEquals(remoteRef, firstCandidateBranch.getName());
	}

	/* was successful also before fix */
    @Issue("JENKINS-37263")
	@Test
	public void testPrefereRemoteBranchInCandidateRevisionsWithCorrectOrderInHashSet() throws Exception {
		String branchName = "feature/42";
		String localRef = "refs/heads/" + branchName;
		String remoteRef = "refs/remotes/origin/" + branchName;
		createRefsWithPredefinedOrderInHashSet(remoteRef, localRef);
		DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

		Collection<Revision> candidateRevisions =
				buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

		assertEquals(2, candidateRevisions.size());
		Revision firstCandidateRevision = candidateRevisions.iterator().next();
		Branch firstCandidateBranch = firstCandidateRevision.getBranches().iterator().next();
		assertEquals(remoteRef, firstCandidateBranch.getName());
	}

    /* was successful also before fix */
    @Issue("JENKINS-37263")
	@Test
	public void testSingleCandidateRevisionWithLocalAndRemoteRefsOnSameCommit() throws Exception {
		String branchName = "feature/42";
		String localRef = "refs/heads/" + branchName;
		String remoteRef = "refs/remotes/origin/" + branchName;
		testGitClient.ref(localRef);
		testGitClient.ref(remoteRef);
		DefaultBuildChooser buildChooser = (DefaultBuildChooser) new GitSCM("foo").getBuildChooser();

		Collection<Revision> candidateRevisions =
				buildChooser.getCandidateRevisions(false, branchName, testGitClient, null, null, null);

		assertEquals(1, candidateRevisions.size());
	}

	private void createRefsWithPredefinedOrderInHashSet(String ref1, String ref2) throws InterruptedException {
		ObjectId commit1 = testGitClient.revParse("HEAD");
		testGitClient.ref(ref1);
		testGitClient.commit("Commit");
		ObjectId commit2 = testGitClient.revParse("HEAD");
		HashSet<Revision> set = new HashSet<Revision>();
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
