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

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.Revision;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import jenkins.plugins.git.GitSampleRepoRule;
import org.eclipse.jgit.lib.ObjectId;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GitUtilsSortBranchTest {

    @ClassRule
    public static GitSampleRepoRule sampleOriginRepo = new GitSampleRepoRule();

    @Rule
    public TemporaryFolder repoParentFolder = new TemporaryFolder();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private static final String[] BRANCH_NAMES = {
        "master",
        "sally-2",
        "baker-1",
        "able-4"
    };
    private static final String OLDER_BRANCH_NAME = "older-branch";

    private static ObjectId headId = null;
    private static ObjectId priorHeadId = null;

    private static Revision headRevision = null;
    private static Revision priorRevision = null;

    private static final String PRIOR_TAG_NAME_1 = "prior-tag-1";
    private static final String PRIOR_TAG_NAME_2 = "prior-tag-2-annotated";
    private static final String HEAD_TAG_NAME_1 = "head-tag-1";
    private static final String HEAD_TAG_NAME_2 = "head-tag-2-annotated";

    private static List<BranchSpec> branchSpecList = null;
    private static List<BranchSpec> priorBranchSpecList = null;
    private static List<Branch> branchList = null;

    private final EnvVars env;
    private final TaskListener listener = StreamTaskListener.NULL;

    private GitUtils gitUtils;
    private GitClient gitClient;

    private static final Random RANDOM = new Random();

    public GitUtilsSortBranchTest() {
        this.env = new EnvVars();
    }

    @BeforeClass
    public static void createSampleOriginRepo() throws Exception {
        String fileName = "README";
        sampleOriginRepo.init();
        sampleOriginRepo.git("config", "user.name", "Author User Name");
        sampleOriginRepo.git("config", "user.email", "author.user.name@mail.example.com");
        sampleOriginRepo.git("tag", PRIOR_TAG_NAME_1);
        sampleOriginRepo.git("tag", "-a", PRIOR_TAG_NAME_2, "-m", "Annotated tag " + PRIOR_TAG_NAME_2);
        priorHeadId = ObjectId.fromString(sampleOriginRepo.head());

        sampleOriginRepo.git("checkout", "-b", OLDER_BRANCH_NAME);
        branchList = new ArrayList<>();
        branchList.add(new Branch(OLDER_BRANCH_NAME, priorHeadId));
        branchList.add(new Branch("refs/tags/" + PRIOR_TAG_NAME_1, priorHeadId));
        branchList.add(new Branch("refs/tags/" + PRIOR_TAG_NAME_2, priorHeadId));
        priorRevision = new Revision(priorHeadId, branchList);
        priorBranchSpecList = new ArrayList<>();
        priorBranchSpecList.add(new BranchSpec(OLDER_BRANCH_NAME));

        sampleOriginRepo.git("checkout", "master");
        sampleOriginRepo.write(fileName, "This is the README file " + RANDOM.nextInt());
        sampleOriginRepo.git("add", fileName);
        sampleOriginRepo.git("commit", "-m", "Adding " + fileName, fileName);
        sampleOriginRepo.git("tag", HEAD_TAG_NAME_1);
        sampleOriginRepo.git("tag", "-a", HEAD_TAG_NAME_2, "-m", "Annotated tag " + HEAD_TAG_NAME_2);
        headId = ObjectId.fromString(sampleOriginRepo.head());
        branchSpecList = new ArrayList<>();
        branchList = new ArrayList<>();
        branchSpecList.add(new BranchSpec("master"));
        branchSpecList.add(new BranchSpec("refs/tags/" + HEAD_TAG_NAME_1));
        branchSpecList.add(new BranchSpec("refs/tags/" + HEAD_TAG_NAME_2));
        branchList.add(new Branch("master", headId));
        branchList.add(new Branch("refs/tags/" + HEAD_TAG_NAME_1, headId));
        branchList.add(new Branch("refs/tags/" + HEAD_TAG_NAME_2, headId));
        for (String branchName : BRANCH_NAMES) {
            if (!branchName.equals("master")) {
                sampleOriginRepo.git("checkout", "-b", branchName);
                branchSpecList.add(new BranchSpec(branchName));
                branchList.add(new Branch(branchName, headId));
            }
        }
        sampleOriginRepo.git("checkout", "master"); // Master branch as current branch in origin repo
        headRevision = new Revision(headId, branchList);
    }

    @Before
    public void cloneSampleRepo() throws Exception {
        File gitDir = repoParentFolder.newFolder("test-repo");
        this.gitClient = Git.with(listener, env).in(gitDir).using("git").getClient();
        this.gitClient.init();
        this.gitClient.clone_().url(sampleOriginRepo.fileUrl()).repositoryName("origin").execute();
        this.gitClient.checkout("origin/master", "master");
        this.gitUtils = new GitUtils(listener, gitClient);
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
        Revision result = gitUtils.sortBranchesForRevision(headRevision, branchSpecList, env);
        assertThat(result, is(headRevision));
    }

    @Test
    public void testGetRevisionContainingBranch() throws Exception {
        for (String branchName : BRANCH_NAMES) {
            Revision revision = gitUtils.getRevisionContainingBranch("origin/" + branchName);
            assertThat(revision, is(headRevision));
        }
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

    @Test
    public void testGetRevisionContainingBranch_OlderName() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch("origin/" + OLDER_BRANCH_NAME);
        assertThat(revision, is(priorRevision));
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
    public void testFilterTipBranches() throws Exception {
        Collection<Revision> multiRevisionList = new ArrayList<>();
        multiRevisionList.add(priorRevision);
        multiRevisionList.add(headRevision);
        Collection<Revision> filteredRevisions = new ArrayList<>();
        filteredRevisions.add(headRevision);
        List<Revision> result = gitUtils.filterTipBranches(multiRevisionList);
        assertThat(result, is(filteredRevisions));
    }
}
