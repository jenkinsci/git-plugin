package hudson.plugins.git.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.AbstractGitRepository;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;

public class AncestryBuildChooserTest extends AbstractGitRepository {

    private String rootCommit = null;
    private String ancestorCommit = null;
    private String fiveDaysAgoCommit = null;
    private String tenDaysAgoCommit = null;
    private String twentyDaysAgoCommit = null;

    private final DateTime fiveDaysAgo = new LocalDate().toDateTimeAtStartOfDay().minusDays(5);
    private final DateTime tenDaysAgo = new LocalDate().toDateTimeAtStartOfDay().minusDays(10);
    private final DateTime twentyDaysAgo = new LocalDate().toDateTimeAtStartOfDay().minusDays(20);
    
    private final PersonIdent johnDoe = new PersonIdent("John Doe", "john@example.com");
    
    private static final String DEFAULT_PRIORITIZED_BRANCHES = "";
    
    private static final BuildChooser DEFAULT_BUILD_CHOOSER = new DefaultBuildChooser();
    private static final BuildChooser INVERSE_BUILD_CHOOSER = new InverseBuildChooser();
    

    /*
     * 20 days old ->  O O    <- 10 days old
     *                 |/
     *    ancestor ->  O   O  <- 5 days old 
     *                  \ /
     *        root ->    O
     * 
     * Creates a small repository of 5 commits with different branches and ages.
     */
    @Before
    public void setUp() throws Exception {
        Set<String> prevBranches = stringifyBranches(testGitClient.getBranches());

        testGitClient.commit("Root Commit");
        rootCommit = getLastCommitSha1(prevBranches);

        testGitClient.commit("Ancestor Commit");
        ancestorCommit = getLastCommitSha1(prevBranches);

        testGitClient.branch("20-days-old-branch");
        testGitClient.checkoutBranch("20-days-old-branch", ancestorCommit);
        this.commit("20 days ago commit message", new PersonIdent(johnDoe, twentyDaysAgo.toDate()), new PersonIdent(johnDoe, twentyDaysAgo.toDate()));
        twentyDaysAgoCommit = getLastCommitSha1(prevBranches);

        testGitClient.checkout().ref(ancestorCommit).execute();
        testGitClient.checkoutBranch("10-days-old-branch", ancestorCommit);
        this.commit("10 days ago commit message", new PersonIdent(johnDoe, tenDaysAgo.toDate()), new PersonIdent(johnDoe, tenDaysAgo.toDate()));
        tenDaysAgoCommit = getLastCommitSha1(prevBranches);

        // create a RELEASE branch for tenDaysAgoCommit
        testGitClient.checkout().ref(tenDaysAgoCommit).execute();
        testGitClient.checkoutBranch("RELEASE/10-days-old-branch", tenDaysAgoCommit);

        testGitClient.checkout().ref(rootCommit).execute();
        testGitClient.checkoutBranch("5-days-old-branch", rootCommit);
        this.commit("5 days ago commit message", new PersonIdent(johnDoe, fiveDaysAgo.toDate()), new PersonIdent(johnDoe, fiveDaysAgo.toDate()));
        fiveDaysAgoCommit = getLastCommitSha1(prevBranches);

        // create a CRITICAL and RELEASE branch for fiveDaysAgoCommit
        testGitClient.checkout().ref(fiveDaysAgoCommit).execute();
        testGitClient.checkoutBranch("CRITICAL/RELEASE/5-days-old-branch", fiveDaysAgoCommit);
    }

    private Set<String> stringifyBranches(Set<Branch> original) {
        Set<String> result = new TreeSet<String>();

        for (Iterator<Branch> iter = original.iterator(); iter.hasNext();) {
            result.add(iter.next().getSHA1String());
        }

        return result;
    }

    private String getLastCommitSha1(Set<String> prevBranches) throws Exception {
        Set<String> newBranches = stringifyBranches(testGitClient.getBranches());

        SetView<String> difference = Sets.difference(newBranches, prevBranches);

        assertEquals(1, difference.size());

        String result = difference.iterator().next();

        prevBranches.clear();
        prevBranches.addAll(newBranches);

        return result;
    }

    // Git Client implementation throws away committer date info so we have to do this manually..
    // Copied from JGitAPIImpl.commit(String message)
    private void commit(String message, PersonIdent author, PersonIdent committer) {
        Repository repo = null;
        try {
            repo = testGitClient.getRepository();
            CommitCommand cmd = Git.wrap(repo).commit().setMessage(message);
            if (author != null)
                cmd.setAuthor(author);
            if (committer != null)
                // cmd.setCommitter(new PersonIdent(committer,new Date()));
                cmd.setCommitter(committer);
            cmd.call();
        } catch (GitAPIException e) {
            throw new GitException(e);
        } finally {
            if (repo != null)
                repo.close();
        }
    }

    private List<String> getTestCandidates(Integer maxAgeInDays, String ancestorCommitSha1) throws Exception {
        return getTestCandidates(DEFAULT_BUILD_CHOOSER, maxAgeInDays, ancestorCommitSha1);
    }

    private List<String> getTestCandidates(BuildChooser buildChooser, Integer maxAgeInDays, String ancestorCommitSha1) throws Exception {
        return getTestCandidates(buildChooser, maxAgeInDays, ancestorCommitSha1, DEFAULT_PRIORITIZED_BRANCHES);
    }

    private List<String> getTestCandidates(BuildChooser buildChooser, Integer maxAgeInDays, String ancestorCommitSha1, String prioritizedBranches) throws Exception {
        GitSCM gitSCM = new GitSCM("foo");
        AncestryBuildChooser chooser = new AncestryBuildChooser(buildChooser, maxAgeInDays, ancestorCommitSha1, prioritizedBranches);
        gitSCM.getExtensions().add(new BuildChooserSetting(chooser));
        assertEquals(maxAgeInDays, chooser.getMaximumAgeInDays());
        assertEquals(ancestorCommitSha1, chooser.getAncestorCommitSha1());

        // mock necessary objects
        GitClient git = Mockito.spy(this.testGitClient);
        Mockito.when(git.getRemoteBranches()).thenReturn(this.testGitClient.getBranches());

        BuildData buildData = Mockito.mock(BuildData.class);
        Mockito.when(buildData.hasBeenBuilt(git.revParse(rootCommit))).thenReturn(false);

        BuildChooserContext context = Mockito.mock(BuildChooserContext.class);
        Mockito.when(context.getEnvironment()).thenReturn(new EnvVars());

        TaskListener listener = TaskListener.NULL;

        // get filtered candidates
        Collection<Revision> candidateRevisions = gitSCM.getBuildChooser().getCandidateRevisions(true, "**-days-old-branch", git, listener, buildData, context);

        // transform revision candidates to sha1 strings
        List<String> candidateSha1s = Lists.newArrayList(Iterables.transform(candidateRevisions, new Function<Revision, String>() {
            public String apply(Revision rev) {
                return rev.getSha1String();
            }
        }));

        return candidateSha1s;
    }

    @Test
    public void testFilterRevisionsNoRestriction() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = null;

        List<String> candidateSha1s = getTestCandidates(maxAgeInDays, ancestorCommitSha1);

        assertEquals(3, candidateSha1s.size());
        assertTrue(candidateSha1s.contains(fiveDaysAgoCommit));
        assertTrue(candidateSha1s.contains(tenDaysAgoCommit));
        assertTrue(candidateSha1s.contains(twentyDaysAgoCommit));
    }

    @Test
    public void testFilterRevisionsZeroDate() throws Exception {
        final Integer maxAgeInDays = 0;
        final String ancestorCommitSha1 = null;

        List<String> candidateSha1s = getTestCandidates(maxAgeInDays, ancestorCommitSha1);

        assertEquals(0, candidateSha1s.size());
    }

    @Test
    public void testFilterRevisionsTenDays() throws Exception {
        final Integer maxAgeInDays = 10;
        final String ancestorCommitSha1 = null;

        List<String> candidateSha1s = getTestCandidates(maxAgeInDays, ancestorCommitSha1);

        assertEquals(1, candidateSha1s.size());
        assertTrue(candidateSha1s.contains(fiveDaysAgoCommit));
    }

    @Test
    public void testFilterRevisionsThirtyDays() throws Exception {
        final Integer maxAgeInDays = 30;
        final String ancestorCommitSha1 = null;

        List<String> candidateSha1s = getTestCandidates(maxAgeInDays, ancestorCommitSha1);

        assertEquals(3, candidateSha1s.size());
        assertTrue(candidateSha1s.contains(fiveDaysAgoCommit));
        assertTrue(candidateSha1s.contains(tenDaysAgoCommit));
        assertTrue(candidateSha1s.contains(twentyDaysAgoCommit));
    }

    @Test
    public void testFilterRevisionsBlankAncestor() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = "";

        List<String> candidateSha1s = getTestCandidates(maxAgeInDays, ancestorCommitSha1);

        assertEquals(3, candidateSha1s.size());
        assertTrue(candidateSha1s.contains(fiveDaysAgoCommit));
        assertTrue(candidateSha1s.contains(tenDaysAgoCommit));
        assertTrue(candidateSha1s.contains(twentyDaysAgoCommit));
    }

    @Test
    public void testFilterRevisionsNonExistingAncestor() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = "This commit sha1 does not exist.";

        try {
            List<String> candidateSha1s = getTestCandidates(maxAgeInDays, ancestorCommitSha1);
            fail("Invalid sha1 should throw GitException.");
        } catch (GitException e) {
            return;
        }
    }

    @Test
    public void testFilterRevisionsExistingAncestor() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = ancestorCommit;

        List<String> candidateSha1s = getTestCandidates(maxAgeInDays, ancestorCommitSha1);

        assertEquals(2, candidateSha1s.size());
        assertTrue(candidateSha1s.contains(tenDaysAgoCommit));
        assertTrue(candidateSha1s.contains(twentyDaysAgoCommit));
    }

    @Test
    public void testUsingInverseBuildChooser() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = null;

        List<String> candidateSha1s = getTestCandidates(INVERSE_BUILD_CHOOSER, maxAgeInDays, ancestorCommitSha1);

        // no branch spec means everything is excluded by InverseBuildChooser
        assertEquals(0, candidateSha1s.size());
    }

    @Test
    public void testSortingByNullPriorityBranches() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = null;
        final String priorityBranches = null;

        List<String> candidateSha1s = getTestCandidates(DEFAULT_BUILD_CHOOSER, maxAgeInDays, ancestorCommitSha1, priorityBranches);

        assertEquals(3, candidateSha1s.size());

        // no priority branches; sort order should be by oldest commit first
        assertEquals(candidateSha1s.get(0), twentyDaysAgoCommit);
        assertEquals(candidateSha1s.get(1), tenDaysAgoCommit);
        assertEquals(candidateSha1s.get(2), fiveDaysAgoCommit);
    }

    @Test
    public void testSortingByEmptyPriorityBranches() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = null;
        final String priorityBranches = "";

        List<String> candidateSha1s = getTestCandidates(DEFAULT_BUILD_CHOOSER, maxAgeInDays, ancestorCommitSha1, priorityBranches);

        assertEquals(3, candidateSha1s.size());

        // no priority branches; sort order should be oldest commits first
        assertEquals(twentyDaysAgoCommit, candidateSha1s.get(0));
        assertEquals(tenDaysAgoCommit, candidateSha1s.get(1));
        assertEquals(fiveDaysAgoCommit, candidateSha1s.get(2));
    }

    @Test
    public void testSortingByCriticalPriorityBranch() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = null;
        final String priorityBranches = "CRITICAL";

        List<String> candidateSha1s = getTestCandidates(DEFAULT_BUILD_CHOOSER, maxAgeInDays, ancestorCommitSha1, priorityBranches);

        assertEquals(3, candidateSha1s.size());

        // fiveDaysAgoCommit should be first as it has a CRITICAL priority
        // branch
        assertEquals(fiveDaysAgoCommit, candidateSha1s.get(0));
        assertEquals(twentyDaysAgoCommit, candidateSha1s.get(1));
        assertEquals(tenDaysAgoCommit, candidateSha1s.get(2));
    }

    @Test
    public void testSortingByMultiplePriorityBranches() throws Exception {
        final Integer maxAgeInDays = null;
        final String ancestorCommitSha1 = null;
        final String priorityBranches = "CRITICAL\nRELEASE";

        List<String> candidateSha1s = getTestCandidates(DEFAULT_BUILD_CHOOSER, maxAgeInDays, ancestorCommitSha1, priorityBranches);

        assertEquals(3, candidateSha1s.size());

        // fiveDaysAgoCommit should be first as it has a CRITICAL priority
        // branch, and tenDaysAgoCommit second as it has a RELEASE priority
        // branch
        assertEquals(fiveDaysAgoCommit, candidateSha1s.get(0));
        assertEquals(tenDaysAgoCommit, candidateSha1s.get(1));
        assertEquals(twentyDaysAgoCommit, candidateSha1s.get(2));
    }

    @Test
    public void testGetPriorityBranchesWithVariousInputs() throws Exception {
        // linux line-ending
        AncestryBuildChooser chooser = new AncestryBuildChooser(DEFAULT_BUILD_CHOOSER, null, null, "a\nb");
        List<String> parsedPriorityBranches = chooser.getPrioritizedBranchesAsList();
        assertEquals(2, parsedPriorityBranches.size());
        assertTrue(parsedPriorityBranches.contains("a"));
        assertTrue(parsedPriorityBranches.contains("b"));

        // mac line-ending
        chooser = new AncestryBuildChooser(DEFAULT_BUILD_CHOOSER, null, null, "a\rb");
        parsedPriorityBranches = chooser.getPrioritizedBranchesAsList();
        assertEquals(2, parsedPriorityBranches.size());
        assertTrue(parsedPriorityBranches.contains("a"));
        assertTrue(parsedPriorityBranches.contains("b"));

        // windows line-ending
        chooser = new AncestryBuildChooser(DEFAULT_BUILD_CHOOSER, null, null, "a\r\nb");
        parsedPriorityBranches = chooser.getPrioritizedBranchesAsList();
        assertEquals(2, parsedPriorityBranches.size());
        assertTrue(parsedPriorityBranches.contains("a"));
        assertTrue(parsedPriorityBranches.contains("b"));

        // tabs and spaces
        chooser = new AncestryBuildChooser(DEFAULT_BUILD_CHOOSER, null, null, " \ta\r\nb \t");
        parsedPriorityBranches = chooser.getPrioritizedBranchesAsList();
        assertEquals(2, parsedPriorityBranches.size());
        assertTrue(parsedPriorityBranches.contains("a"));
        assertTrue(parsedPriorityBranches.contains("b"));

        // empty lines and multiple line-endings
        chooser = new AncestryBuildChooser(DEFAULT_BUILD_CHOOSER, null, null, "\n\t a\r\nb \t\r\n");
        parsedPriorityBranches = chooser.getPrioritizedBranchesAsList();
        assertEquals(2, parsedPriorityBranches.size());
        assertTrue(parsedPriorityBranches.contains("a"));
        assertTrue(parsedPriorityBranches.contains("b"));
    }
}
