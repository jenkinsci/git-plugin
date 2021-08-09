/*
 * The MIT License
 *
 * Copyright 2017 Mark Waite.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.git.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.Revision;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import jenkins.plugins.git.GitSampleRepoRule;
import org.eclipse.jgit.lib.ObjectId;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GitUtilsTest {

    @ClassRule
    public static GitSampleRepoRule originRepo = new GitSampleRepoRule();

    @ClassRule
    public static TemporaryFolder repoParentFolder = new TemporaryFolder();

    private static final String[] HEAD_BRANCH_NAMES = {
        "master",
        "sally-2",
        "baker-1",
        "able-4"
    };
    private static final String OLDER_BRANCH_NAME = "older-branch";

    private static ObjectId headId = null;
    private static ObjectId headTag0Id = null;
    private static ObjectId priorHeadId = null;

    private static Revision headRevision = null;
    private static Revision headTag0Revision = null;
    private static Revision priorRevision = null;

    private static final String PRIOR_TAG_NAME_1 = "prior-tag-1";
    private static final String PRIOR_TAG_NAME_2 = "prior-tag-2-annotated";
    private static final String HEAD_TAG_NAME_0 = "head-tag-0";
    private static final String HEAD_TAG_NAME_1 = "head-tag-1";
    private static final String HEAD_TAG_NAME_2 = "head-tag-2-annotated";
    private final String[] tagNames = {
        PRIOR_TAG_NAME_1,
        PRIOR_TAG_NAME_2,
        HEAD_TAG_NAME_0,
        HEAD_TAG_NAME_1,
        HEAD_TAG_NAME_2
    };

    private static List<BranchSpec> branchSpecList = null;
    private static List<BranchSpec> priorBranchSpecList = null;
    private static List<Branch> branchList = null;

    private static final EnvVars ENV = new EnvVars();
    private static final TaskListener NULL_LISTENER = StreamTaskListener.NULL;

    private GitUtils gitUtils;
    private static GitClient gitClient;

    private static final Random RANDOM = new Random();

    @BeforeClass
    public static void createSampleOriginRepo() throws Exception {
        String fileName = "README";
        originRepo.init();
        originRepo.git("config", "user.name", "Author User Name");
        originRepo.git("config", "user.email", "author.user.name@mail.example.com");
        originRepo.git("tag", PRIOR_TAG_NAME_1);
        originRepo.git("tag", "-a", PRIOR_TAG_NAME_2, "-m", "Annotated tag " + PRIOR_TAG_NAME_2);
        priorHeadId = ObjectId.fromString(originRepo.head());

        originRepo.git("checkout", "-b", OLDER_BRANCH_NAME);
        branchList = new ArrayList<>();
        branchList.add(new Branch(OLDER_BRANCH_NAME, priorHeadId));
        branchList.add(new Branch("refs/tags/" + PRIOR_TAG_NAME_1, priorHeadId));
        branchList.add(new Branch("refs/tags/" + PRIOR_TAG_NAME_2, priorHeadId));
        priorRevision = new Revision(priorHeadId, branchList);
        priorBranchSpecList = new ArrayList<>();
        priorBranchSpecList.add(new BranchSpec(OLDER_BRANCH_NAME));

        originRepo.git("checkout", "master");
        originRepo.write(fileName, "This is the " + HEAD_TAG_NAME_0 + " README file " + RANDOM.nextInt());
        originRepo.git("add", fileName);
        originRepo.git("commit", "-m", "Adding " + fileName + " tagged " + HEAD_TAG_NAME_0, fileName);
        originRepo.git("tag", HEAD_TAG_NAME_0);
        headTag0Id = ObjectId.fromString(originRepo.head());
        headTag0Revision = new Revision(headTag0Id);

        originRepo.write(fileName, "This is the README file " + RANDOM.nextInt());
        originRepo.git("add", fileName);
        originRepo.git("commit", "-m", "Adding " + fileName, fileName);
        originRepo.git("tag", HEAD_TAG_NAME_1);
        originRepo.git("tag", "-a", HEAD_TAG_NAME_2, "-m", "Annotated tag " + HEAD_TAG_NAME_2);
        headId = ObjectId.fromString(originRepo.head());
        branchSpecList = new ArrayList<>();
        branchList = new ArrayList<>();
        branchSpecList.add(new BranchSpec("master"));
        branchSpecList.add(new BranchSpec("refs/tags/" + HEAD_TAG_NAME_0));
        branchSpecList.add(new BranchSpec("refs/tags/" + HEAD_TAG_NAME_1));
        branchSpecList.add(new BranchSpec("refs/tags/" + HEAD_TAG_NAME_2));
        branchList.add(new Branch("master", headId));
        branchList.add(new Branch("refs/tags/" + HEAD_TAG_NAME_0, headId));
        branchList.add(new Branch("refs/tags/" + HEAD_TAG_NAME_1, headId));
        branchList.add(new Branch("refs/tags/" + HEAD_TAG_NAME_2, headId));
        for (String branchName : HEAD_BRANCH_NAMES) {
            if (!branchName.equals("master")) {
                originRepo.git("checkout", "-b", branchName);
                branchSpecList.add(new BranchSpec(branchName));
                branchList.add(new Branch(branchName, headId));
            }
        }
        originRepo.git("checkout", "master"); // Master branch as current branch in origin repo
        headRevision = new Revision(headId, branchList);

        File gitDir = repoParentFolder.newFolder("test-repo");
        gitClient = Git.with(NULL_LISTENER, ENV).in(gitDir).using("git").getClient();
        gitClient.init();
        gitClient.clone_().url(originRepo.fileUrl()).repositoryName("origin").execute();
        gitClient.checkout("origin/master", "master");
    }

    @Before
    public void createGitUtils() throws Exception {
        gitUtils = new GitUtils(NULL_LISTENER, gitClient);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, branchSpecList);
        assertThat(result, is(headRevision));
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Prior() {
        Revision result = gitUtils.sortBranchesForRevision(priorRevision, priorBranchSpecList);
        assertThat(result, is(priorRevision));
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Mix_1() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, priorBranchSpecList);
        assertThat(result, is(headRevision));
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Mix_2() {
        Revision result = gitUtils.sortBranchesForRevision(priorRevision, branchSpecList);
        assertThat(result, is(priorRevision));
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Prior_3_args() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, branchSpecList, ENV);
        assertThat(result, is(headRevision));
    }

    @Test
    public void testSortBranchesForRevision_3args() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, branchSpecList, ENV);
        assertThat(result, is(headRevision));
    }

    @Test
    public void testSortBranchesForRevision_3args_Prior() {
        Revision result = gitUtils.sortBranchesForRevision(priorRevision, branchSpecList, ENV);
        assertThat(result, is(priorRevision));
    }

    @Test
    public void testGetRevisionContainingBranch() throws Exception {
        for (String branchName : HEAD_BRANCH_NAMES) {
            Revision revision = gitUtils.getRevisionContainingBranch("origin/" + branchName);
            assertThat(revision, is(headRevision));
        }
    }

    @Test
    public void testGetRevisionContainingBranch_OlderName() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("origin/" + OLDER_BRANCH_NAME);
        assertThat(revision, is(priorRevision));
    }

    /* Tags are searched in getRevisionContainingBranch beginning with 3.2.0 */
    @Test
    public void testGetRevisionContainingBranch_UseTagNameHead0() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("refs/tags/" + HEAD_TAG_NAME_0);
        assertThat(revision, is(headTag0Revision));
    }

    /* Tags are searched in getRevisionContainingBranch beginning with 3.2.0 */
    @Test
    public void testGetRevisionContainingBranch_UseTagNameHead1() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("refs/tags/" + HEAD_TAG_NAME_1);
        assertThat(revision, is(headRevision));
    }

    /* Tags are searched in getRevisionContainingBranch beginning with 3.2.0 */
    @Test
    public void testGetRevisionContainingBranch_UseTagNameHead2() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("refs/tags/" + HEAD_TAG_NAME_2);
        assertThat(revision, is(headRevision));
    }

    /* Tags are searched in getRevisionContainingBranch beginning with 3.2.0 */
    @Test
    public void testGetRevisionContainingBranch_UseTagNamePrior1() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("refs/tags/" + PRIOR_TAG_NAME_1);
        assertThat(revision, is(priorRevision));
    }

    /* Tags are searched in getRevisionContainingBranch beginning with 3.2.0 */
    @Test
    public void testGetRevisionContainingBranch_UseTagNamePrior2() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("refs/tags/" + PRIOR_TAG_NAME_2);
        assertThat(revision, is(priorRevision));
    }

    @Test
    public void testGetRevisionContainingBranch_InvalidBranchName() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("origin/not-a-valid-branch-name");
        assertThat(revision, is(nullValue(Revision.class)));
    }

    @Test
    public void testGetRevisionContainingBranch_InvalidTagName() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("ref/tags/not-a-valid-tag-name");
        assertThat(revision, is(nullValue(Revision.class)));
    }

    @Test
    public void testGetRevisionForSHA1() throws Exception {
        Revision revision = gitUtils.getRevisionForSHA1(headId);
        assertThat(revision, is(headRevision));
    }

    @Test
    public void testGetRevisionForSHA1PriorRevision() throws Exception {
        Revision revision = gitUtils.getRevisionForSHA1(priorHeadId);
        assertThat(revision, is(priorRevision));
    }

    @Test
    public void testGetRevisionForSHA1UnknownRevision() throws Exception {
        ObjectId unknown = ObjectId.fromString("a422d10c6dc4262effb12f9e7a64911111000000");
        Revision unknownRevision = new Revision(unknown);
        Revision revision = gitUtils.getRevisionForSHA1(unknown);
        assertThat(revision, is(unknownRevision));
    }

    @Test
    public void testFilterTipBranches() throws Exception {
        Collection<Revision> multiRevisionList = new ArrayList<>();
        multiRevisionList.add(priorRevision);
        multiRevisionList.add(headRevision);
        Collection<Revision> filteredRevisions = new ArrayList<>();
        filteredRevisions.add(headRevision);
        List<Revision> result = gitUtils.filterTipBranches(multiRevisionList);
        assertThat(result, is(filteredRevisions));
    }

    @Test
    public void testFilterTipBranchesNoRemovals() throws Exception {
        Collection<Revision> headRevisionList = new ArrayList<>();
        headRevisionList.add(headRevision);
        List<Revision> result = gitUtils.filterTipBranches(headRevisionList);
        assertThat(result, is(headRevisionList));
    }

    @Test
    public void testFilterTipBranchesNoRemovalsNonTip() throws Exception {
        Collection<Revision> priorRevisionList = new ArrayList<>();
        priorRevisionList.add(priorRevision);
        List<Revision> result = gitUtils.filterTipBranches(priorRevisionList);
        assertThat(result, is(priorRevisionList));
    }

    @Test
    public void testFixupNames() {
        String[] names = {"origin", "origin2", null, "", null};
        String[] urls = {
            "git://github.com/jenkinsci/git-plugin.git",
            "git@github.com:jenkinsci/git-plugin.git",
            "https://github.com/jenkinsci/git-plugin",
            "https://github.com/jenkinsci/git-plugin.git",
            "ssh://github.com/jenkinsci/git-plugin.git"
        };
        String[] expected = {"origin", "origin2", "origin1", "origin3", "origin4"};
        String[] actual = GitUtils.fixupNames(names, urls);
        assertThat(expected, is(actual));
    }

    private Set<String> getExpectedNames() {
        Set<String> names = new HashSet<>(HEAD_BRANCH_NAMES.length + tagNames.length + 1);
        for (String branchName : HEAD_BRANCH_NAMES) {
            names.add("origin/" + branchName);
        }
        names.add("origin/" + OLDER_BRANCH_NAME);
        for (String tagName : tagNames) {
            names.add("refs/tags/" + tagName);
        }
        return names;
    }

    private Set<String> getActualNames(@NonNull Collection<Revision> revisions) {
        Set<String> names = new HashSet<>(revisions.size());
        for (Revision revision : revisions) {
            for (Branch branch : revision.getBranches()) {
                names.add(branch.getName());
            }
        }
        return names;
    }

    @Test
    public void testGetAllBranchRevisions() throws Exception {
        Collection<Revision> allRevisions = gitUtils.getAllBranchRevisions();
        assertThat(allRevisions, hasItem(headRevision));
        Set<String> expectedNames = getExpectedNames();
        Set<String> actualNames = getActualNames(allRevisions);
        assertThat(actualNames, is(expectedNames));
    }
}
